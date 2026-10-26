package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @Mock
    ContentSafetyFilter contentSafetyFilter;

    @InjectMocks
    AiClient aiClient;

    @Test
    @DisplayName("推荐理由：LlmClient 正常返回 → 走 recordSuccess")
    void testRecommend_Success() {
        when(breaker.allowRequest("llm-recommend")).thenReturn(true);
        when(llmClient.recommendReason("焦虑", List.of("扎染"))).thenReturn("扎染很适合焦虑时做");
        when(contentSafetyFilter.checkOutput(any(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("通过"));

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

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("streamChat：输入/输出各过滤一次，带 userId 审计，使用独立熔断资源")
    void testStreamChat_PassThrough() {
        UserContext.set(new LoginUser(42L, 0));
        StringBuilder tokens = new StringBuilder();
        List<ChatMessage> history = List.of(ChatMessage.user("hi"));
        when(contentSafetyFilter.checkInput(42L, 100L, "chat", "hi"))
                .thenReturn(SafetyResult.pass("通过"));
        when(contentSafetyFilter.checkOutput(42L, "chat", "你好"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-chat")).thenReturn(true);
        doAnswer(inv -> {
            Consumer<String> consumer = inv.getArgument(1);
            consumer.accept("你");
            consumer.accept("好");
            return null;
        }).when(llmClient).streamChat(eq(history), any());

        AiClient.StreamChatResult result =
                aiClient.streamChat(100L, "hi", history, tokens::append);

        assertFalse(result.blocked());
        assertFalse(result.crisis());
        assertEquals("你好", result.reply());
        assertEquals("你好", tokens.toString());
        verify(contentSafetyFilter).checkInput(42L, 100L, "chat", "hi");
        verify(contentSafetyFilter).checkOutput(42L, "chat", "你好");
        verify(breaker).recordSuccess("llm-chat");
        verify(breaker, never()).recordFailure(any());
    }

    @Test
    @DisplayName("streamChat：输入命中 BLOCK → 不调 LLM、不回调 token、返回关怀话术")
    void testStreamChat_InputBlocked_NoLlmCall() {
        UserContext.set(new LoginUser(42L, 0));
        List<ChatMessage> history = List.of(ChatMessage.user("我不想活了"));
        when(contentSafetyFilter.checkInput(42L, 7L, "chat", "我不想活了"))
                .thenReturn(SafetyResult.block("关键词命中", "请拨打心理援助热线"));

        StringBuilder tokens = new StringBuilder();
        AiClient.StreamChatResult result =
                aiClient.streamChat(7L, "我不想活了", history, tokens::append);

        assertTrue(result.blocked());
        assertTrue(result.crisis(), "危机标志应保留");
        assertEquals("请拨打心理援助热线", result.reply());
        assertEquals("", tokens.toString(), "BLOCK 时不应推送任何 token");
        verifyNoInteractions(llmClient);
        verify(breaker, never()).allowRequest(anyString());
        verify(breaker, never()).recordSuccess(any());
        verify(breaker, never()).recordFailure(any());
        verify(contentSafetyFilter, never()).checkOutput(any(), any(), any());
    }

    @Test
    @DisplayName("streamChat：输出越界 → 整段改写为安全文本")
    void testStreamChat_OutputRewrite() {
        UserContext.set(new LoginUser(42L, 0));
        List<ChatMessage> history = List.of(ChatMessage.user("最近怎么样"));
        when(contentSafetyFilter.checkInput(42L, 8L, "chat", "最近怎么样"))
                .thenReturn(SafetyResult.pass("通过"));
        when(contentSafetyFilter.checkOutput(42L, "chat", "你可能患有抑郁症"))
                .thenReturn(SafetyResult.rewrite("输出越界", "我在这里陪你"));
        when(breaker.allowRequest("llm-chat")).thenReturn(true);
        doAnswer(inv -> {
            Consumer<String> consumer = inv.getArgument(1);
            consumer.accept("你可能患有抑郁症");
            return null;
        }).when(llmClient).streamChat(eq(history), any());

        AiClient.StreamChatResult result =
                aiClient.streamChat(8L, "最近怎么样", history, ignored -> {});

        assertFalse(result.blocked());
        assertEquals("我在这里陪你", result.reply());
    }

    @Test
    @DisplayName("streamChat：熔断打开 → 4001，不调 LLM")
    void testStreamChat_BreakerOpen() {
        UserContext.set(new LoginUser(42L, 0));
        List<ChatMessage> history = List.of(ChatMessage.user("hi"));
        when(contentSafetyFilter.checkInput(42L, 9L, "chat", "hi"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-chat")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiClient.streamChat(9L, "hi", history, ignored -> {}));

        assertEquals(4001, ex.getCode().intValue());
        verifyNoInteractions(llmClient);
        verify(breaker, never()).recordFailure(any());
    }
}
