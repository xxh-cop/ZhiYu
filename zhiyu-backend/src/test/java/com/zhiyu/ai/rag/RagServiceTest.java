package com.zhiyu.ai.rag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @TempDir
    Path temp;

    private Logger ragLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void attachLogs() {
        ragLogger = (Logger) LoggerFactory.getLogger(RagService.class);
        logs = new ListAppender<>();
        logs.start();
        ragLogger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        ragLogger.detachAppender(logs);
    }

    @Test
    void retrieveDropsWithdrawnSourcesAndKeepsVersion() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("竹编以竹篾交织成形，观察经纬关系。",
                        Map.of("source", "bianzhi.md", "sourceId", "bianzhi")),
                new Document("已下架的旧资料不应继续被引用。",
                        Map.of("source", "removed.md", "sourceId", "removed"))));
        RagService service = new RagService(store, validModel(), "test-model", "target/rag-test.json");
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
        RagService service = new RagService(store, validModel(), "test-model", "target/rag-test.json");
        assertTrue(service.retrieve("竹编").isEmpty());
    }

    @Test
    void fingerprintIncludesModelDimensionsAndAllSplitterParameters() {
        List<Document> docs = List.of(new Document("竹编以竹篾交织成形。", Map.of("source", "bianzhi.md")));
        String base = RagService.fingerprint("model-a", 2048, 512, 64, 5, 10000, docs);

        assertNotEquals(base, RagService.fingerprint("model-b", 2048, 512, 64, 5, 10000, docs));
        assertNotEquals(base, RagService.fingerprint("model-a", 1024, 512, 64, 5, 10000, docs));
        assertNotEquals(base, RagService.fingerprint("model-a", 2048, 256, 64, 5, 10000, docs));
        assertNotEquals(base, RagService.fingerprint("model-a", 2048, 512, 32, 5, 10000, docs));
        assertNotEquals(base, RagService.fingerprint("model-a", 2048, 512, 64, 6, 10000, docs));
        assertNotEquals(base, RagService.fingerprint("model-a", 2048, 512, 64, 5, 999, docs));
        assertNotEquals(base, RagService.fingerprint("model-a", 2048, 512, 64, 5, 10000,
                List.of(new Document("不同语料", Map.of("source", "bianzhi.md")))));
    }

    @Test
    void modelChangeRebuildsIndex() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        byte[] firstBytes = Files.readAllBytes(path);
        String firstVersion = Files.readString(versionPath(path));

        logs.list.clear();
        build(path, "model-b");

        String secondVersion = Files.readString(versionPath(path));
        assertNotEquals(firstVersion, secondVersion);
        assertFalse(Arrays.equals(firstBytes, Files.readAllBytes(path)), "重建应重写切片 ID");
        assertTrue(logged("指纹变化"));
    }

    @Test
    void zeroVectorBuildKeepsExistingIndexAndVersion() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        byte[] before = Files.readAllBytes(path);
        String beforeVersion = Files.readString(versionPath(path));

        logs.list.clear();
        FakeEmbeddingModel zero = new FakeEmbeddingModel(8, text -> new float[8]);
        new RagService(store(zero), zero, "model-zero", path.toString()).init();

        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(beforeVersion, Files.readString(versionPath(path)));
        assertTrue(logged("零向量"), logs.list.toString());
        assertTrue(logged("不写文件"), logs.list.toString());
    }

    @Test
    void dimensionMismatchBuildKeepsExistingIndexAndVersion() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        byte[] before = Files.readAllBytes(path);
        String beforeVersion = Files.readString(versionPath(path));

        logs.list.clear();
        FakeEmbeddingModel wrongDimensions = new FakeEmbeddingModel(8,
                text -> new float[]{1f, 2f, 3f, 4f, 5f, 6f});
        new RagService(store(wrongDimensions), wrongDimensions, "model-bad-dim", path.toString()).init();

        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(beforeVersion, Files.readString(versionPath(path)));
        assertTrue(logged("维度不符"), logs.list.toString());
    }

    @Test
    void atomicWriteFailureKeepsExistingIndexAndVersion() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        byte[] before = Files.readAllBytes(path);
        String beforeVersion = Files.readString(versionPath(path));

        logs.list.clear();
        FakeEmbeddingModel model = validModel();
        RagService failing = spy(new RagService(store(model), model, "model-write-fail", path.toString()));
        doThrow(new IOException("disk full")).when(failing).moveAtomically(any(Path.class), any(Path.class));
        failing.init();

        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(beforeVersion, Files.readString(versionPath(path)));
        assertTrue(logged("原子写入失败"), logs.list.toString());
        assertNoTemporaryFiles();
    }

    @Test
    void versionWriteFailureRollsBackStoreAndKeepsVersion() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        byte[] before = Files.readAllBytes(path);
        String beforeVersion = Files.readString(versionPath(path));

        logs.list.clear();
        FakeEmbeddingModel model = validModel();
        RagService failing = spy(new RagService(store(model), model, "model-write-fail", path.toString()));
        doThrow(new IOException("version disk full"))
                .when(failing).writeAtomically(any(Path.class), anyString());
        failing.init();

        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(beforeVersion, Files.readString(versionPath(path)));
        assertTrue(logged("已回滚"), logs.list.toString());
        assertTrue(logged("原子写入失败"), logs.list.toString());
        assertNoTemporaryFiles();
    }

    @Test
    void existingRuntimeStoreContentLoadsWithoutRebuild() throws Exception {
        int checked = 0;
        for (Path source : List.of(Path.of("rag-store.json"), Path.of("../rag-store.json"))) {
            if (!Files.isRegularFile(source)) continue;
            Path path = temp.resolve("existing-" + checked + "-rag-store.json");
            FakeEmbeddingModel model = new FakeEmbeddingModel(2048, text -> vector(2048, 1f));
            new RagService(store(model), model, "model-existing", path.toString()).init();
            Files.copy(source, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            byte[] actual = Files.readAllBytes(path);

            logs.list.clear();
            new RagService(store(model), model, "model-existing", path.toString()).init();

            assertArrayEquals(actual, Files.readAllBytes(path), "载入不应重写 " + source);
            assertTrue(logged("从文件加载完成"), logs.list.toString());
            checked++;
        }
        Assumptions.assumeTrue(checked > 0, "运行期索引文件不存在，跳过");
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(temp)) {
            assertTrue(files.noneMatch(file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".tmp") || name.endsWith(".bak");
            }), "失败后临时/备份文件应被清理");
        }
    }

    @Test
    void validIndexLoadsWithoutRebuild() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        byte[] before = Files.readAllBytes(path);
        String beforeVersion = Files.readString(versionPath(path));

        logs.list.clear();
        build(path, "model-a");

        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(beforeVersion, Files.readString(versionPath(path)));
        assertTrue(logged("从文件加载完成"), logs.list.toString());
    }

    @Test
    void loadRejectsZeroVectorStoreAndRebuilds() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        FakeEmbeddingModel zero = new FakeEmbeddingModel(8, text -> new float[8]);
        SimpleVectorStore poisoned = store(zero);
        poisoned.add(List.of(new Document("零向量切片", Map.of("source", "poison.md", "sourceId", "poison"))));
        poisoned.save(path.toFile());

        logs.list.clear();
        build(path, "model-a");

        assertTrue(logged("校验未通过"), logs.list.toString());
        assertTrue(RagService.inspectStore(path, 8).usable());
    }

    @Test
    void loadRejectsDimensionMismatchStoreAndRebuilds() throws Exception {
        Path path = temp.resolve("rag-store.json");
        build(path, "model-a");
        FakeEmbeddingModel wrongDimensions = new FakeEmbeddingModel(8,
                text -> new float[]{1f, 2f, 3f, 4f, 5f, 6f});
        SimpleVectorStore poisoned = store(wrongDimensions);
        poisoned.add(List.of(new Document("维度不符切片", Map.of("source", "poison.md", "sourceId", "poison"))));
        poisoned.save(path.toFile());

        logs.list.clear();
        build(path, "model-a");

        assertTrue(logged("校验未通过"), logs.list.toString());
        assertTrue(RagService.inspectStore(path, 8).usable());
    }

    @Test
    void existingRuntimeStoreFilesPassInspection() {
        for (Path path : List.of(Path.of("rag-store.json"), Path.of("../rag-store.json"))) {
            Assumptions.assumeTrue(Files.isRegularFile(path), "运行期索引不存在，跳过: " + path.toAbsolutePath());
            RagService.StoreInspection inspection = RagService.inspectStore(path, 2048);
            assertTrue(inspection.usable(), path + " -> " + inspection);
            assertTrue(inspection.total() > 0, path + " 切片数应大于 0");
            assertEquals(2048, inspection.dimension(), path + " 维度应为 2048");
        }
    }

    private RagService build(Path path, String modelName) {
        FakeEmbeddingModel model = validModel();
        RagService service = new RagService(store(model), model, modelName, path.toString());
        service.init();
        return service;
    }

    private static SimpleVectorStore store(EmbeddingModel model) {
        return SimpleVectorStore.builder(model).build();
    }

    private static FakeEmbeddingModel validModel() {
        return new FakeEmbeddingModel(8, text -> vector(8, 1f));
    }

    private static float[] vector(int dimensions, float seed) {
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) vector[i] = seed + i * 0.01f;
        return vector;
    }

    private static Path versionPath(Path storePath) {
        return Path.of(storePath + ".version");
    }

    private boolean logged(String fragment) {
        return logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(fragment));
    }

    private static final class FakeEmbeddingModel implements EmbeddingModel {

        private final int dimensions;
        private final Function<String, float[]> vectors;

        private FakeEmbeddingModel(int dimensions, Function<String, float[]> vectors) {
            this.dimensions = dimensions;
            this.vectors = vectors;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> results = new ArrayList<>();
            List<String> texts = request.getInstructions();
            for (int i = 0; i < texts.size(); i++) {
                results.add(new Embedding(vectors.apply(texts.get(i)), i));
            }
            return new EmbeddingResponse(results, new EmbeddingResponseMetadata());
        }

        @Override
        public float[] embed(Document document) {
            return vectors.apply(document.getText());
        }

        @Override
        public float[] embed(String text) {
            return vectors.apply(text);
        }

        @Override
        public int dimensions() {
            return dimensions;
        }
    }
}
