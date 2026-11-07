package com.zhiyu.ai.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力兜底归属契约测试（2026-11-08 装配层批，D1/D4/D8 第三层防线）。
 *
 * <p>钉死归属表：cloud-only 集合（含审核建议）在任何配置组合下都不得允许本地兜底；
 * 五个可兜底能力按各自开关；影子判定双开关只开一个不生效。
 * 意图：以后有人试图给 cloud-only 能力开配置口子，本测试必须先红。
 */
class ProviderRoutingPolicyTest {

    private ModelRouter router(boolean demoMode, String demoProviders,
                               String chat, String recommend, String message, String rag, String stuckGuide) {
        return new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                demoMode, demoProviders, chat, recommend, message, rag, stuckGuide);
    }

    private ModelRouter defaults() {
        return router(false, "ollama,mock", "local", "local", "local", "local", "local");
    }

    @Test
    @DisplayName("审核建议在任何配置组合下都不得允许本地兜底（D4）")
    void moderationNeverFallsBackToLocal() {
        String[][] combos = {
                {"true", "ollama,mock"}, {"true", "mock"}, {"false", "ollama,mock"}, {"false", "ollama"}
        };
        for (String[] c : combos) {
            ModelRouter r = router(Boolean.parseBoolean(c[0]), c[1],
                    "local", "local", "local", "local", "local");
            org.junit.jupiter.api.Assertions.assertFalse(r.fallbackAllowed("moderation"),
                    "moderation 在 demoMode=" + c[0] + ", demoProviders=" + c[1] + " 下必须 cloud-only");
        }
    }

    @Test
    @DisplayName("cloud-only 集合：倾听小结/陪伴编排/滚动摘要/线稿共创一律不兜底（D1）")
    void cloudOnlyTasksNeverFallback() {
        ModelRouter r = router(true, "ollama,mock", "local", "local", "local", "local", "local");
        for (String task : new String[]{"listening_summary", "companion", "summary", "lineart", "moderation"}) {
            assertFalse(r.fallbackAllowed(task), task + " 必须 cloud-only");
        }
    }

    @Test
    @DisplayName("五个可兜底能力默认允许，单独关停键生效")
    void fallbackCapableTasksFollowSwitches() {
        ModelRouter on = defaults();
        for (String task : new String[]{"chat", "recommend", "message", "rag", "stuck_guide"}) {
            assertTrue(on.fallbackAllowed(task), task + " 默认应允许本地兜底");
        }
        ModelRouter off = router(false, "ollama,mock", "none", "none", "none", "none", "none");
        for (String task : new String[]{"chat", "recommend", "message", "rag", "stuck_guide"}) {
            assertFalse(off.fallbackAllowed(task), task + " 置 none 后不得兜底");
        }
    }

    @Test
    @DisplayName("影子判定双开关：只开一个不生效（D8）")
    void shadowLocalRequiresBothSwitches() {
        assertTrue(router(true, "ollama,mock", "none", "none", "none", "none", "none")
                .fallbackAllowed("moderation_shadow"), "双开关同时成立才允许影子本地");
        assertFalse(router(true, "mock", "none", "none", "none", "none", "none")
                .fallbackAllowed("moderation_shadow"), "只开 local-demo-mode 不得生效");
        assertFalse(router(false, "ollama,mock", "none", "none", "none", "none", "none")
                .fallbackAllowed("moderation_shadow"), "只配 demo-providers 不得生效");
        assertFalse(router(false, "mock", "none", "none", "none", "none", "none")
                .fallbackAllowed("moderation_shadow"), "都不开不得生效");
    }

    @Test
    @DisplayName("未知任务名一律不允许兜底（防调用点笔误静默开洞）")
    void unknownTaskNeverFallsBack() {
        assertFalse(defaults().fallbackAllowed("reflection"));
        assertFalse(defaults().fallbackAllowed("chat2"));
        assertFalse(defaults().fallbackAllowed(""));
    }

    @Test
    @DisplayName("本地单档（D7）：localModel 不区分 standard/strong")
    void localModelIsSingleTier() {
        assertTrue(defaults().localModel().equals("deepseek-r1:8b"));
    }
}
