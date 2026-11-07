package com.zhiyu.ai.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LLM 客户端注册表（2026-11-08 装配层批）。
 *
 * <p>云端（{@link SpringAiLlmClient}）、本地（{@link OllamaLlmClient}）、
 * 降级（{@link MockLlmClient}）三个候选按类型区分注入，解决同接口多 Bean 的
 * 注入歧义；{@code AiClient} 经 {@code ModelRouter} 的策略按能力挑选。
 *
 * <p>选取语义：
 * <ul>
 *   <li>{@link #cloudOrNull()} —— 仅真实云端；Mock 不算云（诚实口径，
 *       替代既有 {@code llmClient instanceof MockLlmClient} 判断）；</li>
 *   <li>{@link #localOrNull()} —— 本地 Ollama 兜底实现，未装配为 null；</li>
 *   <li>{@link #primary()} —— cloud → local → mock 的展示用首选
 *       （运营页 modelName/modelMode 口径）。</li>
 * </ul>
 */
@Component
public class LlmClientRegistry {

    private final LlmClient cloud;
    private final LlmClient local;
    private final LlmClient mock;

    @org.springframework.beans.factory.annotation.Autowired
    public LlmClientRegistry(ObjectProvider<SpringAiLlmClient> cloud,
                             ObjectProvider<OllamaLlmClient> local,
                             ObjectProvider<MockLlmClient> mock) {
        this(exactCloud(cloud.getIfAvailable()), local.getIfAvailable(), mock.getIfAvailable());
    }

    /**
     * 注意：{@link OllamaLlmClient} 继承 {@link SpringAiLlmClient}（复用调用逻辑），
     * 按父类型注入会把本地兜底错认成云端——cloud-only 能力（审核建议等，D4）将被本地顶替。
     * 云槽位必须精确排除子类，只认 {@link SpringAiLlmClient} 本体。
     */
    private static SpringAiLlmClient exactCloud(SpringAiLlmClient bean) {
        return bean instanceof OllamaLlmClient ? null : bean;
    }

    private LlmClientRegistry(LlmClient cloud, LlmClient local, LlmClient mock) {
        this.cloud = cloud;
        this.local = local;
        this.mock = mock;
    }

    /** 测试/旧装配便捷入口：单客户端包装（local=null；Mock 入参放 mock 槽，其余视为 cloud）。 */
    public static LlmClientRegistry single(LlmClient client) {
        if (client instanceof MockLlmClient) {
            return new LlmClientRegistry(null, null, client);
        }
        return new LlmClientRegistry(client, null, null);
    }

    /** 双客户端便捷入口（测试兜底场景）：cloud 可为 null（云缺失），local 可为 null。 */
    public static LlmClientRegistry of(LlmClient cloud, LlmClient local) {
        return new LlmClientRegistry(cloud, local, null);
    }

    /** 真实云端客户端；未配置真实云 key 时为 null（Mock 不算云）。 */
    public LlmClient cloudOrNull() {
        return cloud;
    }

    /** 本地 Ollama 客户端；未启用（zhiyu.ai.providers.local.enabled=false）为 null。 */
    public LlmClient localOrNull() {
        return local;
    }

    /** Mock 降级实现；prod 或无 Mock 装配时为 null。 */
    public LlmClient mockOrNull() {
        return mock;
    }

    /** 展示用首选：cloud → local → mock；三者皆缺（不应发生）返回 null。 */
    public LlmClient primary() {
        if (cloud != null) {
            return cloud;
        }
        if (local != null) {
            return local;
        }
        return mock;
    }
}
