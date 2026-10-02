package com.zhiyu.ai.flow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 装饰器模式心流统计存储（T1.7）。
 *
 * <p>参考 {@code FallbackCircuitBreakerStore} 的模式：
 * <ul>
 *   <li>Redis 正常 → 用 Redis 的结果</li>
 *   <li>Redis 抛异常 → 记 WARN + 转用内存</li>
 *   <li>连续 Redis 失败 N 次后，进入「Redis 冷却期」（30 秒），期间直接走内存</li>
 * </ul>
 *
 * <p>标记 {@code @Primary}：当 {@link FlowDetectionService} 注入
 * {@link FlowStatStore} 时，Spring 优先选本类。
 */
@Slf4j
@Primary
@Component
public class FallbackFlowStatStore implements FlowStatStore {

    private static final int REDIS_FAIL_THRESHOLD = 3;
    private static final long REDIS_COOLDOWN_MILLIS = 30_000L;

    private final RedisFlowStatStore redisStore;
    private final InMemoryFlowStatStore memoryStore;

    private final AtomicInteger redisFailCount = new AtomicInteger(0);
    private final AtomicReference<Long> redisCooldownUntil = new AtomicReference<>(0L);

    /**
     * 构造器。{@code redisStore} 可能为 null（store=memory 时 RedisStore 不被创建）。
     */
    public FallbackFlowStatStore(
            @Autowired(required = false) RedisFlowStatStore redisStore,
            InMemoryFlowStatStore memoryStore) {
        this.redisStore = redisStore;
        this.memoryStore = memoryStore;
        log.info("FallbackFlowStatStore 初始化完成，主 store={}, fallback=memory",
                redisStore != null ? "redis" : "null (memory-only)");
    }

    @Override
    public StepStat get(Long sessionId) {
        if (shouldUseRedis()) {
            try {
                StepStat result = redisStore.get(sessionId);
                redisFailCount.set(0);
                return result;
            } catch (Exception e) {
                onRedisFailure("get", sessionId, e);
            }
        }
        return memoryStore.get(sessionId);
    }

    @Override
    public void save(Long sessionId, StepStat stat) {
        if (shouldUseRedis()) {
            try {
                redisStore.save(sessionId, stat);
                redisFailCount.set(0);
                return;
            } catch (Exception e) {
                onRedisFailure("save", sessionId, e);
            }
        }
        memoryStore.save(sessionId, stat);
    }

    @Override
    public void clear(Long sessionId) {
        if (shouldUseRedis()) {
            try {
                redisStore.clear(sessionId);
                redisFailCount.set(0);
                return;
            } catch (Exception e) {
                onRedisFailure("clear", sessionId, e);
            }
        }
        memoryStore.clear(sessionId);
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
            return false;
        }
        return true;
    }

    /** Redis 调用失败时的统一处理 */
    private void onRedisFailure(String method, Long sessionId, Exception e) {
        int count = redisFailCount.incrementAndGet();
        log.warn("Redis {} 异常，切内存 (连续失败={}): sessionId={}, err={}", method, count, sessionId, e.getMessage());
        if (count >= REDIS_FAIL_THRESHOLD) {
            long until = System.currentTimeMillis() + REDIS_COOLDOWN_MILLIS;
            redisCooldownUntil.set(until);
            log.warn("Redis 连续失败 {} 次，进入冷却期 30 秒", count);
        }
    }
}
