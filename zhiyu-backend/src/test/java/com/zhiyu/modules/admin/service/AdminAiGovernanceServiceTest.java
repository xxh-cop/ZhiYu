package com.zhiyu.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
import com.zhiyu.modules.companion.eval.CompanionEvalCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAiGovernanceServiceTest {

    @Test
    void normalizesReasonWithoutLeakingMatchedKeyword() {
        assertEquals("关键词命中", AdminAiGovernanceService.normalizeReason("关键词命中: 自伤"));
        assertEquals("输出越界词命中", AdminAiGovernanceService.normalizeReason("输出越界词命中: (?i)诊断"));
        assertEquals("输入检查通过", AdminAiGovernanceService.normalizeReason("输入检查通过"));
        assertEquals("未标注", AdminAiGovernanceService.normalizeReason(null));
        assertEquals("其他安全事件", AdminAiGovernanceService.normalizeReason("自由文本原因"));
    }

    @Test
    void overviewKeepsAggregatesAndConfigurationOnly() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectors = mock(ObjectProvider.class);
        when(vectors.getIfAvailable()).thenReturn(null);
        LlmClient mockClient = mock(MockLlmClient.class);
        AiClient ai = mock(AiClient.class);
        when(ai.speechAvailable()).thenReturn(false);
        when(ai.breakerStoreName()).thenReturn("memory");
        when(ai.modelName()).thenReturn("deepseek-chat");
        RuntimeSettings settings = mock(RuntimeSettings.class);
        when(settings.values("model")).thenReturn(Map.of("temperature", 0.7, "maxTokens", 800));
        var eval = new CompanionEvalCatalog(new ObjectMapper(),
                new ClassPathResource("eval/companion-eval.json"));
        var service = new AdminAiGovernanceService(db, vectors, mockClient, ai, eval, settings);

        Map<String, Object> result = service.overview(30);
        assertEquals(30, result.get("windowDays"));
        assertEquals(0L, result.get("totalEvents"));
        assertTrue(result.containsKey("actionCounts"));
        assertTrue(result.containsKey("sceneCounts"));
        assertTrue(result.containsKey("reasonCounts"));
        assertTrue(result.containsKey("recentEvents"));
        assertTrue(result.containsKey("trace"));
        assertTrue(result.containsKey("eval"));
        assertTrue(result.containsKey("config"));
        assertFalse(result.toString().contains("userId"));
        assertFalse(result.toString().contains("rawInput"));
        assertEquals("MOCK", ((Map<?, ?>) result.get("config")).get("chatMode"));
        assertEquals("deepseek-chat", ((Map<?, ?>) result.get("config")).get("modelName"));
        assertEquals(20, ((Map<?, ?>) result.get("eval")).get("total"));
        assertTrue(((Map<?, ?>) result.get("trace")).containsKey("toolSuccessRate"));
        assertTrue(((Map<?, ?>) result.get("trace")).containsKey("inputTokens"));
        assertEquals(false, ((Map<?, ?>) result.get("trace")).get("costConfigured"));
    }

    @Test
    void calculatesEstimatedCostWhenRatesAreConfigured() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1_000_000L);
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectors = mock(ObjectProvider.class);
        when(vectors.getIfAvailable()).thenReturn(null);
        LlmClient mockClient = mock(MockLlmClient.class);
        AiClient ai = mock(AiClient.class);
        when(ai.speechAvailable()).thenReturn(false);
        when(ai.breakerStoreName()).thenReturn("memory");
        RuntimeSettings settings = mock(RuntimeSettings.class);
        when(settings.values("model")).thenReturn(Map.of("temperature", 0.7, "maxTokens", 800));
        var eval = new CompanionEvalCatalog(new ObjectMapper(),
                new ClassPathResource("eval/companion-eval.json"));
        var service = new AdminAiGovernanceService(db, vectors, mockClient, ai, eval, settings);
        ReflectionTestUtils.setField(service, "inputCostPerMillion", 1.0);
        ReflectionTestUtils.setField(service, "outputCostPerMillion", 2.0);
        ReflectionTestUtils.setField(service, "costCurrency", "CNY");

        Map<String, Object> result = service.overview(30);
        Map<?, ?> trace = (Map<?, ?>) result.get("trace");

        assertEquals(true, trace.get("costConfigured"));
        assertEquals(3.0, trace.get("estimatedCost"));
        assertEquals("CNY", trace.get("currency"));
    }
}
