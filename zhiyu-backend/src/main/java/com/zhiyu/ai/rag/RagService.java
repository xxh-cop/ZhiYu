package com.zhiyu.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 向量检索层：只负责可选的向量召回与来源过滤，生成与降级由 KnowledgeAskService 负责。
 * 索引带内容指纹；指纹变化时重建，旧切片不会越过当前来源集合。
 */
@Slf4j
@Service
@ConditionalOnBean(VectorStore.class)
public class RagService {

    public record VectorHit(String id, String title, String excerpt,
                            String sourceId, String corpusVersion) {
    }

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;
    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.3;

    private final VectorStore vectorStore;
    private final String vectorStorePath;
    private volatile Set<String> activeSourceIds = Set.of();
    private volatile String corpusVersion = "uninitialized";

    public RagService(VectorStore vectorStore,
                      @Value("${zhiyu.ai.rag.vector-store-path:./rag-store.json}") String vectorStorePath) {
        this.vectorStore = vectorStore;
        this.vectorStorePath = vectorStorePath;
    }

    /** Local lexical rerank over the vector shortlist; no extra model or external data transfer. */
    static List<Document> rerank(String question, List<Document> candidates) {
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
                .limit(TOP_K)
                .toList();
    }

    @PostConstruct
    public void init() {
        try {
            Corpus corpus = readCorpus();
            activeSourceIds = corpus.documents().stream()
                    .map(RagService::sourceId)
                    .collect(Collectors.toUnmodifiableSet());
            corpusVersion = corpus.fingerprint();

            File storeFile = new File(vectorStorePath);
            File versionFile = new File(vectorStorePath + ".version");
            if (storeFile.exists() && versionFile.exists()) {
                String persisted = Files.readString(versionFile.toPath(), StandardCharsets.UTF_8).trim();
                if (corpus.fingerprint().equals(persisted)) {
                    ((SimpleVectorStore) vectorStore).load(storeFile);
                    log.info("RAG 向量库从文件加载完成: {} (version={})", vectorStorePath, corpus.fingerprint());
                    return;
                }
                log.info("RAG 语料指纹变化，重建索引: {} -> {}", persisted, corpus.fingerprint());
            }
            buildIndex(corpus);
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
                        text.substring(0, Math.min(80, text.length())), sourceId, corpusVersion));
            }
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
        StringBuilder fingerprintInput = new StringBuilder();
        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String filename = Objects.toString(resource.getFilename(), "knowledge.md");
            docs.add(new Document(content, Map.of("source", filename, "sourceId", sourceId(filename))));
            fingerprintInput.append(filename).append('\n').append(content).append('\n');
        }
        return new Corpus(docs, sha256(fingerprintInput.toString()).substring(0, 12));
    }

    private void buildIndex(Corpus corpus) {
        try {
            if (corpus.documents().isEmpty()) {
                log.warn("RAG 知识库目录下未找到任何 .md 文件，跳过索引构建");
                return;
            }
            org.springframework.ai.transformer.splitter.TokenTextSplitter splitter =
                    new org.springframework.ai.transformer.splitter.TokenTextSplitter(
                            CHUNK_SIZE, CHUNK_OVERLAP, 5, 10000, true);
            List<Document> splitDocs = splitter.apply(corpus.documents());
            vectorStore.add(splitDocs);
            ((SimpleVectorStore) vectorStore).save(new File(vectorStorePath));
            Files.writeString(new File(vectorStorePath + ".version").toPath(),
                    corpus.fingerprint(), StandardCharsets.UTF_8);
            log.info("RAG 索引构建完成: {} 篇文档 → {} 个切片, version={}",
                    corpus.documents().size(), splitDocs.size(), corpus.fingerprint());
        } catch (Exception e) {
            log.error("RAG 索引构建失败: {}", e.getMessage(), e);
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

    private record Corpus(List<Document> documents, String fingerprint) {
    }
}
