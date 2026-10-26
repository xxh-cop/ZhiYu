package com.zhiyu.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MockLlmClient} 生产隔离测试。
 *
 * <p>非 prod：无真实 Key 时 mock 可用（本地点/CI 不阻塞）。
 * prod：无真实 Key 时不再注册 mock，{@link ProductionLlmClientConfig}
 * 让启动直接失败并提示 {@code DEEPSEEK_API_KEY}；有真实 Key 时 mock 不生效。
 */
class LlmClientProductionIsolationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockLlmClient.class, ProductionLlmClientConfig.class);

    @Test
    @DisplayName("非 prod + sk-placeholder：MockLlmClient 生效（test profile 行为不变）")
    void mockActiveOutsideProdWithPlaceholder() {
        runner.withPropertyValues("spring.ai.openai.api-key=sk-placeholder")
                .run(context -> {
                    assertThat(context).hasSingleBean(MockLlmClient.class);
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context).doesNotHaveBean("productionLlmClientGuard");
                });
    }

    @Test
    @DisplayName("非 prod + 未配置 Key：MockLlmClient 生效（matchIfMissing）")
    void mockActiveOutsideProdWithoutKey() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MockLlmClient.class);
            assertThat(context).doesNotHaveBean("productionLlmClientGuard");
        });
    }

    @Test
    @DisplayName("prod + sk-placeholder：启动失败且提示 DEEPSEEK_API_KEY，不静默降级")
    void prodWithPlaceholderFailsFast() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("spring.ai.openai.api-key=sk-placeholder")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("DEEPSEEK_API_KEY"));
    }

    @Test
    @DisplayName("prod + 空 Key：同样启动失败")
    void prodWithEmptyKeyFailsFast() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("spring.ai.openai.api-key=")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("DEEPSEEK_API_KEY"));
    }

    @Test
    @DisplayName("prod + 真实 Key：MockLlmClient 与守卫都不生效")
    void prodWithRealKeyDoesNotUseMock() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("spring.ai.openai.api-key=sk-real-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MockLlmClient.class);
                    assertThat(context).doesNotHaveBean("productionLlmClientGuard");
                });
    }
}
