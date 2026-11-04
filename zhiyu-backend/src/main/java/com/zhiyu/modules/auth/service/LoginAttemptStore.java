package com.zhiyu.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 登录失败计数与锁定存储（2026-11-03 账号安全）。
 *
 * <p>按账号（username）维度计数：连续失败达到阈值后锁定一段时间。
 * 计数与锁定键不区分账号是否真实存在——不存在的账号同样计数、同样锁定，
 * 避免通过锁定行为差异探测账号注册状态。
 *
 * <p>主存储为 Redis（多实例共享、TTL 自动过期）；Redis 异常时降级为进程内存
 * （与 {@code FallbackCircuitBreakerStore} 同款策略：连续失败 3 次进入 30 秒冷却，
 * 冷却期内直接走内存）。内存降级只在单实例下语义完整，多实例部署必须保证 Redis 可用。
 */
@Slf4j
@Component
public class LoginAttemptStore {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";
    private static final int REDIS_FAIL_THRESHOLD = 3;
    private static final long REDIS_COOLDOWN_MILLIS = 30_000L;

    @Nullable
    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final long windowMillis;
    private final long lockMillis;

    /** 内存降级存储：username -> 计数状态 */
    private final ConcurrentMap<String, MemoryEntry> memory = new ConcurrentHashMap<>();
    private final AtomicInteger redisFailCount = new AtomicInteger(0);
    private volatile long redisCooldownUntil = 0L;

    public LoginAttemptStore(@Nullable StringRedisTemplate redis,
                             @Value("${zhiyu.auth.login-attempt.max-failures:5}") int maxFailures,
                             @Value("${zhiyu.auth.login-attempt.window-minutes:15}") long windowMinutes,
                             @Value("${zhiyu.auth.login-attempt.lock-minutes:15}") long lockMinutes,
                             @Value("${zhiyu.auth.login-attempt.store:redis}") String store) {
        // store=memory 时强制走内存（单测/无 Redis 环境），避免把计数写进真实 Redis
        this.redis = "memory".equalsIgnoreCase(store) ? null : redis;
        this.maxFailures = maxFailures;
        this.windowMillis = windowMinutes * 60_000L;
        this.lockMillis = lockMinutes * 60_000L;
    }

    /** 剩余锁定秒数；未锁定返回 0。 */
    public long lockedRemainingSeconds(String username) {
        return redisFirst("lockedRemainingSeconds",
                () -> {
                    Long ttl = redis.getExpire(LOCK_KEY_PREFIX + username, TimeUnit.SECONDS);
                    return ttl == null || ttl < 0 ? 0L : ttl;
                },
                () -> {
                    MemoryEntry entry = memory.get(username);
                    if (entry == null) {
                        return 0L;
                    }
                    synchronized (entry) {
                        long remaining = entry.lockedUntilMillis - System.currentTimeMillis();
                        return remaining <= 0 ? 0L : (remaining + 999) / 1000;
                    }
                });
    }

    /** 记录一次失败并返回当前窗口内的失败次数；达到阈值时写入锁定。 */
    public int recordFailure(String username) {
        return redisFirst("recordFailure",
                () -> {
                    String failKey = FAIL_KEY_PREFIX + username;
                    Long count = redis.opsForValue().increment(failKey);
                    if (count != null && count == 1L) {
                        redis.expire(failKey, windowMillis, TimeUnit.MILLISECONDS);
                    }
                    if (count != null && count >= maxFailures) {
                        redis.opsForValue().set(LOCK_KEY_PREFIX + username, "1",
                                lockMillis, TimeUnit.MILLISECONDS);
                        redis.delete(failKey);
                        log.warn("账号连续登录失败 {} 次，锁定 {} 分钟（键维度，不含密码信息）", count,
                                lockMillis / 60_000L);
                    }
                    return count == null ? 0 : count.intValue();
                },
                () -> {
                    MemoryEntry entry = memory.computeIfAbsent(username, k -> new MemoryEntry());
                    synchronized (entry) {
                        long now = System.currentTimeMillis();
                        if (now - entry.windowStartMillis > windowMillis) {
                            entry.failures = 0;
                            entry.windowStartMillis = now;
                        }
                        entry.failures++;
                        if (entry.failures >= maxFailures) {
                            entry.lockedUntilMillis = now + lockMillis;
                            entry.failures = 0;
                            entry.windowStartMillis = now;
                            log.warn("账号连续登录失败达到阈值，内存降级锁定 {} 分钟", lockMillis / 60_000L);
                        }
                        return entry.failures;
                    }
                });
    }

    /** 登录成功或密码重置后清零计数与锁定。 */
    public void reset(String username) {
        redisFirst("reset",
                () -> {
                    redis.delete(FAIL_KEY_PREFIX + username);
                    redis.delete(LOCK_KEY_PREFIX + username);
                    return null;
                },
                () -> {
                    memory.remove(username);
                    return null;
                });
    }

    /** Redis 优先、异常降内存；连续失败进入冷却期，避免每次登录都等连接超时。 */
    private <T> T redisFirst(String op, Supplier<T> redisCall, Supplier<T> memoryCall) {
        if (redis != null && System.currentTimeMillis() >= redisCooldownUntil) {
            try {
                T result = redisCall.get();
                redisFailCount.set(0);
                return result;
            } catch (Exception e) {
                int count = redisFailCount.incrementAndGet();
                log.warn("登录失败计数 Redis {} 异常，降级内存（连续失败={}）: {}", op, count, e.getMessage());
                if (count >= REDIS_FAIL_THRESHOLD) {
                    redisCooldownUntil = System.currentTimeMillis() + REDIS_COOLDOWN_MILLIS;
                    log.warn("登录失败计数 Redis 连续失败 {} 次，进入 30 秒冷却期", count);
                }
            }
        }
        return memoryCall.get();
    }

    /** 内存降级的计数状态。 */
    private static final class MemoryEntry {
        private int failures;
        private long windowStartMillis = System.currentTimeMillis();
        private long lockedUntilMillis;
    }
}
