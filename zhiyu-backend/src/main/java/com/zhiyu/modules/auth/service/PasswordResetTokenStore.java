package com.zhiyu.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 忘记密码一次性重置令牌存储（2026-11-03 账号安全）。
 *
 * <p>只存令牌 SHA-256 哈希 → userId 的映射，令牌明文只出现在发送通道中，
 * 不落库、不写日志。令牌限时（默认 30 分钟）、一次性（consume 读取即删除）。
 *
 * <p>主存储为 Redis（TTL 自动过期）；Redis 异常时降级为进程内存
 * （与 {@link LoginAttemptStore} 同款冷却策略）。
 */
@Slf4j
@Component
public class PasswordResetTokenStore {

    private static final String KEY_PREFIX = "pwdreset:";
    private static final int REDIS_FAIL_THRESHOLD = 3;
    private static final long REDIS_COOLDOWN_MILLIS = 30_000L;

    /** GET+DEL 原子脚本：保证「用后失效」，且兼容 Redis 6.2 以下（无 GETDEL 命令）。 */
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); "
                    + "if v then redis.call('DEL', KEYS[1]); end; return v",
            String.class);

    @Nullable
    private final StringRedisTemplate redis;

    /** 内存降级存储：令牌哈希 -> (userId, 过期时间戳) */
    private final ConcurrentMap<String, MemoryToken> memory = new ConcurrentHashMap<>();
    private final AtomicInteger redisFailCount = new AtomicInteger(0);
    private volatile long redisCooldownUntil = 0L;

    public PasswordResetTokenStore(@Nullable StringRedisTemplate redis,
                                   @Value("${zhiyu.auth.password-reset.store:redis}") String store) {
        // store=memory 时强制走内存（单测/无 Redis 环境），避免把令牌写进真实 Redis
        this.redis = "memory".equalsIgnoreCase(store) ? null : redis;
    }

    /** 令牌明文 → SHA-256 十六进制。存储与比对一律使用哈希。 */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 保证 SHA-256 存在，走不到
            throw new IllegalStateException(e);
        }
    }

    /** 保存令牌哈希映射，ttlSeconds 后自动失效。 */
    public void save(String tokenHash, long userId, long ttlSeconds) {
        redisFirst("save",
                () -> {
                    redis.opsForValue().set(KEY_PREFIX + tokenHash, String.valueOf(userId),
                            ttlSeconds, TimeUnit.SECONDS);
                    return null;
                },
                () -> {
                    memory.put(tokenHash, new MemoryToken(userId,
                            System.currentTimeMillis() + ttlSeconds * 1000L));
                    return null;
                });
    }

    /**
     * 消费令牌：读取即删除（一次性）。
     *
     * @return 对应的 userId；令牌不存在、已过期或已被使用时返回 null
     */
    @Nullable
    public Long consume(String tokenHash) {
        return redisFirst("consume",
                () -> {
                    String value = redis.execute(GET_AND_DELETE_SCRIPT,
                            java.util.List.of(KEY_PREFIX + tokenHash));
                    return value == null ? null : Long.valueOf(value);
                },
                () -> {
                    MemoryToken token = memory.remove(tokenHash);
                    if (token == null || System.currentTimeMillis() > token.expireAtMillis) {
                        return null;
                    }
                    return token.userId;
                });
    }

    private <T> T redisFirst(String op, Supplier<T> redisCall, Supplier<T> memoryCall) {
        if (redis != null && System.currentTimeMillis() >= redisCooldownUntil) {
            try {
                T result = redisCall.get();
                redisFailCount.set(0);
                return result;
            } catch (Exception e) {
                int count = redisFailCount.incrementAndGet();
                log.warn("重置令牌 Redis {} 异常，降级内存（连续失败={}）: {}", op, count, e.getMessage());
                if (count >= REDIS_FAIL_THRESHOLD) {
                    redisCooldownUntil = System.currentTimeMillis() + REDIS_COOLDOWN_MILLIS;
                    log.warn("重置令牌 Redis 连续失败 {} 次，进入 30 秒冷却期", count);
                }
            }
        }
        return memoryCall.get();
    }

    /** 内存降级的令牌记录。 */
    private record MemoryToken(long userId, long expireAtMillis) {
    }
}
