package com.zhiyu.ai;

import com.zhiyu.ai.client.LlmChatResult;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
import com.zhiyu.ai.resilience.InMemoryCircuitBreakerStore;
import com.zhiyu.ai.routing.ModelRouter;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.zhiyu.security.UserContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BL（2026-11-05）：{@link AiClient#moderationSuggest} 单测。
 *
 * <p>覆盖：strong 档正常生成、strong 失败回落 standard、熔断中不可用、
 * Mock 实现诚实返回不可用、输出未过安全过滤降级、llm-moderation 与 llm-chat 熔断隔离。
 */
class AiClientModerationTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
    private final InMemoryCircuitBreakerStore breaker = new InMemoryCircuitBreakerStore(5, 60_000);

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private AiClient client(LlmClient llmImpl) {
        lenient().when(filter.checkOutput(any(), anyString(), anyString())).thenReturn(SafetyResult.pass("ok"));
        return new AiClient(llmImpl, breaker, filter,
                new ModelRouter("deepseek-chat", "deepseek-reasoner", true));
    }

    @Test
    @DisplayName("BL：strong 档正常生成（llm-moderation 计数，不走 llm-chat）")
    void strongTierSuccess() {
        when(llm.chatWithUsage(anyString(), eq("deepseek-reasoner")))
                .thenReturn(new LlmChatResult("{\"tendency\":\"NO_OBVIOUS_ISSUE\",\"riskPoints\":[]}", 1, 2));
        String out = client(llm).moderationSuggest("prompt");
        assertNotNull(out);
        assertTrue(out.contains("NO_OBVIOUS_ISSUE"));
        verify(llm, never()).chat(anyString());
    }

    @Test
    @DisplayName("BL：strong 档失败显式回落 standard 档")
    void strongFailsFallsBackToStandard() {
        when(llm.chatWithUsage(anyString(), anyString())).thenThrow(new RuntimeException("reasoner 挂了"));
        when(llm.chat(anyString())).thenReturn("{\"tendency\":\"UNCERTAIN\",\"riskPoints\":[]}");
        String out = client(llm).moderationSuggest("prompt");
        assertNotNull(out);
        assertTrue(out.contains("UNCERTAIN"));
    }

    @Test
    @DisplayName("BL：Mock 实现返回不可用（不冒充真实建议）；熔断中返回不可用")
    void mockAndOpenBreakerDegrade() {
        AiClient mockBacked = new AiClient(mock(MockLlmClient.class), breaker, filter,
                new ModelRouter("a", "b", true));
        assertNull(mockBacked.moderationSuggest("p"), "Mock 实现必须返回不可用（诚实降级）");

        // 真实熔断：llm-moderation 连败 5 次熔断，llm-chat 不受影响（隔离）
        when(llm.chatWithUsage(anyString(), anyString())).thenThrow(new RuntimeException("x"));
        when(llm.chat(anyString())).thenThrow(new RuntimeException("x"));
        AiClient c = client(llm);
        for (int i = 0; i < 5; i++) {
            assertNull(c.moderationSuggest("p"));
        }
        assertFalse(breaker.allowRequest("llm-moderation"), "5 连败后 moderation 应熔断");
        assertTrue(breaker.allowRequest("llm-chat"), "llm-chat 不得被 moderation 连累");
        assertNull(c.moderationSuggest("p"), "熔断中直接返回不可用");
    }

    @Test
    @DisplayName("BL：输出未过安全过滤（REWRITE）→ 降级不可用，不把改写文本当建议")
    void outputRewriteDegrades() {
        when(llm.chatWithUsage(anyString(), anyString()))
                .thenReturn(new LlmChatResult("{\"tendency\":\"HAS_CONCERNS\"}", 1, 2));
        AiClient c = client(llm); // 先注册兜底 PASS 桩
        when(filter.checkOutput(any(), eq("moderation"), anyString()))
                .thenReturn(SafetyResult.rewrite("越界词", "改写")); // 后注册的更精确桩生效
        assertNull(c.moderationSuggest("prompt"));
    }
}
