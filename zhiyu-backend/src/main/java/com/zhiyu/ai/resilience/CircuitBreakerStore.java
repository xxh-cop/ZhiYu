package com.zhiyu.ai.resilience;

/**
 * 熔断器状态存储抽象（T1.1b）。
 *
 * <p>上层（{@link com.zhiyu.ai.AiClient}）只关心：
 * <ul>
 *   <li>当前请求允不允许通过</li>
 *   <li>调用完成后记成功 / 失败</li>
 *   <li>当前状态（用于日志/可观测）</li>
 * </ul>
 *
 * <p>不暴露内部计数器细节。实现可以是内存（{@link InMemoryCircuitBreakerStore}）
 * 或 Redis（{@link RedisCircuitBreakerStore}），由 {@link FallbackCircuitBreakerStore}
 * 装饰后注入 {@link com.zhiyu.ai.AiClient}。
 *
 * <p>资源 key 隔离（AGENTS.md §4）：{@code llm-chat} / {@code llm-recommend}
 * / {@code llm-message} / {@code asr} / {@code tts}，不得全局共享一个计数器。
 */
public interface CircuitBreakerStore {

    /** 是否允许请求通过 */
    boolean allowRequest(String key);

    /** 记录一次成功（关闭熔断或半开确认） */
    void recordSuccess(String key);

    /** 记录一次失败（计数 +1；达到阈值则打开熔断） */
    void recordFailure(String key);

    /** 当前状态（主要用于可观测/日志） */
    BreakerState getState(String key);

    /** 存储实现名（用于日志：redis / in-memory） */
    String implementationName();
}
