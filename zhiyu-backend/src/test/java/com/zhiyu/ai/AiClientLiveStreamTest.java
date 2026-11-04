package com.zhiyu.ai;

import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiClient#streamChatLive} 单测（SSE A 方案）。
 *
 * <p>覆盖：正常句级流、终检撤回、句级高危中断、输入侧拦截、首句时间。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiClientLiveStreamTest {

    @Mock
    LlmClient llmClient;

    @Mock
    CircuitBreakerStore breaker;

    @Mock
    ContentSafetyFilter contentSafetyFilter;

    AiClient aiClient;

    @BeforeEach
    void setUp() {
        aiClient = new AiClient(llmClient, breaker, contentSafetyFilter);
        UserContext.set(new LoginUser(1001L, 0));
        when(breaker.allowRequest("llm-chat")).thenReturn(true);
        when(contentSafetyFilter.checkInput(any(), any(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.pass("通过"));
        when(contentSafetyFilter.scanStreamSentence(any(), any(), anyString()))
                .thenReturn(SafetyResult.pass("通过"));
        when(contentSafetyFilter.checkOutput(any(), anyString(), anyString()))
                .thenReturn(SafetyResult.pass("通过"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void streamOf(String reply) {
        doAnswer(inv -> {
            Consumer<String> onToken = inv.getArgument(1);
            for (char ch : reply.toCharArray()) {
                onToken.accept(String.valueOf(ch));
            }
            return null;
        }).when(llmClient).streamChat(any(), any());
    }

    @Test
    @DisplayName("正常流：按句推送 chunk，revoked=false，首句 <500ms")
    void testNormalFlow() {
        streamOf("我在这里陪着你。慢慢来，不用急。每一步都值得被看见。");
        List<String> chunks = new ArrayList<>();
        long t0 = System.nanoTime();
        final long[] firstChunkAt = new long[1];

        AiClient.LiveChatResult result = aiClient.streamChatLive(1L, "最近压力大", List.of(),
                s -> {
                    if (chunks.isEmpty()) {
                        firstChunkAt[0] = System.nanoTime();
                    }
                    chunks.add(s);
                });

        assertFalse(result.revoked());
        assertFalse(result.crisis());
        assertEquals(3, chunks.size());
        assertEquals("我在这里陪着你。", chunks.get(0));
        assertEquals("慢慢来，不用急。", chunks.get(1));
        assertEquals("每一步都值得被看见。", chunks.get(2));
        long firstChunkMs = (firstChunkAt[0] - t0) / 1_000_000;
        assertTrue(firstChunkMs < 500, "首句时间应 <500ms，实测 " + firstChunkMs + "ms");
        verify(breaker).recordSuccess("llm-chat");
        verify(contentSafetyFilter, times(1)).checkOutput(any(), eq("chat"), anyString());
    }

    @Test
    @DisplayName("终检命中 REWRITE：revoked=true，回复被替换")
    void testFinalRewriteRevoke() {
        streamOf("先陪你坐一会儿。我们再看看别的。");
        when(contentSafetyFilter.checkOutput(any(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.rewrite("越界", "我在这里陪着你。本系统不能替代专业心理咨询。"));

        List<String> chunks = new ArrayList<>();
        AiClient.LiveChatResult result = aiClient.streamChatLive(1L, "聊聊", List.of(), chunks::add);

        assertTrue(result.revoked());
        assertEquals("我在这里陪着你。本系统不能替代专业心理咨询。", result.reply());
        // 已流出的句子由前端按 revoke 整体替换
        assertEquals(2, chunks.size());
        verify(contentSafetyFilter).checkOutput(any(), eq("chat"), anyString());
    }

    @Test
    @DisplayName("句级高危：立即中断 LLM 流，crisis=true，关怀话术替换，后续句不再下发")
    void testSentenceHighRiskInterrupt() {
        // 三句回复，第二句句级命中高危
        when(contentSafetyFilter.scanStreamSentence(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String s = inv.getArgument(2);
                    if (s.contains("危险句")) {
                        return SafetyResult.block("句级关键词命中", "你并不孤单，可以拨打心理援助热线。");
                    }
                    return SafetyResult.pass("通过");
                });
        streamOf("第一句先陪着你。危险句出现。第三句不该到达。");

        List<String> chunks = new ArrayList<>();
        AiClient.LiveChatResult result = aiClient.streamChatLive(1L, "说说", List.of(), chunks::add);

        assertTrue(result.crisis());
        assertTrue(result.revoked());
        assertEquals("你并不孤单，可以拨打心理援助热线。", result.reply());
        // 中断后第三句不应流出（中断信号在第二句抛出）
        assertFalse(chunks.stream().anyMatch(s -> s.contains("第三句")), "中断后的句子不得流出");
        // 终检审计 scene 标记 chat-revoked
        verify(contentSafetyFilter).checkOutput(any(), eq("chat-revoked"), anyString());
    }

    @Test
    @DisplayName("输入侧高危：不调 LLM，blocked=true，crisis=true")
    void testInputBlocked() {
        when(contentSafetyFilter.checkInput(any(), any(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.block("关键词命中", "你并不孤单。"));

        AiClient.LiveChatResult result = aiClient.streamChatLive(1L, "高危输入", List.of(), s -> { });

        assertTrue(result.blocked());
        assertTrue(result.crisis());
        assertEquals("你并不孤单。", result.reply());
        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("句级越界中断但终检 PASS：revoked=true（已流出内容不完整）")
    void testBoundaryInterruptStillRevokes() {
        when(contentSafetyFilter.scanStreamSentence(any(), any(), anyString()))
                .thenAnswer(inv -> {
                    String s = inv.getArgument(2);
                    if (s.contains("越界句")) {
                        return SafetyResult.rewrite("句级越界", null);
                    }
                    return SafetyResult.pass("通过");
                });
        // 终检对全文放行（边界情况）
        when(contentSafetyFilter.checkOutput(any(), eq("chat-revoked"), anyString()))
                .thenReturn(SafetyResult.pass("通过"));
        streamOf("先听你说。越界句。后面没了。");

        AiClient.LiveChatResult result = aiClient.streamChatLive(1L, "继续", List.of(), s -> { });

        assertTrue(result.revoked());
        assertFalse(result.crisis());
        // 中断即截流：回复只包含中断前已生成的部分
        assertEquals("先听你说。越界句。", result.reply());
    }
}
