package com.zhiyu.ai.routing;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.LlmChatResult;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务分档路由与失败回落链单测（B2，2026-11-04）。
 *
 * <p>断言红线：crisis 永不路由到 LLM；strong 失败显式回落 standard；
 * 两档都失败返回 generated=false（业务层固定模板），全程记熔断不静默。
 */
class ModelRouterTest {

    @Test
    @DisplayName("危机干预不路由到任何 LLM 档位（红线）")
    void crisisNeverRoutesToLlm() {
        ModelRouter router = new ModelRouter("deepseek-chat", "deepseek-reasoner", true);
        var route = router.resolve("crisis");
        assertEquals("none", route.tier());
        assertFalse(route.llmAllowed(), "危机场景禁止 LLM 自主决策");
        assertFalse(route.available());
        assertNull(route.model());
    }

    @Test
    @DisplayName("倾听小结解析到 strong 档，日常对话/推荐/寄语/RAG 走 standard")
    void taskTierMapping() {
        ModelRouter router = new ModelRouter("deepseek-chat", "deepseek-reasoner", true);
        assertEquals("deepseek-reasoner", router.resolve("listening_summary").model());
        assertEquals("strong", router.resolve("moderation").tier());
        for (String task : new String[]{"chat", "companion", "recommend", "message", "rag"}) {
            var route = router.resolve(task);
            assertEquals("standard", route.tier(), task);
            assertEquals("deepseek-chat", route.model(), task);
            assertTrue(route.available(), task);
        }
    }

    @Test
    @DisplayName("strong 档未启用时显式不可用（不假装可用）")
    void strongTierExplicitlyUnavailable() {
        ModelRouter router = new ModelRouter("deepseek-chat", "deepseek-reasoner", false);
        var route = router.resolve("listening_summary");
        assertFalse(route.available(), "未启用档位必须显式不可用");
        assertTrue(route.llmAllowed());
    }

    @Test
    @DisplayName("回落链：strong 失败 → standard 生成成功，熔断按档分别计数")
    void fallbackChainStrongToStandard() {
        LlmClient llm = mock(LlmClient.class);
        CircuitBreakerStore breaker = mock(CircuitBreakerStore.class);
        ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
        when(breaker.allowRequest(anyString())).thenReturn(true);
        when(filter.checkInput(any(), any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        when(filter.checkOutput(any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        // strong 档抛异常，standard 档正常
        when(llm.chatWithUsage(anyString(), eq("deepseek-reasoner")))
                .thenThrow(new RuntimeException("strong tier timeout"));
        when(llm.listeningSummary(anyString())).thenReturn("这段对话里你谈到了压力与手工带来的平静。");

        var router = new ModelRouter("deepseek-chat", "deepseek-reasoner", true);
        var ai = new AiClient(llm, breaker, filter, router);
        var result = ai.listeningSummary(null, "prompt", "用户发言");

        assertTrue(result.generated(), "standard 档接管后应正常生成");
        assertEquals("这段对话里你谈到了压力与手工带来的平静。", result.text());
        verify(breaker, times(1)).recordFailure("llm-strong");
        verify(breaker, times(1)).recordSuccess("llm-message");
    }

    @Test
    @DisplayName("回落链：strong 熔断中 → 直接走 standard，不空等 strong")
    void strongCircuitOpenSkipsToStandard() {
        LlmClient llm = mock(LlmClient.class);
        CircuitBreakerStore breaker = mock(CircuitBreakerStore.class);
        ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
        when(filter.checkInput(any(), any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        when(filter.checkOutput(any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        when(breaker.allowRequest("llm-strong")).thenReturn(false);
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llm.listeningSummary(anyString())).thenReturn("小结文本。");

        var router = new ModelRouter("deepseek-chat", "deepseek-reasoner", true);
        var ai = new AiClient(llm, breaker, filter, router);
        var result = ai.listeningSummary(null, "prompt", "用户发言");

        assertTrue(result.generated());
        verify(llm, never()).chatWithUsage(anyString(), eq("deepseek-reasoner"));
        verify(llm, times(1)).listeningSummary(anyString());
    }

    @Test
    @DisplayName("回落链：两档都失败 → generated=false（固定模板），不抛错不静默")
    void bothTiersFailFallsToTemplate() {
        LlmClient llm = mock(LlmClient.class);
        CircuitBreakerStore breaker = mock(CircuitBreakerStore.class);
        ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
        when(breaker.allowRequest(anyString())).thenReturn(true);
        when(filter.checkInput(any(), any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        when(llm.chatWithUsage(anyString(), anyString())).thenThrow(new RuntimeException("strong down"));
        when(llm.listeningSummary(anyString())).thenThrow(new RuntimeException("standard down"));

        var router = new ModelRouter("deepseek-chat", "deepseek-reasoner", true);
        var ai = new AiClient(llm, breaker, filter, router);
        var result = ai.listeningSummary(null, "prompt", "用户发言");

        assertFalse(result.generated(), "两档都失败必须 generated=false，由业务层用固定模板");
        verify(breaker, times(1)).recordFailure("llm-strong");
        verify(breaker, times(1)).recordFailure("llm-message");
    }

    @Test
    @DisplayName("strong 档未启用：直接走 standard，不调模型覆盖接口")
    void strongDisabledGoesStandard() {
        LlmClient llm = mock(LlmClient.class);
        CircuitBreakerStore breaker = mock(CircuitBreakerStore.class);
        ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
        when(breaker.allowRequest(anyString())).thenReturn(true);
        when(filter.checkInput(any(), any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        when(filter.checkOutput(any(), eq("listening"), anyString())).thenReturn(SafetyResult.pass("ok"));
        when(llm.listeningSummary(anyString())).thenReturn("小结文本。");

        var router = new ModelRouter("deepseek-chat", "deepseek-reasoner", false);
        var ai = new AiClient(llm, breaker, filter, router);
        var result = ai.listeningSummary(null, "prompt", "用户发言");

        assertTrue(result.generated());
        verify(llm, never()).chatWithUsage(anyString(), anyString());
    }
}
