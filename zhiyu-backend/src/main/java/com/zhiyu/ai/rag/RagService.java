package com.zhiyu.ai.rag;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 非遗知识库服务（M3-3）。
 *
 * <p>启动时从 {@code resources/rag/crafts/*.md} 加载非遗知识文档，
 * 用 {@link org.springframework.ai.transformer.splitter.TokenTextSplitter} 切片后
 * 写入 {@link SimpleVectorStore}（内存），并持久化到 JSON 文件。
 * 二次启动时直接从 JSON 文件加载，避免重复调豆包 API。
 *
 * <p>查询时先用向量相似度检索 top-K 切片，再拼装上下文交给 LLM 生成回答。
 *
 * <p>降级策略：VectorStore 不存在（未配置豆包 API Key）或检索无结果时，
 * 返回友好提示，不抛异常。
 */
@Slf4j
@Service
@ConditionalOnBean(VectorStore.class)
public class RagService {

    private final VectorStore vectorStore;
    private final AiClient aiClient;
    private final String ragPromptTemplate;

    @Value("${zhiyu.ai.rag.vector-store-path:./rag-store.json}")
    private String vectorStorePath;

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 64;
    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.3;

    public RagService(VectorStore vectorStore,
                      AiClient aiClient,
                      @Qualifier("ragPrompt") String ragPromptTemplate) {
        this.vectorStore = vectorStore;
        this.aiClient = aiClient;
        this.ragPromptTemplate = ragPromptTemplate;
    }

    /** Local lexical rerank over the vector shortlist; no extra model or external data transfer. */
    static List<Document> rerank(String question, List<Document> candidates) {
        java.util.Set<String> terms = new java.util.HashSet<>();
        String normalized = question.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        for (int i=0;i+1<normalized.length();i++) terms.add(normalized.substring(i,i+2));
        var ranked = new ArrayList<>(candidates);
        ranked.sort(java.util.Comparator.comparingDouble((Document d) -> {
            String body = java.util.Objects.toString(d.getText(), "").toLowerCase(java.util.Locale.ROOT);
            double lexical = terms.isEmpty()?0:(double)terms.stream().filter(body::contains).count()/terms.size();
            double vector = 1.0/(1+candidates.indexOf(d));
            return .65*vector+.35*lexical;
        }).reversed());
        return ranked.stream().filter(d->d.getText()!=null&&!d.getText().isBlank()).limit(TOP_K).toList();
    }

    @PostConstruct
    public void init() {
        File storeFile = new File(vectorStorePath);
        if (storeFile.exists()) {
            try {
                ((SimpleVectorStore) vectorStore).load(storeFile);
                log.info("RAG 向量库从文件加载完成: {}", vectorStorePath);
                return;
            } catch (Exception e) {
                log.warn("RAG 向量库从文件加载失败，将重新构建: {}", e.getMessage());
            }
        }
        buildIndex();
    }

    /**
     * 构建索引：读取 markdown 文档 → 切片 → 写入向量库 → 持久化。
     */
    private void buildIndex() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:rag/crafts/*.md");

            List<Document> docs = new ArrayList<>();
            for (Resource r : resources) {
                String content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Document doc = new Document(content, Map.of("source", r.getFilename()));
                docs.add(doc);
            }

            if (docs.isEmpty()) {
                log.warn("RAG 知识库目录下未找到任何 .md 文件，跳过索引构建");
                return;
            }

            org.springframework.ai.transformer.splitter.TokenTextSplitter splitter =
                    new org.springframework.ai.transformer.splitter.TokenTextSplitter(
                            CHUNK_SIZE, CHUNK_OVERLAP, 5, 10000, true);
            List<Document> splitDocs = splitter.apply(docs);

            vectorStore.add(splitDocs);
            log.info("RAG 索引构建完成: {} 篇文档 → {} 个切片", docs.size(), splitDocs.size());

            ((SimpleVectorStore) vectorStore).save(new File(vectorStorePath));
            log.info("RAG 向量库已持久化到: {}", vectorStorePath);
        } catch (Exception e) {
            log.error("RAG 索引构建失败: {}", e.getMessage(), e);
        }
    }

    /**
     * RAG 问答：检索 → 拼装上下文 → LLM 生成回答。
     *
     * @param question 用户问题
     * @return 含回答和引用来源的结果
     */
    public RagAnswerVO ask(String question) {
        List<RagSourceVO> sources = new ArrayList<>();
        String answer = aiClient.knowledgeAnswer(question, () -> {
            // Retrieval can call an embedding provider: input audit must happen first.
            List<Document> candidates = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question).topK(8).similarityThreshold(SIMILARITY_THRESHOLD).build());
            if (candidates == null || candidates.isEmpty()) return null;
            List<Document> docs = rerank(question, candidates);
            if (docs.isEmpty()) return null;
            StringBuilder context = new StringBuilder();
            for (int i=0; i<docs.size(); i++) {
                Document d=docs.get(i);
                context.append("【资料").append(i+1).append("】\n").append(d.getText()).append("\n\n");
                sources.add(new RagSourceVO(java.util.Objects.toString(d.getMetadata().get("source"), "非遗知识库"),
                        d.getText().substring(0, Math.min(80,d.getText().length()))));
            }
            return ragPromptTemplate.replace("{context}",context.toString()).replace("{question}",question);
        });
        return new RagAnswerVO(answer, sources);
    }
}
