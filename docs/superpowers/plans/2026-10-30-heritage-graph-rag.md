# 非遗图增强检索与可追溯引用实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 RAG 上增加有界关系检索，让未配置向量服务时仍能返回可核对的非遗资料，并把检索模式、来源类型、版本与关系链明确交给学生端。

**Architecture:** 以 `heritage-catalog.json` 为唯一已审核关系源，构建 4 门工艺—24 个原创纹样—4 条已登记来源的轻量图索引；每次请求先做结构化检索，再可选合并现有向量召回，由 `KnowledgeAskService` 统一决定检索模式、降级回答与引用。向量索引增加语料指纹和来源下架过滤；学生端增加检索模式标签、可点击来源卡与首版 Canvas 关系图。

**Tech Stack:** Java 17、Spring Boot 3.4、Spring AI 1.0、JUnit 5、Mockito、Vue 3.4、UniApp、Canvas 2D、node:test。

**Spec:** `docs/superpowers/specs/2026-10-29-heritage-student-experience-design.md` 第 7.1 节

## Global Constraints

- 所有 AI 调用仍走 `AiClient`；提示词外置；内容安全过滤与审计保留；`llm-rag` 熔断按资源隔离，不共享计数器。
- 不把“关系检索”宣传成 LightRAG 或已部署向量数据库；向量不可用时必须显示 `STRUCTURED_GRAPH`，不能把纯关键词/结构检索称为向量模型已接入。
- 高危输入命中时保留 `AiClient` 关怀话术与危机流程，不用结构化摘录覆盖。
- 来源只作材料与技艺背景参考；纹样仍是 `ORIGINAL_STUDY`，不得写成传统原作；不出现诊断性措辞。
- 所有 ID 在 JSON/API/前端中保持字符串，不转 JavaScript Number。
- 新接口/字段同步 `openapi/openapi.yaml` 与 Gherkin 场景；后端改动执行定向测试，前端改动执行 H5 与 mp-weixin 构建。
- 不修改原首页布局，不删除原功能；不触碰用户修改的 docx。
- 不提交 `.env`、真实密钥、`target/`、`node_modules/`、`dist/`、`.ai-tmp/`；截图放 `output/playwright/`。
- Git 仅允许 `add`/`commit`/只读命令；提交前必须展示 `git status --short` 与 `git diff --stat` 并得到用户确认；禁止 `push`/`merge`/`rebase`/`reset`/`checkout`。
- 中文资源显式 UTF-8；Maven 使用 `"-Dmaven.repo.local=../.ai-tmp/m2"`。

---

### Task 1: 结构化关系索引

**Files:**
- Create: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/graph/GraphHit.java`
- Create: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/graph/HeritageGraphIndex.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/ai/rag/graph/HeritageGraphIndexTest.java`

**Interfaces:**
- Consumes: `HeritageCatalogService.baseCatalog()` / `catalog()`，返回 `HeritageCatalog`。
- Produces: `HeritageGraphIndex.search(String question)` → `List<GraphHit>`；包内静态重载 `search(String, HeritageCatalog)` 供单测使用。`MAX_HITS = 6`。

- [ ] **Step 1: 写失败测试**

```java
package com.zhiyu.ai.rag.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeritageGraphIndexTest {

    private static HeritageCatalog catalog;

    @BeforeAll
    static void load() throws Exception {
        catalog = new HeritageCatalogService(new ObjectMapper()).baseCatalog();
    }

    @Test
    void findsTraditionPatternAndVerifiedSourceForWeaveQuestion() {
        var hits = HeritageGraphIndex.search("瑞昌竹编的纹样和来源", catalog);
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("970102")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeType().equals("PATTERN")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-bamboo")));
        assertTrue(hits.stream().filter(hit -> hit.nodeType().equals("SOURCE"))
                .allMatch(hit -> hit.url().startsWith("https://")));
        assertTrue(hits.stream().anyMatch(hit -> hit.relationPath().contains("→")));
    }

    @Test
    void expandsMaterialQuestionInsideTheMatchedTradition() {
        var hits = HeritageGraphIndex.search("夏布绣的材料", catalog);
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("970101")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-xiabu")));
        assertFalse(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-bamboo")));
    }

    @Test
    void findsWindowPatternUnderPapercutTradition() {
        var hits = HeritageGraphIndex.search("剪纸 方窗", catalog);
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("970103")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeType().equals("PATTERN")
                && hit.title().contains("窗")));
    }

    @Test
    void unrelatedDiagnosticQuestionReturnsEmpty() {
        assertTrue(HeritageGraphIndex.search("抑郁症是什么", catalog).isEmpty());
    }

    @Test
    void resultsAreBoundedAndDeduplicated() {
        var hits = HeritageGraphIndex.search("瑞昌竹编 竹编 纹样 来源 夏布绣", catalog);
        assertTrue(hits.size() <= HeritageGraphIndex.MAX_HITS);
        assertEquals(hits.size(), hits.stream().map(GraphHit::nodeId).distinct().count());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd zhiyu-backend && mvn -q -Dtest=HeritageGraphIndexTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 编译失败，`GraphHit` / `HeritageGraphIndex` 不存在。

- [ ] **Step 3: 实现 GraphHit**

```java
package com.zhiyu.ai.rag.graph;

import java.util.List;

/** 结构化关系检索命中的唯一条目；sourceIds 指向当前目录中的已登记来源。 */
public record GraphHit(String nodeId, String nodeType, String title, String excerpt,
                       String url, String organization, String mediaReuse,
                       List<String> sourceIds, String relationPath, String catalogVersion) {
}
```

- [ ] **Step 4: 实现 HeritageGraphIndex**

```java
package com.zhiyu.ai.rag.graph;

import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 有界的非遗关系检索：只使用已审核目录，不调用模型、不递归超过一跳。
 * 每次请求从当前目录重建小图，因此管理端覆盖与来源下架立即生效。
 */
@Component
public class HeritageGraphIndex {

    public static final int MAX_HITS = 6;
    private static final Pattern LATIN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Set<String> STOP = Set.of(
            "什么", "怎么", "如何", "哪些", "可以", "非遗", "了解", "介绍",
            "这个", "那个", "有", "的", "和", "与", "吗", "呢");

    private final HeritageCatalogService catalogs;

    public HeritageGraphIndex(HeritageCatalogService catalogs) {
        this.catalogs = catalogs;
    }

    public List<GraphHit> search(String question) {
        return search(question, catalogs.catalog());
    }

    static List<GraphHit> search(String question, HeritageCatalog catalog) {
        String normalized = normalize(question);
        if (catalog == null || normalized.isBlank()) return List.of();
        Set<String> terms = terms(normalized);
        if (terms.isEmpty()) return List.of();

        List<Node> nodes = nodes(catalog);
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) byId.put(node.id(), node);

        List<Scored> seeds = nodes.stream()
                .map(node -> new Scored(node, score(normalized, terms, node)))
                .filter(scored -> scored.score() > 0.8)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingInt(scored -> rank(scored.node().type()))
                        .thenComparing(scored -> scored.node().id()))
                .limit(3)
                .toList();
        if (seeds.isEmpty()) return List.of();

        LinkedHashMap<String, GraphHit> hits = new LinkedHashMap<>();
        for (Scored seed : seeds) {
            add(hits, seed.node(), seed.node().title(), catalog.version());
        }
        for (Scored seed : seeds) {
            for (Node neighbor : neighbors(seed.node(), catalog, byId, normalized, terms)) {
                add(hits, neighbor, seed.node().title() + " → " + neighbor.title(), catalog.version());
                if (hits.size() >= MAX_HITS) return List.copyOf(hits.values());
            }
        }
        return List.copyOf(hits.values());
    }

    private static void add(Map<String, GraphHit> hits, Node node, String relation, String version) {
        if (hits.size() >= MAX_HITS || hits.containsKey(node.id())) return;
        hits.put(node.id(), new GraphHit(node.id(), node.type(), node.title(), node.excerpt(),
                node.url(), node.organization(), node.mediaReuse(), node.sourceIds(), relation, version));
    }

    private static List<Node> neighbors(Node seed, HeritageCatalog catalog, Map<String, Node> byId,
                                        String normalized, Set<String> terms) {
        List<Node> out = new ArrayList<>();
        switch (seed.type()) {
            case "TRADITION" -> {
                catalog.patterns().stream()
                        .filter(pattern -> pattern.traditionId().equals(seed.id()))
                        .sorted(Comparator.comparingDouble((HeritageCatalog.Pattern pattern) ->
                                score(normalized, terms, byId.get(pattern.id()))).reversed())
                        .limit(2)
                        .map(pattern -> byId.get(pattern.id()))
                        .forEach(out::add);
                seed.sourceIds().stream().map(byId::get).filter(Objects::nonNull).forEach(out::add);
            }
            case "PATTERN" -> {
                catalog.patterns().stream().filter(p -> p.id().equals(seed.id())).findFirst()
                        .ifPresent(pattern -> {
                            Node tradition = byId.get(pattern.traditionId());
                            if (tradition != null) out.add(tradition);
                            pattern.sourceIds().stream().map(byId::get).filter(Objects::nonNull).forEach(out::add);
                        });
            }
            case "SOURCE" -> {
                catalog.traditions().stream().filter(t -> t.sourceIds().contains(seed.id()))
                        .limit(2).map(t -> byId.get(t.id())).filter(Objects::nonNull).forEach(out::add);
                catalog.patterns().stream().filter(p -> p.sourceIds().contains(seed.id()))
                        .limit(2).map(p -> byId.get(p.id())).filter(Objects::nonNull).forEach(out::add);
            }
            default -> { }
        }
        return out;
    }

    private static List<Node> nodes(HeritageCatalog catalog) {
        List<Node> nodes = new ArrayList<>();
        for (HeritageCatalog.Tradition tradition : catalog.traditions()) {
            nodes.add(new Node(tradition.id(), "TRADITION", tradition.name(),
                    String.join(" ", safe(tradition.summary()), safe(tradition.region()),
                            String.join("、", safe(tradition.materials())),
                            String.join("、", safe(tradition.process()))),
                    safe(tradition.summary()), safe(tradition.sourceIds()),
                    null, null, null));
        }
        for (HeritageCatalog.Pattern pattern : catalog.patterns()) {
            nodes.add(new Node(pattern.id(), "PATTERN", pattern.name(),
                    String.join(" ", safe(pattern.designNote()), safe(pattern.learningFocus()),
                            safe(pattern.level())),
                    safe(pattern.learningFocus()), safe(pattern.sourceIds()),
                    null, null, null));
        }
        for (HeritageCatalog.Source source : catalog.sources()) {
            String facts = String.join(" ", safe(source.facts()));
            nodes.add(new Node(source.id(), "SOURCE", source.title(),
                    source.title() + " " + source.organization() + " " + facts,
                    facts.length() > 120 ? facts.substring(0, 120) : facts,
                    List.of(source.id()), source.url(), source.organization(), source.mediaReuse()));
        }
        return nodes;
    }

    private static double score(String normalized, Set<String> terms, Node node) {
        if (node == null) return 0;
        String title = normalize(node.title());
        double score = 0;
        if (!title.isBlank() && normalized.equals(title)) score += 20;
        else if (title.length() >= 2 && (normalized.contains(title) || title.contains(normalized))) score += 10;

        String text = normalize(node.title() + " " + node.text());
        int matched = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                matched++;
            } else if (term.length() == 2
                    && (text.contains(term.substring(0, 1)) || text.contains(term.substring(1)))) {
                score += 0.35;
            }
        }
        return score + 8.0 * matched / Math.max(1, terms.size());
    }

    static Set<String> terms(String normalized) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher latin = LATIN.matcher(normalized);
        while (latin.find()) terms.add(latin.group());
        Matcher han = HAN.matcher(normalized);
        while (han.find()) {
            String run = han.group();
            for (int i = 0; i + 1 < run.length(); i++) {
                String term = run.substring(i, i + 2);
                if (!STOP.contains(term)) terms.add(term);
            }
        }
        return terms;
    }

    private static String normalize(String value) {
        String text = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return text.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static int rank(String type) {
        return switch (type) {
            case "TRADITION" -> 0;
            case "PATTERN" -> 1;
            default -> 2;
        };
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Node(String id, String type, String title, String text, String excerpt,
                        List<String> sourceIds, String url, String organization, String mediaReuse) {
    }

    private record Scored(Node node, double score) {
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd zhiyu-backend && mvn -q -Dtest=HeritageGraphIndexTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 5 tests pass。

- [ ] **Step 6: 提交检查点**

Run: `git status --short && git diff --stat`
Expected: 只出现本任务文件；停止并等待用户确认后再 commit，不 push。

---

### Task 2: 向量检索元数据、来源下架过滤与语料指纹

**Files:**
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/RagService.java`（替换现有实现）
- Test: `zhiyu-backend/src/test/java/com/zhiyu/ai/rag/RagServiceTest.java`

**Interfaces:**
- Produces: `RagService.VectorHit(String id, String title, String excerpt, String sourceId, String corpusVersion)`；`RagService.retrieve(String question)` → `List<VectorHit>`。
- Produces: `activeSourceIds` 与 `corpusVersion` 在 `@PostConstruct init()` 后可用；检索时按当前 classpath 文档集合过滤旧切片。

- [ ] **Step 1: 写失败测试**

```java
package com.zhiyu.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @Test
    void retrieveDropsWithdrawnSourcesAndKeepsVersion() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("竹编以竹篾交织成形，观察经纬关系。",
                        Map.of("source", "bianzhi.md", "sourceId", "bianzhi")),
                new Document("已下架的旧资料不应继续被引用。",
                        Map.of("source", "removed.md", "sourceId", "removed"))));
        RagService service = new RagService(store, "target/rag-test.json");
        ReflectionTestUtils.setField(service, "activeSourceIds", Set.of("bianzhi"));
        ReflectionTestUtils.setField(service, "corpusVersion", "v-test");

        var hits = service.retrieve("竹编");
        assertEquals(1, hits.size());
        assertEquals("bianzhi", hits.get(0).sourceId());
        assertEquals("v-test", hits.get(0).corpusVersion());
    }

    @Test
    void retrieveReturnsEmptyWhenProviderFails() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("offline"));
        RagService service = new RagService(store, "target/rag-test.json");
        assertTrue(service.retrieve("竹编").isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd zhiyu-backend && mvn -q -Dtest=RagServiceTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 编译失败，`RagService(VectorStore,String)` 与 `retrieve` 不存在。

- [ ] **Step 3: 替换 RagService**

```java
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
import java.util.LinkedHashSet;
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
```

- [ ] **Step 4: 运行测试与现有 RAG 门面测试**

Run: `cd zhiyu-backend && mvn -q -Dtest=RagServiceTest,KnowledgeAnswerTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: `RagServiceTest` 2 项 + `KnowledgeAnswerTest` 3 项通过。

- [ ] **Step 5: 提交检查点**

Run: `git status --short && git diff --stat`
Expected: 只出现本任务文件；等待用户确认后再 commit。

---

### Task 3: 统一问答编排、引用契约与 API

**Files:**
- Create: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/KnowledgeAskService.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java`（新增 `KnowledgeResult`）
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/RagController.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/vo/RagAnswerVO.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/rag/vo/RagSourceVO.java`
- Modify: `zhiyu-backend/src/main/resources/prompts/rag.st`
- Modify: `openapi/openapi.yaml`
- Create: `features/24-heritage-graph-rag.feature`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/ai/rag/KnowledgeAskServiceTest.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/ai/rag/RagControllerTest.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/ai/KnowledgeAnswerTest.java`（追加）

**Interfaces:**
- Consumes: `HeritageGraphIndex.search`, `RagService.retrieve`, `AiClient.knowledgeAnswerResult`.
- Produces: `RagAnswerVO(answer, sources, retrievalMode, generated)`，模式为 `GRAPH_VECTOR` / `STRUCTURED_GRAPH` / `VECTOR` / `NONE`。
- Produces: `RagSourceVO(id, title, excerpt, url, organization, mediaReuse, sourceVersion, relation, citationType)`。

- [ ] **Step 1: 写失败测试**

```java
package com.zhiyu.ai.rag;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.graph.HeritageGraphIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeAskServiceTest {

    private HeritageGraphIndex graph;
    private RagService rag;
    private AiClient ai;
    private KnowledgeAskService service;

    @BeforeEach
    void setUp() {
        graph = mock(HeritageGraphIndex.class);
        rag = mock(RagService.class);
        ai = mock(AiClient.class);
        service = new KnowledgeAskService(graph, rag, ai,
                "资料：{context}\n问题：{question}");
        when(ai.reviewKnowledgeQuestion(anyString()))
                .thenReturn(new AiClient.KnowledgeResult("", false, false));
    }

    private GraphHit sourceHit() {
        return new GraphHit("ihc-bamboo", "SOURCE", "竹编（瑞昌竹编）",
                "项目地域为江西瑞昌，以竹篾交织成形。", "https://www.ihchina.cn/project_details/1.html",
                "中国非物质文化遗产网", "NOT_GRANTED", List.of("ihc-bamboo"),
                "竹编（瑞昌竹编） → 竹编（瑞昌竹编）", "v1");
    }

    private GraphHit traditionHit() {
        return new GraphHit("970102", "TRADITION", "竹编（瑞昌竹编）",
                "安排竹篾的经纬关系，观察疏密与留白。", null, null, null,
                List.of("ihc-bamboo"), "竹编（瑞昌竹编）", "v1");
    }

    @Test
    void structuredOnlyReturnsExtractiveAnswerWithoutModelCall() {
        when(graph.search("瑞昌竹编")).thenReturn(List.of(traditionHit(), sourceHit()));
        when(rag.retrieve("瑞昌竹编")).thenReturn(List.of());

        var result = service.ask("瑞昌竹编");
        assertEquals("STRUCTURED_GRAPH", result.getRetrievalMode());
        assertFalse(result.isGenerated());
        assertTrue(result.getAnswer().contains("未调用生成模型"));
        assertEquals("VERIFIED_SOURCE", result.getSources().get(0).getCitationType());
        assertTrue(result.getSources().get(0).getUrl().startsWith("https://"));
        verify(ai).reviewKnowledgeQuestion("瑞昌竹编");
        verify(ai, never()).knowledgeAnswerResultReviewed(anyString(), any(Supplier.class));
    }

    @Test
    void graphAndVectorUseGeneratedAnswer() {
        when(graph.search("瑞昌竹编")).thenReturn(List.of(traditionHit(), sourceHit()));
        when(rag.retrieve("瑞昌竹编")).thenReturn(List.of(
                new RagService.VectorHit("bianzhi#1", "bianzhi.md", "竹编以竹篾交织。",
                        "bianzhi", "rag-v1")));
        when(ai.knowledgeAnswerResultReviewed(anyString(), any(Supplier.class)))
                .thenReturn(new AiClient.KnowledgeResult("生成回答", true, false));

        var result = service.ask("瑞昌竹编");
        assertEquals("GRAPH_VECTOR", result.getRetrievalMode());
        assertTrue(result.isGenerated());
        assertEquals("生成回答", result.getAnswer());
        assertEquals(2, result.getSources().size());
        assertEquals("REFERENCE", result.getSources().get(1).getCitationType());
    }

    @Test
    void noHitsReturnsNoneWithoutModelCall() {
        when(graph.search("不存在")).thenReturn(List.of());
        when(rag.retrieve("不存在")).thenReturn(List.of());
        var result = service.ask("不存在");
        assertEquals("NONE", result.getRetrievalMode());
        assertTrue(result.getSources().isEmpty());
        verify(ai).reviewKnowledgeQuestion("不存在");
        verify(ai, never()).knowledgeAnswerResultReviewed(anyString(), any(Supplier.class));
    }

    @Test
    void blockedInputKeepsCareMessageAndDropsSources() {
        when(ai.reviewKnowledgeQuestion("高危"))
                .thenReturn(new AiClient.KnowledgeResult("请先寻求支持", false, true));
        var result = service.ask("高危");
        assertEquals("NONE", result.getRetrievalMode());
        assertEquals("请先寻求支持", result.getAnswer());
        assertTrue(result.getSources().isEmpty());
        verifyNoInteractions(graph, rag);
        verify(ai, never()).knowledgeAnswerResultReviewed(anyString(), any(Supplier.class));
    }

    @Test
    void vectorFailureDegradesToStructuredGraph() {
        when(graph.search("瑞昌竹编")).thenReturn(List.of(traditionHit(), sourceHit()));
        when(rag.retrieve("瑞昌竹编")).thenThrow(new RuntimeException("offline"));
        var result = service.ask("瑞昌竹编");
        assertEquals("STRUCTURED_GRAPH", result.getRetrievalMode());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd zhiyu-backend && mvn -q -Dtest=KnowledgeAskServiceTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 编译失败，`KnowledgeAskService` / `KnowledgeResult` / 新 VO 字段不存在。

- [ ] **Step 3: 给 AiClient 增加可区分“生成/阻断”的结果**

在 `AiClient` 中新增：

```java
public record KnowledgeResult(String text, boolean generated, boolean blocked) {
}

/** 结构化检索前的输入审核；不调用模型，供向量不可用的问答链路复用。 */
public KnowledgeResult reviewKnowledgeQuestion(String question) {
    SafetyResult input = contentSafetyFilter.checkInput(UserContext.getUserId(), null, "rag", question);
    if (input.getAction() == SafetyAction.BLOCK) {
        return new KnowledgeResult(input.getCareMessage(), false, true);
    }
    return new KnowledgeResult("", false, false);
}

public String knowledgeAnswer(String question, String prompt) {
    return knowledgeAnswerResult(question, () -> prompt).text();
}

public String knowledgeAnswer(String question, java.util.function.Supplier<String> promptSupplier) {
    return knowledgeAnswerResult(question, promptSupplier).text();
}

public KnowledgeResult knowledgeAnswerResult(String question,
                                             java.util.function.Supplier<String> promptSupplier) {
    KnowledgeResult review = reviewKnowledgeQuestion(question);
    if (review.blocked()) return review;
    return generateKnowledgeAnswer(promptSupplier);
}

/** 已由 reviewKnowledgeQuestion 审核过输入时使用，避免重复审计。 */
public KnowledgeResult knowledgeAnswerResultReviewed(String question,
                                                     java.util.function.Supplier<String> promptSupplier) {
    return generateKnowledgeAnswer(promptSupplier);
}

private KnowledgeResult generateKnowledgeAnswer(java.util.function.Supplier<String> promptSupplier) {
    String fallback = "非遗问答暂时不可用，请稍后再试。";
    if (!breaker.allowRequest("llm-rag")) return new KnowledgeResult(fallback, false, false);
    String answer;
    try {
        String prompt = promptSupplier.get();
        if (prompt == null) {
            return new KnowledgeResult("知识库暂未找到相关资料，可以换个关键词试试。", false, false);
        }
        answer = llmClient.chat(prompt);
        if (answer == null || answer.isBlank()) throw new IllegalStateException("empty answer");
        breaker.recordSuccess("llm-rag");
    } catch (Exception e) {
        breaker.recordFailure("llm-rag");
        log.warn("知识问答调用失败，已使用固定降级文本");
        return new KnowledgeResult(fallback, false, false);
    }
    return new KnowledgeResult(applyOutputSafety("rag", answer), true, false);
}
```

保留原 `knowledgeAnswer` 两个重载，现有调用与测试不破坏。`KnowledgeAskService` 在检索前调用 `reviewKnowledgeQuestion`；高风险直接返回关怀话术，不读取图谱或向量。

在 `KnowledgeAnswerTest` 末尾追加：

```java
@Test
void reviewQuestionReturnsCareMessageOnBlock() {
    var llm = mock(LlmClient.class);
    var breaker = mock(CircuitBreakerStore.class);
    var safety = mock(ContentSafetyFilter.class);
    when(safety.checkInput(any(), any(), eq("rag"), anyString()))
            .thenReturn(SafetyResult.block("test", "请先寻求支持"));
    var result = new AiClient(llm, breaker, safety).reviewKnowledgeQuestion("高危");
    assertTrue(result.blocked());
    assertFalse(result.generated());
    assertEquals("请先寻求支持", result.text());
    verifyNoInteractions(llm);
}

@Test
void reviewedAnswerSkipsDuplicateInputAudit() {
    var llm = mock(LlmClient.class);
    var breaker = mock(CircuitBreakerStore.class);
    var safety = mock(ContentSafetyFilter.class);
    when(breaker.allowRequest("llm-rag")).thenReturn(true);
    when(llm.chat("prompt")).thenReturn("answer");
    when(safety.checkOutput(any(), eq("rag"), anyString())).thenReturn(SafetyResult.pass("pass"));
    var result = new AiClient(llm, breaker, safety)
            .knowledgeAnswerResultReviewed("question", () -> "prompt");
    assertTrue(result.generated());
    assertFalse(result.blocked());
    verify(safety, never()).checkInput(any(), any(), eq("rag"), anyString());
}
```

- [ ] **Step 4: 实现 VO 与 KnowledgeAskService**

`RagSourceVO`:

```java
package com.zhiyu.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagSourceVO {
    private String id;
    private String title;
    private String excerpt;
    private String url;
    private String organization;
    private String mediaReuse;
    private String sourceVersion;
    private String relation;
    private String citationType;
}
```

`RagAnswerVO`:

```java
package com.zhiyu.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RagAnswerVO {
    private String answer;
    private List<RagSourceVO> sources;
    private String retrievalMode;
    private boolean generated;

    public RagAnswerVO(String answer, List<RagSourceVO> sources) {
        this(answer, sources, "NONE", false);
    }
}
```

`KnowledgeAskService`:

```java
package com.zhiyu.ai.rag;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.graph.HeritageGraphIndex;
import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 非遗问答统一编排：结构化关系检索始终可用，向量召回可选。
 * 不把结构化检索称为向量模型；高风险输入不进入摘录回答。
 */
@Service
public class KnowledgeAskService {

    public static final String MODE_GRAPH_VECTOR = "GRAPH_VECTOR";
    public static final String MODE_STRUCTURED_GRAPH = "STRUCTURED_GRAPH";
    public static final String MODE_VECTOR = "VECTOR";
    public static final String MODE_NONE = "NONE";
    private static final String NO_RESULT =
            "暂时没有在已审核的非遗资料中找到可引用的内容，可以换成工艺、材料或纹样名称再试。";

    private final HeritageGraphIndex graphIndex;
    private final RagService ragService;
    private final AiClient aiClient;
    private final String promptTemplate;

    public KnowledgeAskService(HeritageGraphIndex graphIndex,
                               @Nullable RagService ragService,
                               AiClient aiClient,
                               @Qualifier("ragPrompt") String promptTemplate) {
        this.graphIndex = graphIndex;
        this.ragService = ragService;
        this.aiClient = aiClient;
        this.promptTemplate = promptTemplate;
    }

    public RagAnswerVO ask(String question) {
        AiClient.KnowledgeResult review = aiClient.reviewKnowledgeQuestion(question);
        if (review.blocked()) {
            return new RagAnswerVO(review.text(), List.of(), MODE_NONE, false);
        }
        List<GraphHit> graphHits = graphIndex.search(question);
        List<RagService.VectorHit> vectorHits;
        try {
            vectorHits = ragService == null ? List.of() : ragService.retrieve(question);
        } catch (Exception e) {
            vectorHits = List.of();
        }
        List<RagSourceVO> sources = sources(graphHits, vectorHits);
        if (sources.isEmpty()) {
            return new RagAnswerVO(NO_RESULT, List.of(), MODE_NONE, false);
        }

        String mode = !graphHits.isEmpty() && !vectorHits.isEmpty() ? MODE_GRAPH_VECTOR
                : !graphHits.isEmpty() ? MODE_STRUCTURED_GRAPH : MODE_VECTOR;
        if (MODE_STRUCTURED_GRAPH.equals(mode)) {
            return new RagAnswerVO(extractive(graphHits, vectorHits), sources, mode, false);
        }

        String prompt = promptTemplate
                .replace("{context}", context(graphHits, vectorHits))
                .replace("{question}", question);
        AiClient.KnowledgeResult result = aiClient.knowledgeAnswerResultReviewed(question, () -> prompt);
        if (!result.generated()) {
            return new RagAnswerVO(extractive(graphHits, vectorHits), sources, mode, false);
        }
        return new RagAnswerVO(result.text(), sources, mode, true);
    }

    private List<RagSourceVO> sources(List<GraphHit> graphHits, List<RagService.VectorHit> vectorHits) {
        LinkedHashMap<String, RagSourceVO> unique = new LinkedHashMap<>();
        graphHits.stream().filter(hit -> "SOURCE".equals(hit.nodeType())).forEach(hit ->
                unique.putIfAbsent(hit.nodeId(), new RagSourceVO(hit.nodeId(), hit.title(), hit.excerpt(),
                        hit.url(), hit.organization(), hit.mediaReuse(), hit.catalogVersion(),
                        hit.relationPath(), "VERIFIED_SOURCE")));
        for (RagService.VectorHit hit : vectorHits) {
            unique.putIfAbsent(hit.id(), new RagSourceVO(hit.id(), hit.title(), hit.excerpt(),
                    null, null, null, hit.corpusVersion(), "检索文档", "REFERENCE"));
        }
        return unique.values().stream().limit(6).toList();
    }

    private String context(List<GraphHit> graphHits, List<RagService.VectorHit> vectorHits) {
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (GraphHit hit : graphHits) {
            String label = switch (hit.nodeType()) {
                case "SOURCE" -> "已核实来源";
                case "TRADITION" -> "已登记工艺";
                default -> "原创教学纹样";
            };
            context.append("【资料").append(index++).append("｜").append(label).append("】")
                    .append(hit.title()).append('\n')
                    .append(hit.excerpt()).append('\n')
                    .append("关系：").append(hit.relationPath()).append("\n\n");
        }
        for (RagService.VectorHit hit : vectorHits) {
            context.append("【资料").append(index++).append("｜检索参考】")
                    .append(hit.title()).append('\n')
                    .append(hit.excerpt()).append("\n\n");
        }
        return context.toString();
    }

    private String extractive(List<GraphHit> graphHits, List<RagService.VectorHit> vectorHits) {
        StringBuilder answer = new StringBuilder("以下为结构化资料检索结果，未调用生成模型：\n");
        graphHits.stream().filter(hit -> "TRADITION".equals(hit.nodeType())).findFirst()
                .ifPresent(hit -> answer.append("工艺：").append(hit.excerpt()).append('\n'));
        graphHits.stream().filter(hit -> "PATTERN".equals(hit.nodeType())).findFirst()
                .ifPresent(hit -> answer.append("纹样：").append(hit.title())
                        .append("——").append(hit.excerpt()).append('\n'));
        List<GraphHit> verified = graphHits.stream()
                .filter(hit -> "SOURCE".equals(hit.nodeType())).limit(2).toList();
        if (!verified.isEmpty()) {
            answer.append("可核对来源：")
                    .append(verified.stream().map(GraphHit::title).collect(Collectors.joining("；")))
                    .append("。\n");
        } else if (!vectorHits.isEmpty()) {
            answer.append("参考资料：").append(vectorHits.get(0).excerpt()).append('\n');
        }
        return answer.append("来源仅作材料与技艺背景参考；本系统不能替代专业心理咨询。").toString();
    }
}
```

- [ ] **Step 5: 改造 RagController**

```java
@Slf4j
@RestController
@RequestMapping("/api/ai/rag")
public class RagController {

    private final KnowledgeAskService knowledgeAskService;

    public RagController(KnowledgeAskService knowledgeAskService) {
        this.knowledgeAskService = knowledgeAskService;
        log.info("RagController 初始化: 结构化关系检索就绪");
    }

    @PostMapping("/ask")
    public Result<RagAnswerVO> ask(@Valid @RequestBody RagAskDTO dto) {
        log.info("RAG 问答请求");
        return Result.success(knowledgeAskService.ask(dto.getQuestion()));
    }
}
```

删除 `@Autowired @Nullable RagService` 与 4001 分支。

- [ ] **Step 6: 补 OpenAPI 与 Gherkin**

`RagAnswerVO` 增加：

```yaml
retrievalMode:
  type: string
  enum: [GRAPH_VECTOR, STRUCTURED_GRAPH, VECTOR, NONE]
  description: '检索模式；STRUCTURED_GRAPH 表示未使用向量服务'
generated:
  type: boolean
  description: '是否由生成模型产出；false 时回答为结构化摘录或降级提示'
```

`RagSourceVO` 增加：

```yaml
id: { type: string, description: '稳定来源/切片 ID' }
url: { type: string, nullable: true, description: '已登记来源链接；检索参考为空' }
organization: { type: string, nullable: true }
mediaReuse: { type: string, nullable: true, description: 'NOT_GRANTED / GRANTED' }
sourceVersion: { type: string, description: '目录版本或语料指纹' }
relation: { type: string, nullable: true, description: '关系链，例如 工艺 → 来源' }
citationType:
  type: string
  enum: [VERIFIED_SOURCE, REFERENCE]
  description: 'VERIFIED_SOURCE 为已登记来源，REFERENCE 为检索参考'
```

`features/24-heritage-graph-rag.feature` 至少包含：

> 实现时发现 `openapi.yaml` 里 `InheritorProfileSelfVO`、`InheritorProfileSelfUpdateReq`、`UserStatsVO`、`RagAskReq`、`RagAnswerVO`、`RagSourceVO` 被误写在 `paths` 末尾（4 空格缩进），解析器不会把它们当组件。必须把这些块移回 `components.schemas`，并验证 `paths['/api/ai/rag/ask']` 只剩 `post`。

```gherkin
#language: zh-CN
功能: 非遗图增强检索
  作为登录学生
  我希望问答能区分结构化关系检索与向量检索
  以便核对来源，并在向量服务未配置时仍能获得资料

  场景: 未配置向量服务时返回结构化关系检索
    假定 学生已登录且向量服务未就绪
    当 学生询问“瑞昌竹编有什么特点”
    那么 返回 retrievalMode 为 STRUCTURED_GRAPH
    并且 generated 为 false
    并且 sources 包含已登记来源与 https 链接
    并且 回答注明“未调用生成模型”

  场景: 关系与向量同时命中
    假定 向量服务已就绪
    当 学生询问“瑞昌竹编的纹样”
    那么 retrievalMode 为 GRAPH_VECTOR
    并且 sources 同时包含 VERIFIED_SOURCE 与 REFERENCE
    并且 生成回答保留来源列表

  场景: 来源下架后旧向量不再引用
    假定 旧索引中仍有 removed.md 的切片
    并且 当前 classpath 已无 removed.md
    当 学生检索相关问题
    那么 不返回 sourceId 为 removed 的引用
    并且 其余来源仍可返回

  场景: 无资料时不编造
    当 学生询问与目录无关的问题
    那么 retrievalMode 为 NONE
    并且 sources 为空
    并且 不调用生成模型

  场景: 高危输入保留关怀话术
    当 学生输入高危表达
    那么 返回关怀话术
    并且 不把结构化摘录覆盖为回答
    并且 不生成诊断结论

  场景: 回答不声称已部署向量数据库
    当 页面展示检索模式
    那么 STRUCTURED_GRAPH 显示“结构化关系检索”
    并且 不显示“向量模型已接入”或“已部署 Chroma/Milvus”
```

- [ ] **Step 7: 增加控制器契约测试并运行后端测试**

`RagControllerTest`：

```java
package com.zhiyu.ai.rag;

import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagControllerTest {

    @Test
    void askExposesRetrievalModeWithoutVectorBean() throws Exception {
        KnowledgeAskService service = mock(KnowledgeAskService.class);
        when(service.ask("q")).thenReturn(new RagAnswerVO("结构化回答",
                List.of(new RagSourceVO("ihc-bamboo", "竹编（瑞昌竹编）", "瑞昌",
                        "https://www.ihchina.cn/", "中国非物质文化遗产网", "NOT_GRANTED",
                        "v1", "工艺 → 来源", "VERIFIED_SOURCE")),
                "STRUCTURED_GRAPH", false));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RagController(service)).build();

        mockMvc.perform(post("/api/ai/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.retrievalMode").value("STRUCTURED_GRAPH"))
                .andExpect(jsonPath("$.data.generated").value(false))
                .andExpect(jsonPath("$.data.sources[0].citationType").value("VERIFIED_SOURCE"));
    }
}
```

Run: `cd zhiyu-backend && mvn -q -Dtest=KnowledgeAskServiceTest,KnowledgeAnswerTest,RagServiceTest,HeritageGraphIndexTest,RagControllerTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 全部通过。

- [ ] **Step 8: 提交检查点**

Run: `git status --short && git diff --stat`
Expected: 等待用户确认后再 commit。

---

### Task 4: 学生端检索模式、来源卡与关系图

**Files:**
- Create: `zhiyu-app/src/utils/heritage-graph-layout.js`
- Create: `zhiyu-app/src/components/HeritageKnowledgeMap.vue`
- Modify: `zhiyu-app/src/pages/ai/rag-ask.vue`
- Test: `zhiyu-app/tests/heritage-graph-layout.test.mjs`

**Interfaces:**
- Consumes: `/api/ai/rag/ask` 返回的 `retrievalMode`、`generated`、`sources[*]` 新字段；`/api/heritage/catalog` 的 4 门工艺、24 个纹样、4 条来源。
- Produces: `buildHeritageGraph(catalog)` → `{ nodes, edges, groups }`；`HeritageKnowledgeMap` 的 `graph` prop。

- [ ] **Step 1: 写失败测试**

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { buildHeritageGraph, shortTitle } from '../src/utils/heritage-graph-layout.js'

const catalog = JSON.parse(fs.readFileSync(
  new URL('../../zhiyu-backend/src/main/resources/catalog/heritage-catalog.json', import.meta.url),
  'utf8'
))

test('builds four tradition nodes, four source nodes and closed edges', () => {
  const graph = buildHeritageGraph(catalog)
  assert.equal(graph.nodes.filter(node => node.type === 'tradition').length, 4)
  assert.equal(graph.nodes.filter(node => node.type === 'source').length, 4)
  assert.ok(graph.edges.length >= 4)
  const ids = new Set(graph.nodes.map(node => node.id))
  assert.ok(graph.edges.every(edge => ids.has(edge.from) && ids.has(edge.to)))
  assert.equal(graph.groups.length, 4)
  assert.equal(graph.groups.reduce((sum, group) => sum + group.patternCount, 0), 24)
  assert.deepEqual(buildHeritageGraph(catalog), graph)
})

test('shortens long source labels for canvas while keeping a full accessible list', () => {
  assert.ok(shortTitle('中国非物质文化遗产网·中国非物质文化遗产数字博物馆').length <= 8)
  const graph = buildHeritageGraph(catalog)
  assert.ok(graph.groups.every(group => group.sources.length > 0))
  assert.ok(graph.groups.every(group => group.patterns.length > 0))
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd zhiyu-app && node --test tests/heritage-graph-layout.test.mjs`
Expected: 模块不存在。

- [ ] **Step 3: 实现布局辅助**

```javascript
const MAX_LABEL = 8

export function shortTitle(value) {
  const text = String(value || '').trim()
  return text.length <= MAX_LABEL ? text : text.slice(0, MAX_LABEL)
}

export function buildHeritageGraph(catalog) {
  const traditions = Array.isArray(catalog && catalog.traditions) ? catalog.traditions.slice(0, 4) : []
  const sources = Array.isArray(catalog && catalog.sources) ? catalog.sources.slice(0, 4) : []
  const patterns = Array.isArray(catalog && catalog.patterns) ? catalog.patterns : []
  const nodes = []
  const edges = []

  traditions.forEach((tradition, index) => {
    const angle = -Math.PI / 2 + (index * Math.PI) / 2
    nodes.push({
      id: tradition.id,
      type: 'tradition',
      label: shortTitle(tradition.shortName || tradition.name),
      x: 50 + Math.cos(angle) * 26,
      y: 50 + Math.sin(angle) * 26,
    })
  })
  sources.forEach((source, index) => {
    const angle = -Math.PI / 4 + (index * Math.PI) / 2
    nodes.push({
      id: source.id,
      type: 'source',
      label: shortTitle(source.title),
      x: 50 + Math.cos(angle) * 42,
      y: 50 + Math.sin(angle) * 42,
    })
  })

  const nodeIds = new Set(nodes.map(node => node.id))
  const seen = new Set()
  traditions.forEach(tradition => {
    ;(tradition.sourceIds || []).forEach(sourceId => {
      const key = `${tradition.id}>${sourceId}`
      if (!nodeIds.has(tradition.id) || !nodeIds.has(sourceId) || seen.has(key)) return
      seen.add(key)
      edges.push({ from: tradition.id, to: sourceId })
    })
  })

  const groups = traditions.map(tradition => {
    const ownPatterns = patterns.filter(pattern => pattern.traditionId === tradition.id)
    return {
      id: tradition.id,
      title: tradition.name,
      region: tradition.region,
      patternCount: ownPatterns.length,
      patterns: ownPatterns.slice(0, 6).map(pattern => pattern.name),
      sources: sources
        .filter(source => (tradition.sourceIds || []).includes(source.id))
        .map(source => source.title),
    }
  })

  return { nodes, edges, groups }
}
```

- [ ] **Step 4: 实现关系图组件**

`HeritageKnowledgeMap.vue`：

```vue
<template>
	<view class="knowledge-map">
		<canvas
			id="heritage-knowledge-map"
			canvas-id="heritage-knowledge-map"
			class="knowledge-map-canvas"
			:style="{ width: size + 'px', height: size + 'px' }"
			role="img"
			:aria-label="canvasLabel"
			:data-drawn="drawn ? 'true' : 'false'"
		></canvas>
		<view class="knowledge-map-legend" aria-label="关系图图例">
			<view class="legend-item"><view class="legend-dot tradition"></view><text>工艺</text></view>
			<view class="legend-item"><view class="legend-dot source"></view><text>已登记来源</text></view>
		</view>
		<view class="knowledge-map-list">
			<view v-for="group in graph.groups" :key="group.id" class="knowledge-group">
				<text class="knowledge-group-title">{{ group.title }} · {{ group.patternCount }} 个纹样</text>
				<text class="knowledge-group-meta">{{ group.region }} · {{ group.sources.join('、') }}</text>
				<text class="knowledge-group-patterns">{{ group.patterns.join('、') }}</text>
			</view>
		</view>
	</view>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
	graph: { type: Object, required: true },
})

const instance = getCurrentInstance()
const size = computed(() => Math.min(Math.max(240, (uni.getSystemInfoSync().windowWidth || 375) - 96), 360))
const label = computed(() => props.graph.groups
	.map(group => `${group.title} ${group.patternCount} 个纹样`)
	.join('；'))
const canvasLabel = computed(() => `非遗关系图：${label.value}。完整关系在下方列表。`)
const drawn = ref(false)
let context = null
let timer = null

function draw() {
	if (!props.graph || !props.graph.nodes) return
	if (!context) context = uni.createCanvasContext('heritage-knowledge-map', instance.proxy)
	const sizeValue = size.value
	const byId = new Map(props.graph.nodes.map(node => [node.id, node]))
	drawn.value = false
	context.clearRect(0, 0, sizeValue, sizeValue)
	context.setFillStyle('#f7f9f4')
	context.fillRect(0, 0, sizeValue, sizeValue)
	context.setStrokeStyle('#c9d8ca')
	context.setLineWidth(1)
	for (const edge of props.graph.edges) {
		const from = byId.get(edge.from)
		const to = byId.get(edge.to)
		if (!from || !to) continue
		context.beginPath()
		context.moveTo((from.x / 100) * sizeValue, (from.y / 100) * sizeValue)
		context.lineTo((to.x / 100) * sizeValue, (to.y / 100) * sizeValue)
		context.stroke()
	}
	for (const node of props.graph.nodes) {
		const x = (node.x / 100) * sizeValue
		const y = (node.y / 100) * sizeValue
		const radius = node.type === 'tradition' ? 18 : 13
		context.beginPath()
		context.setFillStyle(node.type === 'tradition' ? '#2f7566' : '#bb914d')
		context.arc(x, y, radius, 0, Math.PI * 2)
		context.fill()
		context.setFillStyle('#233d34')
		context.setFontSize(node.type === 'tradition' ? 11 : 9)
		context.setTextAlign('center')
		context.fillText(node.label, x, y + 3)
	}
	context.draw(false, () => { drawn.value = true })
}

function drawLater() {
	if (timer) clearTimeout(timer)
	timer = setTimeout(draw, 160)
}

onMounted(() => nextTick(drawLater))
watch(() => props.graph, () => nextTick(drawLater), { deep: true })
onBeforeUnmount(() => {
	if (timer) clearTimeout(timer)
})
</script>

<style scoped>
.knowledge-map { padding: 8rpx 0 0; }
.knowledge-map-canvas { display: block; margin: 0 auto; border-radius: 20rpx; background: #f7f9f4; }
.knowledge-map-legend { display: flex; justify-content: center; gap: 24rpx; margin: 12rpx 0 16rpx; }
.legend-item { display: flex; align-items: center; gap: 8rpx; font-size: 21rpx; color: var(--zy-text, #586e62); }
.legend-dot { width: 16rpx; height: 16rpx; border-radius: 50%; }
.legend-dot.tradition { background: #2f7566; }
.legend-dot.source { background: #bb914d; }
.knowledge-map-list { display: flex; flex-direction: column; gap: 12rpx; }
.knowledge-group { padding: 14rpx 16rpx; border: 1rpx solid var(--zy-border, #dfe7dc); border-radius: 14rpx; background: var(--zy-bg, #f5f7f2); }
.knowledge-group-title { display: block; font-size: 24rpx; color: var(--zy-title, #233d34); font-weight: 600; }
.knowledge-group-meta, .knowledge-group-patterns { display: block; margin-top: 4rpx; font-size: 21rpx; line-height: 1.6; color: var(--zy-text, #586e62); word-break: break-word; }
</style>
```

- [ ] **Step 5: 接入 RAG 页面**

在 `rag-ask.vue` 中：

1. 顶部说明卡后加入可折叠关系图卡：

```vue
<view class="map-card">
	<button
		class="map-toggle"
		:aria-expanded="mapExpanded ? 'true' : 'false'"
		aria-controls="heritage-map-panel"
		@click="toggleMap"
	>
		<view>
			<text class="map-title">非遗关系图谱</text>
			<text class="map-desc">{{ graph ? '4 门工艺 · 24 个纹样 · 4 条已登记来源' : '正在读取目录…' }}</text>
		</view>
		<text class="map-action">{{ mapExpanded ? '收起' : '展开' }}</text>
	</button>
	<scroll-view v-if="mapExpanded" id="heritage-map-panel" class="map-panel" scroll-y>
		<view v-if="mapError" class="map-error" role="alert">
			<text>{{ mapError }}</text>
			<button class="map-retry" @click="loadGraph">重试</button>
		</view>
		<HeritageKnowledgeMap v-else-if="graph" :graph="graph" />
	</scroll-view>
</view>
```

2. AI 消息中加入模式：

```vue
<view class="answer-meta">
	<text class="mode-chip">{{ modeLabel(msg.retrievalMode) }}</text>
	<text v-if="msg.generated === false" class="mode-note">结构化资料 · 未调用生成模型</text>
</view>
```

3. 引用来源块改为：

```vue
<view v-for="(src, sIdx) in msg.sources" :key="src.id || sIdx" class="source-item">
	<view class="source-head">
		<text class="source-title">{{ src.title }}</text>
		<text class="source-badge">{{ src.citationType === 'VERIFIED_SOURCE' ? '已登记来源' : '检索参考' }}</text>
	</view>
	<text v-if="src.organization" class="source-org">{{ src.organization }}</text>
	<text class="source-excerpt">{{ src.excerpt }}</text>
	<text v-if="src.relation" class="source-relation">{{ src.relation }}</text>
	<text class="source-version">资料版本 {{ src.sourceVersion }}</text>
	<button
		v-if="src.url"
		class="source-link"
		role="link"
		:aria-label="'打开来源 ' + src.title"
		@click="openSource(src)"
	>打开已登记来源 ↗</button>
	<text v-if="src.mediaReuse === 'NOT_GRANTED'" class="source-media">仅作背景参考，媒体不转载</text>
</view>
```

4. 脚本：

```js
import { ref, nextTick, onMounted } from 'vue'
import HeritageKnowledgeMap from '../../components/HeritageKnowledgeMap.vue'
import { buildHeritageGraph } from '../../utils/heritage-graph-layout'

const graph = ref(null)
const mapExpanded = ref(false)
const mapError = ref('')

const MODE_LABELS = {
	GRAPH_VECTOR: '关系 + 向量检索',
	STRUCTURED_GRAPH: '结构化关系检索',
	VECTOR: '向量检索',
	NONE: '未找到资料',
}

function modeLabel(mode) {
	return MODE_LABELS[mode] || '检索结果'
}

async function loadGraph() {
	mapError.value = ''
	try {
		const catalog = await request({ url: '/api/heritage/catalog' })
		graph.value = buildHeritageGraph(catalog)
	} catch (e) {
		mapError.value = (e && e.message) || '关系图谱暂时不可用'
	}
}

function toggleMap() {
	mapExpanded.value = !mapExpanded.value
}

function openSource(source) {
	if (!source || !source.url || !/^https:\/\//.test(source.url)) return
	// #ifdef H5
	window.open(source.url, '_blank', 'noopener,noreferrer')
	// #endif
	// #ifndef H5
	uni.setClipboardData({
		data: source.url,
		success: () => uni.showToast({ title: '来源链接已复制，请在浏览器打开', icon: 'none' }),
	})
	// #endif
}

onMounted(loadGraph)
```

5. `handleAsk` 成功时保留新字段：

```js
messages.value.push({
	role: 'ai',
	content: data.answer || '抱歉，我暂时无法回答这个问题。',
	sources: data.sources || [],
	retrievalMode: data.retrievalMode || 'NONE',
	generated: data.generated === true,
	showSources: false,
})
```

6. 在 `rag-ask.vue` 的 `<style scoped>` 末尾追加：

```css
.map-card {
	margin: 12rpx 24rpx 0;
	overflow: hidden;
	border: 1rpx solid var(--zy-border, #dfe7dc);
	border-radius: var(--zy-radius-md, 16rpx);
	background: var(--zy-card-bg, #ffffff);
}
.map-toggle {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16rpx;
	width: 100%;
	padding: 18rpx 20rpx;
	text-align: left;
	background: transparent;
	border: 0;
}
.map-title { display: block; font-size: 25rpx; font-weight: 600; color: var(--zy-title, #233d34); }
.map-desc { display: block; margin-top: 4rpx; font-size: 21rpx; color: var(--zy-aid, #718478); }
.map-action { flex-shrink: 0; font-size: 22rpx; color: var(--zy-primary, #2f7566); }
.map-panel { height: 52vh; padding: 0 16rpx 16rpx; }
.map-error { display: flex; flex-direction: column; align-items: flex-start; gap: 12rpx; padding: 20rpx; font-size: 23rpx; color: #9b4b4b; }
.map-retry { padding: 8rpx 18rpx; font-size: 22rpx; color: var(--zy-primary, #2f7566); background: var(--zy-bg-tint-mint, #e8efe4); border: 0; border-radius: 999rpx; }
.answer-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx; margin-top: 12rpx; }
.mode-chip { padding: 2rpx 10rpx; border-radius: 8rpx; background: var(--zy-bg-tint-mint, #e8efe4); color: var(--zy-primary, #2f7566); font-size: 20rpx; }
.mode-note { font-size: 20rpx; color: var(--zy-aid, #718478); }
.source-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.source-badge { flex-shrink: 0; padding: 2rpx 8rpx; border-radius: 6rpx; background: var(--zy-bg, #f5f7f2); color: var(--zy-aid, #718478); font-size: 19rpx; }
.source-org, .source-relation, .source-version, .source-media { display: block; margin-top: 4rpx; font-size: 20rpx; line-height: 1.6; color: var(--zy-aid, #718478); word-break: break-word; }
.source-link { display: inline-block; margin-top: 8rpx; padding: 0; font-size: 21rpx; color: var(--zy-primary, #2f7566); background: transparent; border: 0; text-align: left; }
```

- [ ] **Step 6: 运行前端测试与构建**

Run:
```bash
cd zhiyu-app
node --test tests/heritage-graph-layout.test.mjs tests/heritage-ui.test.mjs
npm run build:h5
npm run build:mp-weixin
```
Expected: 前端测试通过，H5 与 mp-weixin 构建完成。

- [ ] **Step 7: 浏览器验收**

重启后端（先停止本任务启动的 8080 进程，避免覆盖运行中 class），登录 `student01`，打开 `/pages/ai/rag-ask`：

- 询问“瑞昌竹编有什么特点” → 显示 `结构化关系检索`，回答带“未调用生成模型”，来源卡含已登记来源与 https 链接。
- 展开关系图 → Canvas 出现 4 个工艺节点与 4 个来源节点；下方等价列表显示 4 门工艺、24 个纹样分组。
- 375/768/1280 无横向溢出；页面与 console 无新增错误（Canvas willReadFrequently 警告可忽略）。
- 截图保存为 `output/playwright/g1-rag-structured-375.png`、`g1-rag-structured-1280.png`、`g1-rag-map-375.png`。

- [ ] **Step 8: 最终检查与提交检查点**

Run: `git status --short && git diff --stat`
Expected: 等待用户确认后再 commit；不 push，不提交截图/.ai-tmp。

---

## Self-Review

- **Spec coverage**：第 7.1 节的“先组织关系再索引”由 Task 1 的 catalog 图索引实现；“有界关系扩展”由 1 跳、最多 6 条实现；“来源元数据/版本”由 Task 2/3 的 `RagSourceVO` 实现；“下架后过滤”由 Task 2 的 activeSourceIds 实现；“指纹快照”由 `rag-store.json.version` 实现；“向量不可用展示结构化结果”由 Task 3 的 `STRUCTURED_GRAPH` 实现；“学生关系图 Canvas + 等价列表”由 Task 4 实现；“二十条评测问题”不在本次代码任务内，后续在 `features/24-heritage-graph-rag.feature` 基础上建立独立评测集。
- **Placeholder scan**：无 TBD / TODO / “类似上文”；每个代码步骤给出可编译片段或完整替换。
- **Type consistency**：`GraphHit.nodeId/nodeType/title/excerpt/url/organization/mediaReuse/sourceIds/relationPath/catalogVersion` 在 Task 1/3/4 一致；`RagService.VectorHit.id/title/excerpt/sourceId/corpusVersion` 在 Task 2/3 一致；`RagSourceVO` 九字段与 Task 4 模板一致。
