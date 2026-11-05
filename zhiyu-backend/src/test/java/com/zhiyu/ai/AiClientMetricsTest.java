package com.zhiyu.ai;

import com.zhiyu.ai.client.LlmChatResult;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.metrics.AiCallMetrics;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiClient 用量指标插桩单测（BO 批，2026-11-05）。
 *
 * <p>验证门面在成功 / 熔断 / 异常 / 输入拦截 / 携带 tokens 各路径下
 * 向 {@link AiCallMetrics} 写入正确的资源 key、状态与降级原因。
 */
class AiClientMetricsTest {

    private LlmClient llm;
    private CircuitBreakerStore breaker;
    private ContentSafetyFilter filter;
    private AiCallMetrics metrics;
    private AiClient ai;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        breaker = mock(CircuitBreakerStore.class);
        filter = mock(ContentSafetyFilter.class);
        metrics = mock(AiCallMetrics.class);
        lenient().when(metrics.active()).thenReturn(true);
        lenient().when(llm.modelName()).thenReturn("deepseek-chat");
        lenient().when(breaker.allowRequest(anyString())).thenReturn(true);
        lenient().when(breaker.implementationName()).thenReturn("test-store");
        lenient().when(filter.checkInput(any(), any(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("ok"));
        lenient().when(filter.checkOutput(any(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("ok"));
        // modelRouter 传 null：按 strong 档关闭处理（与本类测试目标无关）
        ai = new AiClient(llm, breaker, filter, null, metrics);
    }

    @Test
    void recommendReasonSuccessRecordsOk() {
        when(llm.recommendReasonWithUsage(anyString(), anyList()))
                .thenReturn(new LlmChatResult("因为你最近有些焦虑，试试扎染", 11, 7));

        ai.recommendReason("焦虑", List.of("扎染", "赣绣"));

        // usage 覆盖（2026-11-05 健壮性批）：拿到 tokens 必须如实透传进 ai_trace
        verify(metrics).record(any(), eq("llm-recommend"), eq("deepseek-chat"), anyLong(),
                eq(AiCallMetrics.OK), isNull(), eq(11), eq(7));
    }

    @Test
    void recommendReasonBreakerOpenRecordsFallback() {
        when(breaker.allowRequest("llm-recommend")).thenReturn(false);

        ai.recommendReason("焦虑", List.of("扎染"));

        verify(metrics).record(any(), eq("llm-recommend"), eq("deepseek-chat"), anyLong(),
                eq(AiCallMetrics.FALLBACK), eq(AiCallMetrics.BREAKER_OPEN), isNull(), isNull());
    }

    @Test
    void recommendReasonProviderErrorRecordsFallback() {
        when(llm.recommendReasonWithUsage(anyString(), anyList())).thenThrow(new RuntimeException("timeout"));

        ai.recommendReason("焦虑", List.of("扎染"));

        verify(metrics).record(any(), eq("llm-recommend"), eq("deepseek-chat"), anyLong(),
                eq(AiCallMetrics.FALLBACK), eq(AiCallMetrics.PROVIDER_ERROR), isNull(), isNull());
    }

    @Test
    void streamChatInputBlockRecordsBlocked() {
        when(filter.checkInput(any(), any(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.block("hit", "关怀话术"));

        ai.streamChat(1L, "高危输入", List.of(), token -> {
        });

        verify(metrics).record(any(), eq("llm-chat"), isNull(), anyLong(),
                eq(AiCallMetrics.BLOCKED), eq(AiCallMetrics.INPUT_BLOCK), isNull(), isNull());
    }

    @Test
    void streamChatProviderErrorRecordsError() {
        org.mockito.Mockito.doThrow(new RuntimeException("llm down"))
                .when(llm).streamChat(anyList(), any());

        assertThrows(RuntimeException.class,
                () -> ai.streamChat(1L, "你好", List.of(), token -> {
                }));

        verify(metrics).record(any(), eq("llm-chat"), eq("deepseek-chat"), anyLong(),
                eq(AiCallMetrics.ERROR), eq(AiCallMetrics.PROVIDER_ERROR), isNull(), isNull());
    }

    @Test
    void streamChatBreakerOpenRecordsError() {
        when(breaker.allowRequest("llm-chat")).thenReturn(false);

        assertThrows(com.zhiyu.common.exception.BusinessException.class,
                () -> ai.streamChat(1L, "你好", List.of(), token -> {
                }));

        verify(metrics).record(any(), eq("llm-chat"), eq("deepseek-chat"), anyLong(),
                eq(AiCallMetrics.ERROR), eq(AiCallMetrics.BREAKER_OPEN), isNull(), isNull());
    }

    @Test
    void companionPlanRecordsTokensWhenAvailable() {
        when(llm.chatWithUsage(anyString())).thenReturn(new LlmChatResult("{\"tools\":[]}", 12, 8));

        ai.companionPlan(9L, "prompt", "用户文本");

        // tokens 可获取时如实透传；获取不到的路径（如 streamChat）留 null
        verify(metrics).record(any(), eq("llm-message"), eq("deepseek-chat"), anyLong(),
                eq(AiCallMetrics.OK), isNull(), eq(12), eq(8));
    }

    @Test
    void noopMetricsKeepsOldConstructorSemantics() {
        // 旧 3 参构造函数：metrics 为 no-op，任何路径都不应抛异常
        AiClient legacy = new AiClient(llm, breaker, filter);
        when(llm.recommendReasonWithUsage(anyString(), anyList()))
                .thenReturn(new LlmChatResult("ok", null, null));
        legacy.recommendReason("焦虑", List.of("扎染"));
        verify(metrics, never()).record(any(), anyString(), any(), anyLong(),
                anyString(), any(), any(), any());
    }
}
