package com.zhiyu.ai;

import com.zhiyu.ai.client.LlmChatResult;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.LlmClientRegistry;
import com.zhiyu.ai.metrics.AiCallMetrics;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.routing.ModelRouter;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本地兜底行为测试（2026-11-08 装配层批，D1/D2/D4/D8）。
 *
 * <p>验证：云不可用（超时/连接/5xx/429/401/403）才兜底；4xx 请求错误不兜底；
 * 兜底不静默（trace 双段、provider 如实）；审核建议（D4）云再烂也不走本地；
 * 影子判定双开关只开一个不走本地。
 */
class AiClientLocalFallbackTest {

    private LlmClient cloud;
    private LlmClient local;
    private CircuitBreakerStore breaker;
    private ContentSafetyFilter filter;
    private AiCallMetrics metrics;

    @BeforeEach
    void setUp() {
        cloud = mock(LlmClient.class);
        local = mock(LlmClient.class);
        breaker = mock(CircuitBreakerStore.class);
        filter = mock(ContentSafetyFilter.class);
        metrics = mock(AiCallMetrics.class);
        lenient().when(metrics.active()).thenReturn(true);
        lenient().when(breaker.allowRequest(anyString())).thenReturn(true);
        lenient().when(breaker.implementationName()).thenReturn("test-store");
        lenient().when(cloud.modelName()).thenReturn("deepseek-chat");
        lenient().when(cloud.providerName()).thenReturn("deepseek");
        lenient().when(local.modelName()).thenReturn("deepseek-r1:8b");
        lenient().when(local.providerName()).thenReturn("ollama");
        lenient().when(filter.checkInput(any(), any(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("ok"));
        lenient().when(filter.checkOutput(any(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("ok"));
        lenient().when(filter.scanStreamSentence(any(), any(), anyString()))
                .thenReturn(SafetyResult.pass("ok"));
    }

    private ModelRouter fallbackRouter() {
        return new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                false, "ollama,mock", "local", "local", "local", "local", "local");
    }

    private AiClient aiClient(LlmClient c, LlmClient l, ModelRouter router) {
        return new AiClient(LlmClientRegistry.of(c, l), breaker, filter, router, metrics);
    }

    @Test
    @DisplayName("推荐：云超时 → 本地兜底成功，trace 双段 provider 如实（D2）")
    void recommendFallsBackToLocalOnTimeout() {
        when(cloud.recommendReasonWithUsage(anyString(), anyList()))
                .thenThrow(new ResourceAccessException("connect timed out"));
        when(local.recommendReasonWithUsage(anyString(), anyList()))
                .thenReturn(new LlmChatResult("本地生成的推荐理由", 9, 5));

        String result = aiClient(cloud, local, fallbackRouter()).recommendReason("焦虑", List.of("扎染"));

        assertEquals("本地生成的推荐理由", result);
        verify(metrics).record(any(), eq("llm-recommend"), eq("deepseek-chat"), eq("deepseek"), anyLong(),
                eq(AiCallMetrics.FALLBACK), eq(AiCallMetrics.PROVIDER_ERROR), isNull(), isNull());
        verify(metrics).record(any(), eq("llm-recommend"), eq("deepseek-r1:8b"), eq("ollama"), anyLong(),
                eq(AiCallMetrics.OK), isNull(), eq(9), eq(5));
        // 云失败如实记熔断；本地成功不回填云熔断计数
        verify(breaker).recordFailure("llm-recommend");
        verify(breaker, never()).recordSuccess("llm-recommend");
    }

    @Test
    @DisplayName("推荐：云 400 请求错误 → 不兜底（D2 请求自身错误打本地也会败）")
    void recommendDoesNotFallbackOnBadRequest() {
        when(cloud.recommendReasonWithUsage(anyString(), anyList()))
                .thenThrow(new RestClientResponseException("bad request", HttpStatus.BAD_REQUEST, "Bad Request",
                        null, null, null));

        String result = aiClient(cloud, local, fallbackRouter()).recommendReason("焦虑", List.of("扎染"));

        assertEquals("（暂时无法生成个性化理由，先看看这些手作吧）", result);
        verify(local, never()).recommendReasonWithUsage(anyString(), anyList());
    }

    @Test
    @DisplayName("推荐：云 401 → 兜底但按 D2 走（ERROR 告警在日志层，行为同兜底）")
    void recommendFallsBackOnAuthError() {
        when(cloud.recommendReasonWithUsage(anyString(), anyList()))
                .thenThrow(new RestClientResponseException("unauthorized", HttpStatus.UNAUTHORIZED, "Unauthorized",
                        null, null, null));
        when(local.recommendReasonWithUsage(anyString(), anyList()))
                .thenReturn(new LlmChatResult("本地兜底文本", 3, 2));

        String result = aiClient(cloud, local, fallbackRouter()).recommendReason("焦虑", List.of("扎染"));

        assertEquals("本地兜底文本", result);
        verify(local).recommendReasonWithUsage(anyString(), anyList());
    }

    @Test
    @DisplayName("推荐：策略关停（env=none）后云失败不兜底")
    void recommendNoFallbackWhenSwitchOff() {
        ModelRouter off = new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                false, "ollama,mock", "none", "none", "none", "none", "none");
        when(cloud.recommendReasonWithUsage(anyString(), anyList()))
                .thenThrow(new ResourceAccessException("connect timed out"));

        String result = aiClient(cloud, local, off).recommendReason("焦虑", List.of("扎染"));

        assertEquals("（暂时无法生成个性化理由，先看看这些手作吧）", result);
        verify(local, never()).recommendReasonWithUsage(anyString(), anyList());
    }

    @Test
    @DisplayName("对话（缓冲流）：云不可用 → 本地整段兜底（零外发前提）")
    void streamChatFallsBackToLocal() {
        doThrow(new ResourceAccessException("connection refused")).when(cloud).streamChat(anyList(), any());
        doAnswer(inv -> {
            java.util.function.Consumer<String> cb = inv.getArgument(1);
            cb.accept("本地");
            cb.accept("兜底回复");
            return null;
        }).when(local).streamChat(anyList(), any());

        StringBuilder served = new StringBuilder();
        var result = aiClient(cloud, local, fallbackRouter())
                .streamChat(1L, "你好", List.of(), served::append);

        assertEquals("本地兜底回复", result.reply());
        verify(metrics).record(any(), eq("llm-chat"), eq("deepseek-chat"), eq("deepseek"), anyLong(),
                eq(AiCallMetrics.FALLBACK), eq(AiCallMetrics.PROVIDER_ERROR), isNull(), isNull());
        verify(metrics).record(any(), eq("llm-chat"), eq("deepseek-r1:8b"), eq("ollama"), anyLong(),
                eq(AiCallMetrics.OK), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("审核建议：云两档均失败 → 返回 null 且本地从未被调用（D4 硬约束）")
    void moderationNeverFallsBackToLocal() {
        when(cloud.chatWithUsageJson(anyString(), any()))
                .thenThrow(new ResourceAccessException("connect timed out"));

        String result = aiClient(cloud, local, fallbackRouter()).moderationSuggest("待审文本");

        assertNull(result);
        verify(local, never()).chatWithUsageJson(anyString(), any());
    }

    @Test
    @DisplayName("影子判定：双开关成立 + 云缺席 → 本地判定归属 ollama（D8）")
    void shadowUsesLocalWhenDemoDoubleSwitchOn() {
        ModelRouter demoRouter = new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                true, "ollama,mock", "none", "none", "none", "none", "none");
        when(local.chatWithUsageJson(anyString(), eq("deepseek-r1:8b")))
                .thenReturn(new LlmChatResult("{\"tendency\":\"UNCERTAIN\"}", 5, 4));

        var verdict = aiClient(null, local, demoRouter).moderationShadowSuggestWithAttribution("待审文本");

        assertEquals("ollama", verdict.provider());
        assertEquals("deepseek-r1:8b", verdict.model());
    }

    @Test
    @DisplayName("影子判定：只开 demo-providers（demo-mode 关）→ 云缺席即不可用，不走本地（D8）")
    void shadowSingleSwitchDoesNotUseLocal() {
        ModelRouter singleSwitch = new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                false, "ollama,mock", "none", "none", "none", "none", "none");

        var verdict = aiClient(null, local, singleSwitch).moderationShadowSuggestWithAttribution("待审文本");

        assertNull(verdict);
        verify(local, never()).chatWithUsageJson(anyString(), any());
    }

    @Test
    @DisplayName("倾听小结（cloud-only）：云缺席 + 本地在 → 仍按未配置真实模型降级（D1）")
    void listeningSummaryDoesNotUseLocal() {
        var result = aiClient(null, local, fallbackRouter()).listeningSummary(1L, "prompt", "用户文本");

        assertNull(result.text());
        verify(local, never()).listeningSummary(anyString());
    }

    @Test
    @DisplayName("本地 provider 危机用例：fallback 全开 + 云在场，高危输入仍零模型调用（防规则层被挪到 provider 之后）")
    void crisisInputBypassesBothProvidersUnderFallbackConfig() {
        // 真实词库过滤器（桩化过滤器钉不住「真词命中 → 零模型调用」的完整链）
        var lexicon = new com.zhiyu.ai.safety.HighRiskLexicon();
        org.springframework.test.util.ReflectionTestUtils.setField(lexicon, "lexiconResource",
                new org.springframework.core.io.ClassPathResource("lexicon/high-risk.txt"));
        lexicon.init();
        var careReminder = mock(com.zhiyu.modules.audit.service.CareReminderService.class);
        var realFilter = new com.zhiyu.ai.safety.KeywordContentSafetyFilter(lexicon, careReminder,
                mock(com.zhiyu.modules.audit.service.AiAuditLogService.class),
                mock(com.zhiyu.modules.crisis.CrisisSessionLogService.class));
        var ai = new AiClient(LlmClientRegistry.of(cloud, local), breaker, realFilter,
                fallbackRouter(), metrics);
        StringBuilder served = new StringBuilder();

        var result = ai.streamChat(1L, "最近压力好大，我想自杀。", List.of(), served::append);

        org.junit.jupiter.api.Assertions.assertTrue(result.blocked(), "高危输入必须被拦截");
        org.junit.jupiter.api.Assertions.assertTrue(result.crisis(), "crisis 标志必须为 true");
        org.junit.jupiter.api.Assertions.assertTrue(
                result.reply().contains("并不孤单") || result.reply().contains("热线"),
                "应下发关怀话术而非模型文本: " + result.reply());
        verify(cloud, never()).streamChat(any(), any());
        verify(local, never()).streamChat(any(), any());
    }
}
