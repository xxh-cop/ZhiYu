package com.zhiyu.ai.flow;

/**
 * 心流统计存储接口（T1.7）。
 *
 * <p>参考 {@code CircuitBreakerStore} 的模式：内存实现 + Redis 实现 + Fallback 装饰器。
 * 生产环境用 Redis（多实例共享），测试 / 单机用内存。
 */
public interface FlowStatStore {

    /**
     * 获取会话的心流统计。不存在时返回 {@code null}（由调用方初始化）。
     *
     * @param sessionId 会话 ID
     * @return StepStat 或 null
     */
    StepStat get(Long sessionId);

    /**
     * 保存会话的心流统计。
     *
     * @param sessionId 会话 ID
     * @param stat      统计状态
     */
    void save(Long sessionId, StepStat stat);

    /**
     * 清除会话的心流统计（会话结束时调用）。
     *
     * @param sessionId 会话 ID
     */
    void clear(Long sessionId);

    /**
     * 返回实现名称（用于日志 / 调试）。
     */
    String implementationName();
}
