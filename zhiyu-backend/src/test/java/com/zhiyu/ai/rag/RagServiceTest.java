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
