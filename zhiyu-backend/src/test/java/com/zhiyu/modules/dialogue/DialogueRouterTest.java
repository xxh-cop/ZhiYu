package com.zhiyu.modules.dialogue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 规则路由器单测（P2）：纯函数，覆盖 R1-R4 与多条件优先级。
 * 规则表见 docs/dialogue-unification-20261104.md §2.3 与 ai-config/ai-capabilities.yaml dialogue 段。
 */
class DialogueRouterTest {

    @Test
    @DisplayName("R1：PRACTICE_PAGE + 活跃练习会话 → PRACTICE_COMPANION")
    void r1PracticePageWithActiveSession() {
        DialogueRouter.Route r = DialogueRouter.route("PRACTICE_PAGE", null, true);
        assertEquals("PRACTICE_COMPANION", r.capability());
        assertEquals("R1", r.ruleId());
    }

    @Test
    @DisplayName("R1 不命中：PRACTICE_PAGE 但练习会话不活跃 → 落 R4 默认倾听")
    void r1SkippedWhenSessionInactive() {
        DialogueRouter.Route r = DialogueRouter.route("PRACTICE_PAGE", null, false);
        assertEquals("LISTENING", r.capability());
        assertEquals("R4", r.ruleId());
    }

    @Test
    @DisplayName("R2：KNOWLEDGE_PAGE → KNOWLEDGE（优先于 R3/R4）")
    void r2KnowledgePage() {
        DialogueRouter.Route r = DialogueRouter.route("KNOWLEDGE_PAGE", null, false);
        assertEquals("KNOWLEDGE", r.capability());
        assertEquals("R2", r.ruleId());
    }

    @Test
    @DisplayName("R2 优先于 R1：KNOWLEDGE_PAGE 即使有活跃练习会话也走 KNOWLEDGE")
    void r2TakesPrecedenceOverR1() {
        // entryContext=KNOWLEDGE_PAGE 时 R1 前提（PRACTICE_PAGE）不成立
        DialogueRouter.Route r = DialogueRouter.route("KNOWLEDGE_PAGE", null, true);
        assertEquals("KNOWLEDGE", r.capability());
        assertEquals("R2", r.ruleId());
    }

    @Test
    @DisplayName("R3：intentHint=knowledge* → KNOWLEDGE")
    void r3KnowledgeHint() {
        DialogueRouter.Route r = DialogueRouter.route("LISTENING_PAGE", "knowledge_history", false);
        assertEquals("KNOWLEDGE", r.capability());
        assertEquals("R3", r.ruleId());
    }

    @Test
    @DisplayName("R3：intentHint=ask_heritage → KNOWLEDGE")
    void r3AskHeritageHint() {
        DialogueRouter.Route r = DialogueRouter.route("LISTENING_PAGE", "ask_heritage", false);
        assertEquals("KNOWLEDGE", r.capability());
        assertEquals("R3", r.ruleId());
    }

    @Test
    @DisplayName("R3：其他显式意图 + 活跃练习会话 → PRACTICE_COMPANION")
    void r3OtherHintWithActivePractice() {
        DialogueRouter.Route r = DialogueRouter.route("LISTENING_PAGE", "recommend_craft", true);
        assertEquals("PRACTICE_COMPANION", r.capability());
        assertEquals("R3", r.ruleId());
    }

    @Test
    @DisplayName("R3：其他显式意图且无活跃练习会话 → LISTENING（注记回落）")
    void r3OtherHintFallsBackToListening() {
        DialogueRouter.Route r = DialogueRouter.route("LISTENING_PAGE", "breathing", false);
        assertEquals("LISTENING", r.capability());
        assertEquals("R3", r.ruleId());
    }

    @Test
    @DisplayName("R4：无命中走默认倾听（最安全默认）")
    void r4DefaultListening() {
        DialogueRouter.Route r = DialogueRouter.route("LISTENING_PAGE", null, false);
        assertEquals("LISTENING", r.capability());
        assertEquals("R4", r.ruleId());
    }

    @Test
    @DisplayName("R4：entryContext 为空/未知值同样落默认倾听")
    void r4UnknownContext() {
        assertEquals("LISTENING", DialogueRouter.route(null, null, false).capability());
        assertEquals("LISTENING", DialogueRouter.route("SOMEWHERE_ELSE", " ", false).capability());
    }
}
