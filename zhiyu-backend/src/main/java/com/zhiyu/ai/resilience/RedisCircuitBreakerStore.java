package com.zhiyu.ai.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 版熔断器存储（T1.1b）。
 *
 * <p>生效条件：{@code zhiyu.ai.resilience.store=redis}。
 * 多实例共享熔断状态，重启不丢失。
 *
 * <p>Key 设计：
 * <pre>
 *   breaker:{resourceKey}:state       → "CLOSED" | "OPEN" | "HALF_OPEN"
 *   breaker:{resourceKey}:fail_count  → 数字字符串
 *   breaker:{resourceKey}:open_until  → 时间戳毫秒字符串
 * </pre>
 *
 * <p>状态转换用 Lua 脚本保证原子性。
 *
 * <p><b>关键设计</b>：所有 Redis 调用用 try-catch 包裹，
 * 任何 RedisException 都返回「安全默认值」：
 * <ul>
 *   <li>{@code allowRequest} → true（放行，宁可熔断器失效也不能拦截所有请求）</li>
 *   <li>{@code recordSuccess/recordFailure} → 静默吞掉，只记 WARN 日志</li>
 * </ul>
 * 熔断器本身不能成为单点故障。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "zhiyu.ai.resilience.store", havingValue = "redis")
public class RedisCircuitBreakerStore implements CircuitBreakerStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final int failureThreshold;
    private final long openDurationMillis;

    // ── Lua 脚本 ──

    /** allowRequest：原子地读状态 + 判断 + 更新（OPEN→HALF_OPEN） */
    private static final DefaultRedisScript<String> ALLOW_SCRIPT;
    /** recordFailure：原子地递增计数 + 判断阈值 + 更新状态 */
    private static final DefaultRedisScript<String> FAILURE_SCRIPT;

    static {
        ALLOW_SCRIPT = new DefaultRedisScript<>();
        ALLOW_SCRIPT.setScriptText(
                """
                local state = redis.call('GET', KEYS[1])
                if not state or state == 'CLOSED' then
                    return 'ALLOW'
                end
                if state == 'OPEN' then
                    local open_until = tonumber(redis.call('GET', KEYS[3]) or '0')
                    if tonumber(ARGV[1]) < open_until then
                        return 'BLOCK'
                    end
                    redis.call('SET', KEYS[1], 'HALF_OPEN')
                    return 'ALLOW'
                end
                -- HALF_OPEN
                return 'BLOCK'
                """);
        ALLOW_SCRIPT.setResultType(String.class);

        FAILURE_SCRIPT = new DefaultRedisScript<>();
        FAILURE_SCRIPT.setScriptText(
                """
                local state = redis.call('GET', KEYS[1])
                if state == 'HALF_OPEN' then
                    redis.call('SET', KEYS[1], 'OPEN')
                    redis.call('SET', KEYS[3], ARGV[2])
                    redis.call('SET', KEYS[2], '0')
                    return 'OPEN'
                end
                local count = redis.call('INCR', KEYS[2])
                if count >= tonumber(ARGV[1]) then
                    redis.call('SET', KEYS[1], 'OPEN')
                    redis.call('SET', KEYS[3], ARGV[2])
                    redis.call('SET', KEYS[2], '0')
                    return 'OPEN'
                end
                return state or 'CLOSED'
                """);
        FAILURE_SCRIPT.setResultType(String.class);
    }

    public RedisCircuitBreakerStore(
            RedisTemplate<String, String> redisTemplate,
            @Value("${zhiyu.ai.resilience.failure-threshold:5}") int failureThreshold,
            @Value("${zhiyu.ai.resilience.open-duration-seconds:60}") int openDurationSeconds) {
        this.redisTemplate = redisTemplate;
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationSeconds * 1000L;
        log.info("RedisCircuitBreakerStore 初始化（threshold={}, openDuration={}ms）", failureThreshold, openDurationMillis);
    }

    private String stateKey(String key) { return "breaker:" + key + ":state"; }
    private String failKey(String key) { return "breaker:" + key + ":fail_count"; }
    private String openUntilKey(String key) { return "breaker:" + key + ":open_until"; }

    @Override
    public boolean allowRequest(String key) {
        try {
            String now = String.valueOf(System.currentTimeMillis());
            String result = redisTemplate.execute(
                    ALLOW_SCRIPT,
                    List.of(stateKey(key), failKey(key), openUntilKey(key)),
                    now);
            return "ALLOW".equals(result);
        } catch (Exception e) {
            log.warn("Redis allowRequest 异常，放行（安全默认）: key={}, err={}", key, e.getMessage());
            return true;
        }
    }

    @Override
    public void recordSuccess(String key) {
        try {
            redisTemplate.opsForValue().set(stateKey(key), "CLOSED");
            redisTemplate.opsForValue().set(failKey(key), "0");
        } catch (Exception e) {
            log.warn("Redis recordSuccess 异常，静默吞掉: key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public void recordFailure(String key) {
        try {
            String now = String.valueOf(System.currentTimeMillis());
            String openUntil = String.valueOf(System.currentTimeMillis() + openDurationMillis);
            redisTemplate.execute(
                    FAILURE_SCRIPT,
                    List.of(stateKey(key), failKey(key), openUntilKey(key)),
                    String.valueOf(failureThreshold), openUntil);
        } catch (Exception e) {
            log.warn("Redis recordFailure 异常，静默吞掉: key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public BreakerState getState(String key) {
        try {
            String state = redisTemplate.opsForValue().get(stateKey(key));
            if (state == null) {
                return BreakerState.CLOSED;
            }
            return BreakerState.valueOf(state);
        } catch (Exception e) {
            log.warn("Redis getState 异常，返回 CLOSED: key={}, err={}", key, e.getMessage());
            return BreakerState.CLOSED;
        }
    }

    @Override
    public String implementationName() {
        return "redis";
    }
}
