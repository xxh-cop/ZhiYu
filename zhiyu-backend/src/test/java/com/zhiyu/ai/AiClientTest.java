package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiClient} 单测（T1.1b）。
 *
 * <p>用 Mockito mock {@link LlmClient} 和 {@link CircuitBreakerStore}，
 * 验证熔断 + 降级逻辑，不打真 API。
 */
@ExtendWith(MockitoExtension.class)
class AiClientTest {

    @Mock
    LlmClient llmClient;

    @Mock
    CircuitBreakerStore breaker;

    @InjectMocks
    AiClient aiClient;

    @Test
    @DisplayName("推荐理由：LlmClient 正常返回 → 走 recordSuccess")
    void testRecommend_Success() {
        when(breaker.allowRequest("llm-recommend")).thenReturn(true);
        when(llmClient.recommendReason("焦虑", List.of("扎染"))).thenReturn("扎染很适合焦虑时做");

        String result = aiClient.recommendReason("焦虑", List.of("扎染"));

        assertEquals("扎染很适合焦虑时做", result);
        verify(breaker).recordSuccess("llm-recommend");
        verify(breaker, never()).recordFailure(any());
    }

    @Test
    @DisplayName("推荐理由：LlmClient 抛异常 → 返回降级文案，走 recordFailure")
    void testRecommend_LlmThrows_FallsBack() {
        when(breaker.allowRequest("llm-recommend")).thenReturn(true);
        when(llmClient.recommendReason("焦虑", List.of("扎染")))
                .thenThrow(new RuntimeException("API timeout"));

        String result = aiClient.recommendReason("焦虑", List.of("扎染"));

        assertTrue(result.contains("暂时无法生成"), "应返回降级文案");
        verify(breaker).recordFailure("llm-recommend");
        verify(breaker, never()).recordSuccess(any());
    }

    @Test
    @DisplayName("推荐理由：熔断打开 → 直接返回降级，LlmClient 未被调用")
    void testRecommend_BreakerOpen_NoLlmCall() {
        when(breaker.allowRequest("llm-recommend")).thenReturn(false);

        String result = aiClient.recommendReason("焦虑", List.of("扎染"));

        assertTrue(result.contains("暂时无法生成"), "应返回降级文案");
        verify(llmClient, never()).recommendReason(any(), any());
        verify(breaker, never()).recordSuccess(any());
        verify(breaker, never()).recordFailure(any());
    }

    @Test
    @DisplayName("streamChat：不走熔断，直接透传")
    void testStreamChat_PassThrough() {
        Consumer<String> onToken = s -> {};
        List<ChatMessage> history = List.of(ChatMessage.user("hi"));

        // doNothing — streamChat 返回 void
        doNothing().when(llmClient).streamChat(history, onToken);

        aiClient.streamChat(history, onToken);

        verify(llmClient).streamChat(history, onToken);
        // streamChat 不调 breaker.allowRequest / recordSuccess / recordFailure
        verify(breaker, never()).allowRequest(any());
        verify(breaker, never()).recordSuccess(any());
        verify(breaker, never()).recordFailure(any());
    }
}
