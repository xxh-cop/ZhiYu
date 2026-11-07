package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.client.LlmChatResult;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
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

    AiClient aiClient;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 2026-11-08 装配层批：AiClient 生产构造器已改为 LlmClientRegistry 注入，
        // @InjectMocks 会误选 registry 构造器并传入 null；单测显式走旧 3 参装配（语义不变）
        aiClient = new AiClient(llmClient, breaker, contentSafetyFilter);
    }

    @Test
    @DisplayName("推荐理由：LlmClient 正常返回 → 走 recordSuccess")
    void testRecommend_Success() {
        when(breaker.allowRequest("llm-recommend")).thenReturn(true);
        when(llmClient.recommendReasonWithUsage("焦虑", List.of("扎染")))
                .thenReturn(new LlmChatResult("扎染很适合焦虑时做", null, null));
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
        when(llmClient.recommendReasonWithUsage("焦虑", List.of("扎染")))
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
        verify(llmClient, never()).recommendReasonWithUsage(any(), any());
        verify(breaker, never()).recordSuccess(any());
        verify(breaker, never()).recordFailure(any());
    }

    @Test
    @DisplayName("创作回顾：输入/输出过滤后返回文本，复用 llm-message 熔断")
    void reflectionSummaryPassesSafetyAndUsesMessageBreaker() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, null, "reflection", "用户自填文本"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.reflectionSummaryWithUsage("prompt"))
                .thenReturn(new LlmChatResult("你完成了四个步骤。", null, null));
        when(contentSafetyFilter.checkOutput(42L, "reflection", "你完成了四个步骤。"))
                .thenReturn(SafetyResult.pass("通过"));

        String result = aiClient.reflectionSummary("prompt", "用户自填文本");

        assertEquals("你完成了四个步骤。", result);
        verify(breaker).recordSuccess("llm-message");
        verify(breaker, never()).recordFailure(any());
    }

    @Test
    @DisplayName("创作回顾：输入高危时不调用模型")
    void reflectionSummaryBlockedInputSkipsModel() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, null, "reflection", "高危文本"))
                .thenReturn(SafetyResult.block("命中高危", "请寻求支持"));

        assertNull(aiClient.reflectionSummary("prompt", "高危文本"));
        verify(llmClient, never()).reflectionSummaryWithUsage(anyString());
        verify(breaker, never()).allowRequest(any());
    }

    @Test
    @DisplayName("创作回顾：模型异常时返回 null，由业务使用固定模板")
    void reflectionSummaryFallsBackOnFailure() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, null, "reflection", "文本"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.reflectionSummaryWithUsage("prompt")).thenThrow(new RuntimeException("timeout"));

        assertNull(aiClient.reflectionSummary("prompt", "文本"));
        verify(breaker).recordFailure("llm-message");
        verify(breaker, never()).recordSuccess(any());
    }

    @Test
    @DisplayName("倾听小结：带 sessionId 审计，复用 llm-message 熔断")
    void listeningSummaryPassesSafetyWithSessionId() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 9L, "listening", "用户发言"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.listeningSummary("prompt")).thenReturn("你提到最近有些累。");
        when(contentSafetyFilter.checkOutput(42L, "listening", "你提到最近有些累。"))
                .thenReturn(SafetyResult.pass("通过"));

        var result = aiClient.listeningSummary(9L, "prompt", "用户发言");

        assertTrue(result.generated());
        assertFalse(result.blocked());
        assertEquals("你提到最近有些累。", result.text());
        verify(breaker).recordSuccess("llm-message");
    }

    @Test
    @DisplayName("倾听小结：输入高危时不调用模型并返回 blocked")
    void listeningSummaryBlockedInputSkipsModel() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 9L, "listening", "高危发言"))
                .thenReturn(SafetyResult.block("命中高危", "请寻求支持"));

        var result = aiClient.listeningSummary(9L, "prompt", "高危发言");

        assertTrue(result.blocked());
        assertFalse(result.generated());
        verify(llmClient, never()).listeningSummary(anyString());
        verify(breaker, never()).allowRequest(any());
    }

    @Test
    @DisplayName("倾听小结：模型异常时返回未生成，由业务使用记录引导")
    void listeningSummaryFailureReturnsNotGenerated() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 9L, "listening", "用户发言"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.listeningSummary("prompt")).thenThrow(new RuntimeException("timeout"));

        var result = aiClient.listeningSummary(9L, "prompt", "用户发言");

        assertFalse(result.generated());
        assertFalse(result.blocked());
        assertNull(result.text());
        verify(breaker).recordFailure("llm-message");
    }

    @Test
    @DisplayName("倾听小结：Mock 实现不被标记为已生成")
    void mockListeningSummaryIsNotMarkedGenerated() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 9L, "listening", "用户发言"))
                .thenReturn(SafetyResult.pass("通过"));
        var mockAi = new AiClient(mock(MockLlmClient.class), breaker, contentSafetyFilter);

        var result = mockAi.listeningSummary(9L, "prompt", "用户发言");

        assertFalse(result.generated());
        assertFalse(result.blocked());
        assertNull(result.text());
        verify(breaker, never()).allowRequest(any());
    }

    @Test
    @DisplayName("陪伴计划：安全过滤后返回严格 JSON，复用 llm-message 熔断")
    void companionPlanPassesSafetyAndUsesMessageBreaker() {
        UserContext.set(new LoginUser(42L, 0));
        String raw = "{\"reply\":\"我在。\",\"stage\":\"LISTEN\",\"tool\":null,\"toolInput\":{}}";
        when(contentSafetyFilter.checkInput(42L, 12L, "companion", "有点累"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.chatWithUsage("prompt")).thenReturn(new LlmChatResult(raw, 12, 8));
        when(contentSafetyFilter.checkOutput(42L, "companion", raw))
                .thenReturn(SafetyResult.pass("通过"));

        var result = aiClient.companionPlan(12L, "prompt", "有点累");

        assertTrue(result.generated());
        assertFalse(result.blocked());
        assertEquals(raw, result.text());
        assertNull(result.rewrittenReply());
        assertEquals(12, result.inputTokens());
        assertEquals(8, result.outputTokens());
        verify(breaker).recordSuccess("llm-message");
    }

    @Test
    @DisplayName("陪伴计划：输入高危时不调用模型并返回关怀话术")
    void companionPlanBlockedInputSkipsModel() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 12L, "companion", "高危发言"))
                .thenReturn(SafetyResult.block("命中高危", "请联系学校心理中心"));

        var result = aiClient.companionPlan(12L, "prompt", "高危发言");

        assertTrue(result.blocked());
        assertFalse(result.generated());
        assertEquals("请联系学校心理中心", result.text());
        assertNull(result.rewrittenReply());
        verify(llmClient, never()).chatWithUsage(anyString());
        verify(breaker, never()).allowRequest(any());
    }

    @Test
    @DisplayName("陪伴计划：Mock 实现不被标记为已生成")
    void mockCompanionPlanIsNotMarkedGenerated() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 12L, "companion", "有点累"))
                .thenReturn(SafetyResult.pass("通过"));
        var mockAi = new AiClient(mock(MockLlmClient.class), breaker, contentSafetyFilter);

        var result = mockAi.companionPlan(12L, "prompt", "有点累");

        assertFalse(result.generated());
        assertFalse(result.blocked());
        assertNull(result.text());
        verify(breaker, never()).allowRequest(any());
    }

    @Test
    @DisplayName("陪伴计划：模型异常时返回未生成，由编排层使用确定性建议")
    void companionPlanFailureReturnsNotGenerated() {
        UserContext.set(new LoginUser(42L, 0));
        when(contentSafetyFilter.checkInput(42L, 12L, "companion", "有点累"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.chatWithUsage("prompt")).thenThrow(new RuntimeException("timeout"));

        var result = aiClient.companionPlan(12L, "prompt", "有点累");

        assertFalse(result.generated());
        assertFalse(result.blocked());
        assertNull(result.text());
        verify(breaker).recordFailure("llm-message");
    }

    @Test
    @DisplayName("陪伴计划：输出越界时使用改写文本")
    void companionPlanUsesRewrittenOutput() {
        UserContext.set(new LoginUser(42L, 0));
        String raw = "{\"reply\":\"你患有抑郁症\",\"stage\":\"LISTEN\",\"tool\":null,\"toolInput\":{}}";
        when(contentSafetyFilter.checkInput(42L, 12L, "companion", "有点累"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.chatWithUsage("prompt")).thenReturn(new LlmChatResult(raw, 10, 4));
        when(contentSafetyFilter.checkOutput(42L, "companion", raw))
                .thenReturn(SafetyResult.rewrite("诊断性结论", "我在，我们先慢慢说。"));

        var result = aiClient.companionPlan(12L, "prompt", "有点累");

        assertTrue(result.generated());
        assertFalse(result.blocked());
        assertEquals(raw, result.text());
        assertEquals("我在，我们先慢慢说。", result.rewrittenReply());
        verify(breaker).recordSuccess("llm-message");
    }

    @Test
    @DisplayName("陪伴计划：输出整体被阻断时返回安全话术并标记 blocked")
    void companionPlanBlockedOutputReturnsSafeReply() {
        UserContext.set(new LoginUser(42L, 0));
        String raw = "{\"reply\":\"危险输出\",\"stage\":\"LISTEN\",\"tool\":null,\"toolInput\":{}}";
        when(contentSafetyFilter.checkInput(42L, 12L, "companion", "有点累"))
                .thenReturn(SafetyResult.pass("通过"));
        when(breaker.allowRequest("llm-message")).thenReturn(true);
        when(llmClient.chatWithUsage("prompt")).thenReturn(new LlmChatResult(raw, 9, 3));
        when(contentSafetyFilter.checkOutput(42L, "companion", raw))
                .thenReturn(SafetyResult.block("输出越界", "安全提示"));

        var result = aiClient.companionPlan(12L, "prompt", "有点累");

        assertTrue(result.blocked());
        assertFalse(result.generated());
        assertNull(result.text());
        assertEquals(AiClient.SAFE_CHAT_REPLY, result.rewrittenReply());
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

        StringBuilder tokens = new StringBuilder();
        AiClient.StreamChatResult result =
                aiClient.streamChat(8L, "最近怎么样", history, tokens::append);

        assertFalse(result.blocked());
        assertEquals("我在这里陪你", result.reply());
        assertEquals("我在这里陪你", tokens.toString(), "输出终检前不得外发原始 token");
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
