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
