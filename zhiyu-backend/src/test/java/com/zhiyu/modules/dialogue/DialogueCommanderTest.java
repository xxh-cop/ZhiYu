package com.zhiyu.modules.dialogue;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.companion.CompanionTraceService;
import com.zhiyu.modules.dialogue.capability.CompanionCapability;
import com.zhiyu.modules.dialogue.guard.RouteGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调度层 commander 单测（P2，AA 批 2026-11-04）。
 *
 * <p>覆盖：危机零 LLM（mock 计数断言）、规则路由记账、深度上限（每轮 1 路由 + 1 执行）、
 * 重复意图锁定、振荡锁定、会话预算、单轮墙钟超时记账、路由异常回落。
 * commander 本身不生成文本、不持久化、不持有跨请求状态——本测试全部用 mock 能力验证职责边界。
 */
@ExtendWith(MockitoExtension.class)
class DialogueCommanderTest {

    private static final Long USER = 7L;
    private static final Long SESSION = 100L;

    @Mock
    private ContentSafetyFilter safetyFilter;
    @Mock
    private CompanionTraceService trace;
    @Mock
    private AiClient aiClient;
    @Mock
    private CompanionCapability companionCapability;
    @Mock
    private DialogueCapability listening;
    @Mock
    private DialogueCapability knowledge;

    private RouteGuard guard;

    @BeforeEach
    void setUp() {
        // 内存版闸口（与测试 profile 一致；Redis 降级路径由 RouteGuardTest 覆盖）
        guard = new RouteGuard(null, "memory", 3, 600, true);
        lenient().when(listening.id()).thenReturn("LISTENING");
        lenient().when(knowledge.id()).thenReturn("KNOWLEDGE");
        lenient().when(companionCapability.id()).thenReturn("PRACTICE_COMPANION");
        lenient().when(listening.ensureSession(any())).thenAnswer(inv ->
                ((DialogueTurnRequest) inv.getArgument(0)).sessionId());
        lenient().when(knowledge.ensureSession(any())).thenAnswer(inv ->
                ((DialogueTurnRequest) inv.getArgument(0)).sessionId());
        lenient().when(companionCapability.ensureSession(any())).thenReturn(null);
    }

    private DialogueCommander commander(int budget, long wallClockMs) {
        return new DialogueCommander(safetyFilter, guard, trace, aiClient, companionCapability,
                List.of(listening, knowledge, companionCapability), budget, wallClockMs);
    }

    private DialogueTurnRequest req(String text, String entryContext, String intentHint,
                                    Consumer<String> sink) {
        return new DialogueTurnRequest(USER, SESSION, null, text, entryContext, intentHint,
                true, sink, null);
    }

    private void stubPassInput() {
        when(safetyFilter.checkInput(any(), any(), eq("chat"), any()))
                .thenReturn(SafetyResult.pass("输入检查通过"));
    }

    private DialogueTurnResult listeningReply() {
        return new DialogueTurnResult(SESSION, "LISTENING", "回复", false, false, false);
    }

    @Test
    @DisplayName("危机红线：高危输入零 LLM 调用（mock 计数），能力编排完全不启动，crisis=true")
    void crisisInputSkipsLlmEntirely() {
        when(safetyFilter.checkInput(eq(USER), eq(SESSION), eq("chat"), eq("我不想活了")))
                .thenReturn(SafetyResult.block("关键词命中", "请记得你并不孤单，可拨打热线 12356"));
        StringBuilder sink = new StringBuilder();

        DialogueTurnResult r = commander(30, 30_000)
                .chat(req("我不想活了", "LISTENING_PAGE", null, sink::append));

        assertTrue(r.crisis());
        assertTrue(r.revoked());
        assertEquals("CRISIS", r.capability());
        assertTrue(sink.toString().contains("12356"), "关怀话术应直接下发: " + sink);
        // 零 LLM：门面两类流式接口（含预检重载）一次都没被调用——不是只看返回值
        verify(aiClient, never()).streamChat(any(), any(), any(), any());
        verify(aiClient, never()).streamChat(any(), any(), any(), any(), any());
        verify(aiClient, never()).streamChatLive(any(), any(), any(), any());
        verify(aiClient, never()).streamChatLive(any(), any(), any(), any(), any());
        // 编排/能力也完全不启动
        verify(listening, never()).execute(any());
        verify(knowledge, never()).execute(any());
        verify(companionCapability, never()).execute(any());
        verify(trace).record(eq(USER), isNull(), eq("dialogue-route"), nullable(String.class),
                isNull(), eq("CRISIS"), anyInt(), eq("FALLBACK"), eq("ROUTE_CRISIS"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("规则路由：无命中 → 默认 LISTENING，trace 记 rule=R4")
    void defaultRouteToListeningWithTrace() {
        stubPassInput();
        when(listening.execute(any())).thenReturn(listeningReply());

        DialogueTurnResult r = commander(30, 30_000)
                .chat(req("今天有点累", "LISTENING_PAGE", null, s -> { }));

        assertEquals("LISTENING", r.capability());
        verify(listening, times(1)).execute(any());
        verify(trace, times(1)).record(eq(USER), isNull(), eq("dialogue-route"),
                nullable(String.class), isNull(), eq("LISTENING"), anyInt(), eq("OK"),
                eq("rule=R4"), isNull(), isNull());
    }

    @Test
    @DisplayName("深度上限=1：单轮恰好 1 次路由记账 + 1 次能力执行（无二次路由路径）")
    void depthCapSingleRouteAndExecution() {
        stubPassInput();
        when(listening.execute(any())).thenReturn(listeningReply());

        commander(30, 30_000).chat(req("聊聊今天", "LISTENING_PAGE", null, s -> { }));

        verify(listening, times(1)).execute(any());
        verify(trace, times(1)).record(any(), any(), eq("dialogue-route"), any(), any(),
                any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("路由异常：回落 LISTENING 并记 ROUTE_FALLBACK（不静默）")
    void routeExceptionFallsBackToListening() {
        stubPassInput();
        when(companionCapability.sessionActive(any(), any()))
                .thenThrow(new IllegalStateException("模拟路由异常"));
        when(listening.execute(any())).thenReturn(listeningReply());

        DialogueTurnResult r = commander(30, 30_000)
                .chat(req("随便聊聊", "LISTENING_PAGE", null, s -> { }));

        assertEquals("LISTENING", r.capability());
        verify(trace).record(eq(USER), isNull(), eq("dialogue-route"), nullable(String.class),
                isNull(), eq("LISTENING"), anyInt(), eq("FALLBACK"), eq("ROUTE_FALLBACK"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("重复意图：同会话同文本第 3 次锁定，给确定性文案且不再执行能力")
    void repeatIntentLocksOnThirdOccurrence() {
        stubPassInput();
        when(listening.execute(any())).thenReturn(listeningReply());
        DialogueCommander c = commander(30, 30_000);

        c.chat(req("我总想同一件烦心事", "LISTENING_PAGE", null, s -> { }));
        c.chat(req("我总想同一件烦心事", "LISTENING_PAGE", null, s -> { }));
        DialogueTurnResult third = c.chat(req("我总想同一件烦心事", "LISTENING_PAGE", null, s -> { }));

        assertEquals(DialogueCommander.REPEAT_LOCK_REPLY, third.reply());
        verify(listening, times(2)).execute(any());
        verify(trace).record(eq(USER), isNull(), eq("dialogue-route"), nullable(String.class),
                isNull(), eq("LISTENING"), anyInt(), eq("FALLBACK"), eq("REPEAT_INTENT_LOCK"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("振荡锁定：同会话 L→K→L 后本轮计算 K → 强制留 L")
    void oscillationLockForcesListening() {
        stubPassInput();
        when(listening.execute(any())).thenReturn(listeningReply());
        when(knowledge.execute(any()))
                .thenReturn(new DialogueTurnResult(SESSION, "KNOWLEDGE", "知识回复", false, false, false));
        DialogueCommander c = commander(30, 30_000);

        c.chat(req("先开个头", "LISTENING_PAGE", null, s -> { }));     // hist [L]
        c.chat(req("竹编纹样有哪些", "KNOWLEDGE_PAGE", null, s -> { })); // hist [L,K]
        c.chat(req("心情一般般", "LISTENING_PAGE", null, s -> { }));    // hist [L,K,L]
        DialogueTurnResult locked = c.chat(req("竹编的产地", "KNOWLEDGE_PAGE", null, s -> { }));

        assertEquals("LISTENING", locked.capability());
        verify(knowledge, times(1)).execute(any());
        verify(listening, times(3)).execute(any());
        verify(trace).record(eq(USER), isNull(), eq("dialogue-route"), nullable(String.class),
                isNull(), eq("LISTENING"), anyInt(), eq("FALLBACK"),
                eq("rule=oscillation:KNOWLEDGE"), isNull(), isNull());
    }

    @Test
    @DisplayName("会话预算：达上限给确定性收尾文案并记 BUDGET_EXHAUSTED")
    void sessionBudgetExhausted() {
        stubPassInput();
        when(listening.execute(any())).thenReturn(listeningReply());
        DialogueCommander c = commander(2, 30_000);

        c.chat(req("第一轮", "LISTENING_PAGE", null, s -> { }));
        c.chat(req("第二轮", "LISTENING_PAGE", null, s -> { }));
        DialogueTurnResult exhausted = c.chat(req("第三轮", "LISTENING_PAGE", null, s -> { }));

        assertEquals(DialogueCommander.BUDGET_EXHAUSTED_REPLY, exhausted.reply());
        verify(listening, times(2)).execute(any());
        verify(trace).record(eq(USER), isNull(), eq("dialogue-route"), nullable(String.class),
                isNull(), eq("LISTENING"), anyInt(), eq("FALLBACK"), eq("BUDGET_EXHAUSTED"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("单轮墙钟：超预算记 TURN_TIMEOUT（不阻断回包）")
    void wallClockTimeoutIsTraced() {
        stubPassInput();
        when(listening.execute(any())).thenReturn(listeningReply());

        // 墙钟预算 -1ms：任何一轮都必然超过，用于稳定触发超时记账分支
        DialogueTurnResult r = commander(30, -1)
                .chat(req("慢慢说", "LISTENING_PAGE", null, s -> { }));

        assertEquals("回复", r.reply());
        verify(trace).record(eq(USER), isNull(), eq("dialogue-route"), nullable(String.class),
                isNull(), eq("LISTENING"), anyInt(), eq("FALLBACK"), eq("TURN_TIMEOUT"),
                isNull(), isNull());
    }
}
