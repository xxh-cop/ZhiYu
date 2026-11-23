package com.zhiyu.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.embedding.DoubaoEmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 向量检索层：只负责可选的向量召回与来源过滤，生成与降级由 KnowledgeAskService 负责。
 * 索引带内容、Embedding 模型与切片参数指纹；任一变化时重建，旧切片不会越过当前来源集合。
 */
@Slf4j
@Service
@ConditionalOnBean(VectorStore.class)
public class RagService {

    public record VectorHit(String id, String title, String excerpt,
                            String sourceId, String corpusVersion) {
    }

    private static final int CHUNK_SIZE = 512;
    /** 实为 TokenTextSplitter 的 minChunkSizeChars（历史常量名沿用，并非切片重叠）。 */
    private static final int CHUNK_OVERLAP = 64;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
    private static final int MAX_CHUNKS = 10000;
    private static final int DEFAULT_FINAL_TOP_K = 5;
    private static final int DEFAULT_EXCERPT_MAX_CHARS = 0;
    private static final double SIMILARITY_THRESHOLD = 0.3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final String embeddingModelName;
    private final Path vectorStorePath;
    private final int finalTopK;
    private final int excerptMaxChars;
    private volatile Set<String> activeSourceIds = Set.of();
    private volatile String corpusVersion = "uninitialized";

    /** 测试用便捷构造：默认 5 条、完整切片。 */
    public RagService(VectorStore vectorStore,
                      EmbeddingModel embeddingModel,
                      String configuredModelName,
                      String vectorStorePath) {
        this(vectorStore, embeddingModel, configuredModelName, vectorStorePath,
                DEFAULT_FINAL_TOP_K, DEFAULT_EXCERPT_MAX_CHARS);
    }

    @Autowired
    public RagService(VectorStore vectorStore,
                      EmbeddingModel embeddingModel,
                      @Value("${zhiyu.ai.doubao.embedding.model:unknown}") String configuredModelName,
                      @Value("${zhiyu.ai.rag.vector-store-path:./rag-store.json}") String vectorStorePath,
                      @Value("${zhiyu.ai.rag.final-top-k:5}") int finalTopK,
                      @Value("${zhiyu.ai.rag.excerpt-max-chars:0}") int excerptMaxChars) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.embeddingModelName = embeddingModel instanceof DoubaoEmbeddingModel doubao
                ? doubao.modelName() : configuredModelName;
        this.vectorStorePath = Path.of(vectorStorePath).toAbsolutePath().normalize();
        this.finalTopK = Math.max(3, Math.min(8, finalTopK));
        this.excerptMaxChars = Math.max(0, excerptMaxChars);
    }

    /** Local lexical rerank over the vector shortlist; no extra model or external data transfer. */
    List<Document> rerank(String question, List<Document> candidates) {
        Set<String> terms = new HashSet<>();
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        for (int i = 0; i + 1 < normalized.length(); i++) terms.add(normalized.substring(i, i + 2));
        var ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((Document document) -> {
            String body = Objects.toString(document.getText(), "").toLowerCase(Locale.ROOT);
            double lexical = terms.isEmpty() ? 0
                    : (double) terms.stream().filter(body::contains).count() / terms.size();
            double vector = 1.0 / (1 + candidates.indexOf(document));
            return .65 * vector + .35 * lexical;
        }).reversed());
        return ranked.stream()
                .filter(document -> document.getText() != null && !document.getText().isBlank())
                .limit(finalTopK)
                .toList();
    }

    @PostConstruct
    public void init() {
        try {
            Corpus corpus = readCorpus();
            activeSourceIds = corpus.documents().stream()
                    .map(RagService::sourceId)
                    .collect(Collectors.toUnmodifiableSet());
            int dimensions = expectedDimensions();
            String fingerprint = fingerprint(embeddingModelName, dimensions, CHUNK_SIZE,
                    CHUNK_OVERLAP, MIN_CHUNK_LENGTH_TO_EMBED, MAX_CHUNKS, corpus.documents());
            corpusVersion = fingerprint;

            Path storeFile = vectorStorePath;
            Path versionFile = versionPath();
            if (Files.isRegularFile(storeFile) && Files.isRegularFile(versionFile)) {
                String persisted = null;
                try {
                    persisted = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
                } catch (IOException e) {
                    log.warn("RAG 版本文件不可读，将重建索引: {} ({})", versionFile, e.getMessage());
                }
                if (fingerprint.equals(persisted)) {
                    StoreInspection inspection = inspectStore(storeFile, dimensions);
                    if (inspection.usable()) {
                        try {
                            ((SimpleVectorStore) vectorStore).load(storeFile.toFile());
                            log.info("RAG 向量库从文件加载完成: {} (version={}, 切片={}, {} 维)",
                                    storeFile, fingerprint, inspection.total(), inspection.dimension());
                            return;
                        } catch (Exception e) {
                            log.warn("RAG 现有索引载入失败，将重建: {} ({})", storeFile, e.getMessage());
                        }
                    } else {
                        log.warn("RAG 现有索引校验未通过，将重建: {} (切片 {}，零向量 {}，维度不符 {}，空向量 {}，期望 {} 维)",
                                storeFile, inspection.total(), inspection.zeroVectors(),
                                inspection.dimensionMismatch(), inspection.emptyVectors(), dimensions);
                    }
                } else {
                    log.info("RAG 语料/模型/切片参数指纹变化，重建索引: {} -> {}", persisted, fingerprint);
                }
            }
            buildIndex(corpus, fingerprint, dimensions);
        } catch (Exception e) {
            log.error("RAG 索引初始化失败: {}", e.getMessage(), e);
        }
    }

    public List<VectorHit> retrieve(String question) {
        try {
            List<Document> candidates = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question).topK(8).similarityThreshold(SIMILARITY_THRESHOLD).build());
            if (candidates == null || candidates.isEmpty()) return List.of();
            List<Document> docs = rerank(question, candidates);
            List<VectorHit> hits = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Document document : docs) {
                String sourceId = sourceId(document);
                if (!activeSourceIds.contains(sourceId)) continue;
                String text = Objects.toString(document.getText(), "");
                if (text.isBlank()) continue;
                String id = sourceId + "#" + Integer.toHexString(text.hashCode());
                if (!seen.add(id)) continue;
                hits.add(new VectorHit(id,
                        Objects.toString(document.getMetadata().get("source"), sourceId + ".md"),
                        excerpt(text), sourceId, corpusVersion));
            }
            int contextChars = hits.stream().mapToInt(hit -> hit.excerpt().length()).sum();
            log.info("RAG 向量注入: {} 条, 正文 {} 字 (final-top-k={}, excerpt-max-chars={})",
                    hits.size(), contextChars, finalTopK,
                    excerptMaxChars == 0 ? "full" : excerptMaxChars);
            return hits;
        } catch (Exception e) {
            log.warn("RAG 向量检索不可用，返回空候选: {}", e.getMessage());
            return List.of();
        }
    }

    private Corpus readCorpus() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:rag/crafts/*.md");
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename,
                Comparator.nullsLast(String::compareTo)));
        List<Document> docs = new ArrayList<>();
        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String filename = Objects.toString(resource.getFilename(), "knowledge.md");
            docs.add(new Document(content, Map.of("source", filename, "sourceId", sourceId(filename))));
        }
        return new Corpus(docs);
    }

    private void buildIndex(Corpus corpus, String fingerprint, int dimensions) {
        if (corpus.documents().isEmpty()) {
            log.error("RAG 索引构建取消：classpath:rag/crafts/*.md 未找到任何文档，保留现有索引与版本");
            return;
        }
        List<Document> splitDocs;
        try {
            TokenTextSplitter splitter = new TokenTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP,
                    MIN_CHUNK_LENGTH_TO_EMBED, MAX_CHUNKS, true);
            splitDocs = splitter.apply(corpus.documents());
        } catch (Exception e) {
            log.error("RAG 索引构建失败：切片阶段异常，保留现有索引与版本: {}", e.getMessage(), e);
            return;
        }
        if (splitDocs == null || splitDocs.isEmpty()) {
            log.error("RAG 索引构建取消：切片数为 0（文档 {} 篇），保留现有索引与版本",
                    corpus.documents().size());
            return;
        }

        AuditingEmbeddingModel auditing = new AuditingEmbeddingModel(embeddingModel, dimensions);
        SimpleVectorStore candidate = SimpleVectorStore.builder(auditing).build();
        EmbeddingIssues issues = EmbeddingIssues.none();
        try {
            auditing.start();
            candidate.add(splitDocs);
        } catch (Exception e) {
            log.error("RAG 索引构建失败：向量化阶段异常，保留现有索引与版本: {}", e.getMessage(), e);
            return;
        } finally {
            issues = auditing.stop();
        }
        if (issues.hasIssues()) {
            log.error("RAG 索引构建取消：切片 {} 条中出现零向量 {} 条、维度不符 {} 条（期望 {} 维，实际 {}）、"
                            + "空向量 {} 条、空响应 {} 次；保留现有索引与版本，不写文件",
                    splitDocs.size(), issues.zeroVectors(), issues.dimensionMismatch(),
                    dimensions, issues.actualDimension(), issues.emptyVectors(), issues.emptyResponses());
            return;
        }
        if (!persist(candidate, fingerprint)) return;

        try {
            ((SimpleVectorStore) vectorStore).load(vectorStorePath.toFile());
            log.info("RAG 索引构建完成: {} 篇文档 → {} 个切片, version={}",
                    corpus.documents().size(), splitDocs.size(), fingerprint);
        } catch (Exception e) {
            log.error("RAG 索引已写入 {}，但载入当前进程失败: {}", vectorStorePath, e.getMessage(), e);
        }
    }

    /** 候选索引先写临时文件并校验完成后再原子替换；任一步失败都回滚，原索引与版本保持不变。 */
    private boolean persist(SimpleVectorStore candidate, String fingerprint) {
        Path tempStore = null;
        Path backup = null;
        try {
            Path parent = vectorStorePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            tempStore = Files.createTempFile(parent, "rag-store-", ".tmp");
            candidate.save(tempStore.toFile());
            if (Files.isRegularFile(vectorStorePath)) {
                backup = Files.createTempFile(parent, "rag-store-", ".bak");
                Files.copy(vectorStorePath, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            moveAtomically(tempStore, vectorStorePath);
            tempStore = null;
            writeAtomically(versionPath(), fingerprint);
            return true;
        } catch (Exception e) {
            if (backup != null) {
                try {
                    Files.move(backup, vectorStorePath, StandardCopyOption.REPLACE_EXISTING);
                    backup = null;
                    log.warn("RAG 索引写入失败，已回滚到原索引文件: {}", vectorStorePath);
                } catch (IOException restoreError) {
                    log.error("RAG 索引回滚失败，原索引可能已被替换: {}", restoreError.getMessage(), restoreError);
                }
            }
            log.error("RAG 索引原子写入失败，已清理临时文件；原索引与版本保持不变: {}",
                    e.getMessage(), e);
            return false;
        } finally {
            if (tempStore != null) deleteQuietly(tempStore);
            if (backup != null) deleteQuietly(backup);
        }
    }

    void writeAtomically(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), "rag-version-", ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            moveAtomically(temp, target);
        } finally {
            deleteQuietly(temp);
        }
    }

    void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            log.warn("文件系统不支持原子移动，退化为 REPLACE_EXISTING: {}", target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 校验现有索引文件：空文件、空向量、零向量或维度不符都视为不可用。 */
    static StoreInspection inspectStore(Path file, int expectedDimensions) {
        try {
            JsonNode root = JSON.readTree(file.toFile());
            if (root == null || !root.isObject() || root.isEmpty()) {
                return new StoreInspection(0, 0, 0, 0, -1);
            }
            long total = 0;
            long zeroVectors = 0;
            long dimensionMismatch = 0;
            long emptyVectors = 0;
            int actualDimension = -1;
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                JsonNode entry = fields.next().getValue();
                total++;
                JsonNode embedding = entry == null ? null : entry.get("embedding");
                if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                    emptyVectors++;
                    continue;
                }
                int size = embedding.size();
                if (actualDimension < 0) actualDimension = size;
                if (expectedDimensions > 0 && size != expectedDimensions) {
                    dimensionMismatch++;
                    continue;
                }
                boolean allZero = true;
                for (JsonNode value : embedding) {
                    if (value.asDouble() != 0.0d) {
                        allZero = false;
                        break;
                    }
                }
                if (allZero) zeroVectors++;
            }
            return new StoreInspection(total, zeroVectors, dimensionMismatch, emptyVectors, actualDimension);
        } catch (Exception e) {
            log.warn("RAG 索引文件无法解析，视为不可用: {} ({})", file, e.getMessage());
            return new StoreInspection(0, 0, 0, 0, -1);
        }
    }

    static String fingerprint(String modelName, int dimensions, int chunkSize, int minChunkSizeChars,
                              int minChunkLengthToEmbed, int maxNumChunks, List<Document> documents) {
        StringBuilder input = new StringBuilder();
        input.append("embedding-model=").append(modelName == null ? "" : modelName).append('\n');
        input.append("embedding-dimensions=").append(dimensions).append('\n');
        input.append("splitter-chunk-size=").append(chunkSize).append('\n');
        input.append("splitter-min-chunk-size-chars=").append(minChunkSizeChars).append('\n');
        input.append("splitter-min-chunk-length-to-embed=").append(minChunkLengthToEmbed).append('\n');
        input.append("splitter-max-num-chunks=").append(maxNumChunks).append('\n');
        for (Document document : documents) {
            input.append(Objects.toString(document.getMetadata().get("source"), "knowledge.md")).append('\n')
                    .append(document.getText()).append('\n');
        }
        return sha256(input.toString()).substring(0, 12);
    }

    private int expectedDimensions() {
        try {
            return Math.max(0, embeddingModel.dimensions());
        } catch (RuntimeException e) {
            log.warn("无法读取 Embedding 维度，本次跳过维度校验: {}", e.getMessage());
            return 0;
        }
    }

    private String excerpt(String text) {
        if (excerptMaxChars <= 0 || text.length() <= excerptMaxChars) return text;
        if (excerptMaxChars == 1) return "…";
        return text.substring(0, excerptMaxChars - 1) + "…";
    }

    private Path versionPath() {
        return vectorStorePath.resolveSibling(vectorStorePath.getFileName() + ".version");
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响主流程，原文件未被触碰。
        }
    }

    private static String sourceId(Document document) {
        Object value = document.getMetadata().get("sourceId");
        if (value != null && !value.toString().isBlank()) return value.toString();
        return sourceId(Objects.toString(document.getMetadata().get("source"), "knowledge"));
    }

    private static String sourceId(String filename) {
        String name = filename == null ? "knowledge" : filename;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    record StoreInspection(long total, long zeroVectors, long dimensionMismatch,
                           long emptyVectors, int dimension) {
        boolean usable() {
            return total > 0 && zeroVectors == 0 && dimensionMismatch == 0 && emptyVectors == 0;
        }
    }

    record EmbeddingIssues(long zeroVectors, long dimensionMismatch, long emptyVectors,
                           long emptyResponses, int actualDimension) {
        static EmbeddingIssues none() {
            return new EmbeddingIssues(0, 0, 0, 0, -1);
        }

        boolean hasIssues() {
            return zeroVectors > 0 || dimensionMismatch > 0 || emptyVectors > 0 || emptyResponses > 0;
        }
    }

    /** 建库专用包装：只记录本次建库的向量异常，查询阶段不记录。 */
    static final class AuditingEmbeddingModel implements EmbeddingModel {

        private final EmbeddingModel delegate;
        private final int expectedDimensions;
        private final AtomicBoolean recording = new AtomicBoolean(false);
        private final AtomicLong zeroVectors = new AtomicLong();
        private final AtomicLong dimensionMismatch = new AtomicLong();
        private final AtomicLong emptyVectors = new AtomicLong();
        private final AtomicLong emptyResponses = new AtomicLong();
        private volatile int actualDimension = -1;

        AuditingEmbeddingModel(EmbeddingModel delegate, int expectedDimensions) {
            this.delegate = delegate;
            this.expectedDimensions = expectedDimensions;
        }

        void start() {
            zeroVectors.set(0);
            dimensionMismatch.set(0);
            emptyVectors.set(0);
            emptyResponses.set(0);
            actualDimension = -1;
            recording.set(true);
        }

        EmbeddingIssues stop() {
            recording.set(false);
            return new EmbeddingIssues(zeroVectors.get(), dimensionMismatch.get(),
                    emptyVectors.get(), emptyResponses.get(), actualDimension);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            EmbeddingResponse response = delegate.call(request);
            if (!recording.get()) return response;
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                emptyResponses.incrementAndGet();
                return response;
            }
            for (Embedding embedding : response.getResults()) {
                inspect(embedding == null ? null : embedding.getOutput());
            }
            return response;
        }

        @Override
        public float[] embed(Document document) {
            float[] vector = delegate.embed(document);
            if (recording.get()) inspect(vector);
            return vector;
        }

        @Override
        public float[] embed(String text) {
            float[] vector = delegate.embed(text);
            if (recording.get()) inspect(vector);
            return vector;
        }

        @Override
        public int dimensions() {
            return expectedDimensions;
        }

        private void inspect(float[] vector) {
            if (vector == null || vector.length == 0) {
                emptyVectors.incrementAndGet();
                return;
            }
            if (actualDimension < 0) actualDimension = vector.length;
            if (expectedDimensions > 0 && vector.length != expectedDimensions) {
                dimensionMismatch.incrementAndGet();
                return;
            }
            for (float value : vector) {
                if (value != 0.0f) return;
            }
            zeroVectors.incrementAndGet();
        }
    }

    private record Corpus(List<Document> documents) {
    }
}
