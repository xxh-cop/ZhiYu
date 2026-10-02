package com.zhiyu.ai.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 装饰器模式熔断器存储（T1.1b）。
 *
 * <p>内部持有 {@link RedisCircuitBreakerStore} 和 {@link InMemoryCircuitBreakerStore}。
 * 每次调用先试 Redis：
 * <ul>
 *   <li>Redis 正常 → 用 Redis 的结果</li>
 *   <li>Redis 抛异常 → 记 WARN（限流日志）+ 转用内存</li>
 *   <li>连续 Redis 失败 N 次后，进入「Redis 冷却期」（30 秒），
 *       期间直接走内存，不再尝试 Redis</li>
 * </ul>
 *
 * <p>标记 {@code @Primary}：当 {@link com.zhiyu.ai.AiClient} 注入
 * {@link CircuitBreakerStore} 时，Spring 优先选本类（而非 Redis / InMemory 之一）。
 *
 * <p>设计目的：让 {@link com.zhiyu.ai.AiClient} 无需感知底层存储——
 * 它只注入一个 {@link CircuitBreakerStore}。
 *
 * <p><b>注意</b>：本类同时引入了 {@code @Primary} 和两个 store 的条件装配，
 * 为了避免 Spring 报 "multiple candidates"，
 * {@code @Primary} 必须在所有 profile 下都生效（不能加 @ConditionalOnProperty）。
 * 当 store=redis 时，RedisStore + InMemoryStore（matchIfMissing）都会被创建，
 * 本类作为 @Primary 装饰器接管。
 * 当 store=memory 时，只有 InMemoryStore 被创建，本类装饰器也能工作（RedisStore 不存在）。
 * 为了处理 RedisStore 可能不存在的情况，用 {@code @Autowired(required = false)}。
 */
@Slf4j
@Primary
@Component
public class FallbackCircuitBreakerStore implements CircuitBreakerStore {

    private static final int REDIS_FAIL_THRESHOLD = 3;
    private static final long REDIS_COOLDOWN_MILLIS = 30_000L;

    private final RedisCircuitBreakerStore redisStore;
    private final InMemoryCircuitBreakerStore memoryStore;

    /** 连续 Redis 失败计数 */
    private final AtomicInteger redisFailCount = new AtomicInteger(0);
    /** Redis 冷却到期时间戳（0 = 未冷却） */
    private final AtomicReference<Long> redisCooldownUntil = new AtomicReference<>(0L);

    /**
     * 构造器。{@code redisStore} 可能为 null（store=memory 时 RedisStore 不被创建）。
     */
    public FallbackCircuitBreakerStore(
            @Autowired(required = false) RedisCircuitBreakerStore redisStore,
            InMemoryCircuitBreakerStore memoryStore) {
        this.redisStore = redisStore;
        this.memoryStore = memoryStore;
        log.info("FallbackCircuitBreakerStore 初始化完成，主 store={}, fallback=memory",
                redisStore != null ? "redis" : "null (memory-only)");
    }

    @Override
    public boolean allowRequest(String key) {
        if (shouldUseRedis()) {
            try {
                boolean result = redisStore.allowRequest(key);
                redisFailCount.set(0);
                return result;
            } catch (Exception e) {
                onRedisFailure("allowRequest", key, e);
            }
        }
        return memoryStore.allowRequest(key);
    }

    @Override
    public void recordSuccess(String key) {
        if (shouldUseRedis()) {
            try {
                redisStore.recordSuccess(key);
                redisFailCount.set(0);
                return;
            } catch (Exception e) {
                onRedisFailure("recordSuccess", key, e);
            }
        }
        memoryStore.recordSuccess(key);
    }

    @Override
    public void recordFailure(String key) {
        if (shouldUseRedis()) {
            try {
                redisStore.recordFailure(key);
                redisFailCount.set(0);
                return;
            } catch (Exception e) {
                onRedisFailure("recordFailure", key, e);
            }
        }
        memoryStore.recordFailure(key);
    }

    @Override
    public BreakerState getState(String key) {
        if (shouldUseRedis()) {
            try {
                BreakerState state = redisStore.getState(key);
                redisFailCount.set(0);
                return state;
            } catch (Exception e) {
                onRedisFailure("getState", key, e);
            }
        }
        return memoryStore.getState(key);
    }

    @Override
    public String implementationName() {
        if (shouldUseRedis() && redisFailCount.get() == 0) {
            return "redis";
        }
        return "in-memory (fallback)";
    }

    /** 是否应该尝试 Redis（redisStore 存在且不在冷却期） */
    private boolean shouldUseRedis() {
        if (redisStore == null) {
            return false;
        }
        long cooldown = redisCooldownUntil.get();
        if (cooldown > 0 && System.currentTimeMillis() < cooldown) {
            return false; // 冷却期
        }
        return true;
    }

    /** Redis 调用失败时的统一处理 */
    private void onRedisFailure(String method, String key, Exception e) {
        int count = redisFailCount.incrementAndGet();
        log.warn("Redis {} 异常，切内存 (连续失败={}): key={}, err={}", method, count, key, e.getMessage());
        if (count >= REDIS_FAIL_THRESHOLD) {
            long until = System.currentTimeMillis() + REDIS_COOLDOWN_MILLIS;
            redisCooldownUntil.set(until);
            log.warn("Redis 连续失败 {} 次，进入冷却期 30 秒", count);
        }
    }
}
