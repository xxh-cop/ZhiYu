package com.zhiyu.ai.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 生产环境（{@code prod} profile）LLM 配置守卫。
 *
 * <p>背景：{@link MockLlmClient} 已用 {@code @Profile("!prod")} 与生产隔离。
 * 当 prod 缺少真实 Key 时，{@link SpringAiLlmClient} 也不会创建；如果没有本守卫，
 * 只会得到一个含糊的 {@code NoSuchBeanDefinitionException}，或者被误以为“功能还没接”。
 *
 * <p>本类只在 prod 且 api-key 为空 / {@code sk-placeholder} 时注册一个
 * “创建即失败”的 {@link LlmClient} Bean，让应用<b>启动失败</b>并直接说明如何配置，
 * 而不是静默降级到 mock（AGENTS.md §4）。
 *
 * <p>条件与 {@link SpringAiLlmClient} 的 {@code @ConditionalOnExpression} 严格互补：
 * 有真实 Key 时本类不生效，由真实客户端接管。
 */
@Configuration
@Profile("prod")
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' == '' or '${spring.ai.openai.api-key:}' == 'sk-placeholder'")
public class ProductionLlmClientConfig {

    /**
     * 生产环境缺少真实 LLM 配置时的 fail-fast 出口。
     *
     * <p>返回类型声明为 {@link LlmClient} 是为了让依赖 {@code LlmClient} 的
     * {@code AiClient} 在启动解析依赖时触发本方法，从而把异常指向真实原因。
     */
    @Bean
    public LlmClient productionLlmClientGuard() {
        throw new IllegalStateException(
                "生产环境（prod）缺少真实 LLM 配置：spring.ai.openai.api-key 为空或仍为占位值 sk-placeholder。"
                        + "请通过环境变量 DEEPSEEK_API_KEY 配置真实 Key"
                        + "（可选 DEEPSEEK_BASE_URL / DEEPSEEK_CHAT_MODEL）。"
                        + "MockLlmClient 仅在非 prod 环境生效，生产环境禁止静默降级到 mock。");
    }
}
