package com.zhiyu.ai;

import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.LlmClientRegistry;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.routing.ModelRouter;
import com.zhiyu.ai.safety.HighRiskLexicon;
import com.zhiyu.ai.safety.KeywordContentSafetyFilter;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.modules.audit.service.AiAuditLogService;
import com.zhiyu.modules.audit.service.CareReminderService;
import com.zhiyu.modules.crisis.CrisisSessionLogService;
import com.zhiyu.modules.dialogue.DialogueCommander;
import com.zhiyu.modules.dialogue.DialogueTurnRequest;
import com.zhiyu.modules.dialogue.DialogueTurnResult;
import com.zhiyu.modules.dialogue.DialogueCapability;
import com.zhiyu.modules.companion.CompanionTraceService;
import com.zhiyu.modules.dialogue.capability.CompanionCapability;
import com.zhiyu.modules.dialogue.guard.RouteGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 危机识别顺序钉子（2026-11-08，「先查后批」补齐）。
 *
 * <p><b>承重结构</b>：危机识别（规则层词库）发生在 provider 选取与任何 LLM 调用之前——
 * 这是当前安全性的唯一承重结构，此前没有任何测试守着它；
 * 一次「顺手重构」把顺序挪走，全量测试照样全绿。本类两条对话路径各钉一条：
 *
 * <ol>
 *   <li>直连 {@code AiClient.streamChatLive}（句级流式，本地兜底配置下）；</li>
 *   <li>调度层 {@code DialogueCommander.chat}（当前默认倾听路径）。</li>
 * </ol>
 *
 * <p>与既有用例的差异：这里用<b>真实</b> {@link KeywordContentSafetyFilter} +
 * 真实 {@link HighRiskLexicon}（classpath 词库原样加载），不是桩化过滤器——
 * 桩化过滤器只能钉「 honors 判定」，钉不住「真词命中 → 零模型调用」的完整链。
 */
class CrisisOrderPinTest {

    private static final String RISKY_INPUT = "最近压力好大，我想自杀。";

    private LlmClient cloud;
    private LlmClient local;
    private ContentSafetyFilter realFilter;
    private CareReminderService careReminder;
    private CircuitBreakerStore breaker;

    @BeforeEach
    void setUp() {
        // 真实词库 + 真实规则过滤器（危机三件套副作用用 mock 承载，不影响判定本身）
        HighRiskLexicon lexicon = new HighRiskLexicon();
        org.springframework.test.util.ReflectionTestUtils.setField(lexicon, "lexiconResource",
                new org.springframework.core.io.ClassPathResource("lexicon/high-risk.txt"));
        org.springframework.test.util.ReflectionTestUtils.setField(lexicon, "benignResource",
                new org.springframework.core.io.ClassPathResource("lexicon/high-risk-benign.txt"));
        lexicon.init();
        careReminder = mock(CareReminderService.class);
        realFilter = new KeywordContentSafetyFilter(lexicon, careReminder,
                mock(AiAuditLogService.class), mock(CrisisSessionLogService.class));

        cloud = mock(LlmClient.class);
        local = mock(LlmClient.class);
        lenient().when(local.providerName()).thenReturn("ollama");
        breaker = mock(CircuitBreakerStore.class);
        lenient().when(breaker.allowRequest(anyString())).thenReturn(true);
        lenient().when(breaker.implementationName()).thenReturn("test-store");
    }

    /** 本地兜底配置（fallback 全开）下的 AiClient。 */
    private AiClient aiClientWithLocalFallback() {
        ModelRouter router = new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                false, "ollama,mock", "local", "local", "local", "local", "local");
        return new AiClient(LlmClientRegistry.of(cloud, local), breaker, realFilter, router,
                com.zhiyu.ai.metrics.AiCallMetrics.noop());
    }

    @Test
    @DisplayName("顺序钉子①直连：streamChatLive 在本地兜底配置下，高危输入零模型调用、产出关怀话术")
    void directPathCrisisBlockedBeforeAnyProvider() {
        StringBuilder served = new StringBuilder();

        var result = aiClientWithLocalFallback().streamChatLive(1L, RISKY_INPUT, List.of(), served::append);

        // 产出是危机关怀话术（含热线），不是任何模型回复
        assertTrue(result.blocked(), "高危输入必须被拦截: " + result.reply());
        assertTrue(result.crisis(), "done 事件 crisis 标志必须为 true");
        assertTrue(result.reply().contains("并不孤单") || result.reply().contains("热线"),
                "应下发关怀话术而非模型文本: " + result.reply());
        assertEquals(0, served.length(), "高危路径不得有任何正文外发");
        // 云与本地两个模型客户端零调用——识别发生在 provider 选取之前
        verify(cloud, never()).streamChat(any(), any());
        verify(local, never()).streamChat(any(), any());
        verify(cloud, never()).chat(anyString());
        verify(local, never()).chat(anyString());
        // 危机流程副作用真实触发（关怀提醒落库；单测线程无 UserContext，userId 为 null 用 nullable 匹配）
        verify(careReminder).trigger(org.mockito.ArgumentMatchers.nullable(Long.class),
                any(), anyInt(), anyString());
    }

    @Test
    @DisplayName("顺序钉子②调度层：DialogueCommander 真实词库命中即熔断，LLM 与能力编排零启动")
    void commanderPathCrisisBlockedBeforeRouting() {
        CompanionTraceService trace = mock(CompanionTraceService.class);
        AiClient aiFacade = mock(AiClient.class);
        CompanionCapability companionCapability = mock(CompanionCapability.class);
        DialogueCapability listening = mock(DialogueCapability.class);
        DialogueCapability knowledge = mock(DialogueCapability.class);
        lenient().when(listening.id()).thenReturn("LISTENING");
        lenient().when(knowledge.id()).thenReturn("KNOWLEDGE");
        lenient().when(companionCapability.id()).thenReturn("PRACTICE_COMPANION");
        RouteGuard guard = new RouteGuard(null, "memory", 3, 600, true);
        DialogueCommander commander = new DialogueCommander(realFilter, guard, trace, aiFacade,
                companionCapability, List.of(listening, knowledge, companionCapability), 30, 30_000);
        StringBuilder sink = new StringBuilder();

        DialogueTurnResult r = commander.chat(new DialogueTurnRequest(
                7L, 100L, null, RISKY_INPUT, "LISTENING_PAGE", null, true, sink::append, null));

        assertTrue(r.crisis());
        assertEquals("CRISIS", r.capability());
        assertTrue(sink.toString().contains("并不孤单") || sink.toString().contains("热线"),
                "关怀话术应直接下发: " + sink);
        verify(aiFacade, never()).streamChat(any(), any(), any(), any());
        verify(aiFacade, never()).streamChatLive(any(), any(), any(), any());
        verify(listening, never()).execute(any());
        verify(knowledge, never()).execute(any());
        verify(companionCapability, never()).execute(any());
        verify(careReminder).trigger(anyLong(), anyLong(), anyInt(), anyString());
    }
}
