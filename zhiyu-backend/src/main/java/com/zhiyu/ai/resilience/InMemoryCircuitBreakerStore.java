package com.zhiyu.ai.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存版熔断器存储（T1.1b）。
 *
 * <p>始终作为 {@link FallbackCircuitBreakerStore} 的 fallback 存在。
 * 单机内有效，重启即重置。
 *
 * <p>参数通过 {@code @Value} 注入，单测可通过构造器传小值方便测试。
 *
 * <p>状态转换（AGENTS.md §4）：
 * <pre>
 *   CLOSED  --失败达阈值-->  OPEN
 *   OPEN    --到期-->        HALF_OPEN
 *   HALF_OPEN --成功-->      CLOSED
 *   HALF_OPEN --失败-->      OPEN
 * </pre>
 */
@Slf4j
@Component
public class InMemoryCircuitBreakerStore implements CircuitBreakerStore {

    private final int failureThreshold;
    private final long openDurationMillis;

    private final ConcurrentHashMap<String, Breaker> breakers = new ConcurrentHashMap<>();

    /** 单测专用构造器（传小值） */
    public InMemoryCircuitBreakerStore(int failureThreshold, long openDurationMillis) {
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationMillis;
        log.info("InMemoryCircuitBreakerStore 初始化（threshold={}, openDuration={}ms）", failureThreshold, openDurationMillis);
    }

    /** Spring 注入构造器 */
    @Autowired
    public InMemoryCircuitBreakerStore(
            @Value("${zhiyu.ai.resilience.failure-threshold:5}") int failureThreshold,
            @Value("${zhiyu.ai.resilience.open-duration-seconds:60}") int openDurationSeconds) {
        this(failureThreshold, openDurationSeconds * 1000L);
    }

    @Override
    public boolean allowRequest(String key) {
        Breaker b = breakers.computeIfAbsent(key, k -> new Breaker());
        synchronized (b) {
            long now = System.currentTimeMillis();
            switch (b.state) {
                case CLOSED -> {
                    return true;
                }
                case OPEN -> {
                    if (now < b.openUntil) {
                        return false; // 熔断中
                    }
                    b.state = BreakerState.HALF_OPEN;
                    return true; // 放行一次试探
                }
                case HALF_OPEN -> {
                    return false; // 已有试探在跑，其他请求拒绝
                }
                default -> {
                    return true;
                }
            }
        }
    }

    @Override
    public void recordSuccess(String key) {
        Breaker b = breakers.computeIfAbsent(key, k -> new Breaker());
        synchronized (b) {
            b.state = BreakerState.CLOSED;
            b.failCount.set(0);
        }
    }

    @Override
    public void recordFailure(String key) {
        Breaker b = breakers.computeIfAbsent(key, k -> new Breaker());
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (b.state == BreakerState.HALF_OPEN) {
                // 半开试探失败 → 重新 OPEN
                b.state = BreakerState.OPEN;
                b.openUntil = now + openDurationMillis;
                b.failCount.set(0);
            } else {
                if (b.failCount.incrementAndGet() >= failureThreshold) {
                    b.state = BreakerState.OPEN;
                    b.openUntil = now + openDurationMillis;
                    b.failCount.set(0);
                }
            }
        }
    }

    @Override
    public BreakerState getState(String key) {
        Breaker b = breakers.get(key);
        if (b == null) {
            return BreakerState.CLOSED;
        }
        synchronized (b) {
            // 如果 OPEN 已到期但还没被 allowRequest 切换，对外仍报 OPEN
            return b.state;
        }
    }

    @Override
    public String implementationName() {
        return "in-memory";
    }

    /** 单个资源 key 的熔断状态。 */
    static class Breaker {
        final AtomicInteger failCount = new AtomicInteger(0);
        volatile BreakerState state = BreakerState.CLOSED;
        volatile long openUntil = 0L;
    }
}
