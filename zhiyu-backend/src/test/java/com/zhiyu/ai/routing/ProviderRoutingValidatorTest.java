package com.zhiyu.ai.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动期 fail-fast 校验器测试（2026-11-08 装配层批，D4 第二层防线）。
 *
 * <p>真实配置面没有 cloud-only 能力的本地键（第一层），本测试用「被污染的
 * ModelRouter」模拟未来任何途径（新配置键、代码回归）把审核建议解析成本地——
 * 校验器必须在启动期炸掉，而不是放行。
 */
class ProviderRoutingValidatorTest {

    private ModelRouter healthyRouter() {
        return new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                false, "ollama,mock", "local", "local", "local", "local", "local");
    }

    /** 模拟「moderation 被某种途径解析为可本地兜底」的污染路由。 */
    private ModelRouter poisonedRouter() {
        return new ModelRouter("deepseek-chat", "deepseek-reasoner", true, "deepseek-r1:8b",
                false, "ollama,mock", "local", "local", "local", "local", "local") {
            @Override
            public boolean fallbackAllowed(String task) {
                if ("moderation".equals(task)) {
                    return true;
                }
                return super.fallbackAllowed(task);
            }
        };
    }

    @Test
    @DisplayName("健康策略：启动校验通过")
    void healthyPolicyPasses() {
        assertDoesNotThrow(() -> new ProviderRoutingValidator(healthyRouter()).afterSingletonsInstantiated());
    }

    @Test
    @DisplayName("审核建议被指向本地：启动 fail-fast 并点名任务")
    void poisonedModerationFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ProviderRoutingValidator(poisonedRouter()).afterSingletonsInstantiated());
        assertTrue(e.getMessage().contains("moderation"));
        assertTrue(e.getMessage().contains("评审"));
    }

    @Test
    @DisplayName("影子双开关只开一个：不炸启动（按不生效处理）")
    void shadowSingleSwitchDoesNotFailStartup() {
        ModelRouter demoOnly = new ModelRouter("deepseek-chat", "deepseek-reasoner", true,
                "deepseek-r1:8b", true, "mock", "local", "local", "local", "local", "local");
        assertDoesNotThrow(() -> new ProviderRoutingValidator(demoOnly).afterSingletonsInstantiated());
    }
}
