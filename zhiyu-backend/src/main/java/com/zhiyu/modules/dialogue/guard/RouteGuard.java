package com.zhiyu.modules.dialogue.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 调度三道闸（P2，ai-config/ai-capabilities.yaml dialogue.guards 段数值的 Spring 承载）：
 * <ul>
 *   <li><b>重复意图</b>：规范化文本哈希同值 ≥{@code times} 次/{@code windowSeconds}s ⇒ 锁定
 *       （由 commander 给温和确定性提示，trace REPEAT_INTENT_LOCK）；</li>
 *   <li><b>振荡锁定</b>：路由历史 3 条内出现 A→B→A 且本次计算=B ⇒ 强制留在 A；</li>
 *   <li><b>预算</b>：会话轮次计数（commander 每轮 increment，超 sessionTurnBudget 即收尾）。</li>
 * </ul>
 * 存储：{@code zhiyu.dialogue.guard-store}=redis（默认）|memory；Redis 连续失败 3 次
 * 冷却 30 秒降级内存（与 FallbackCircuitBreakerStore 同族），<b>不得直接放行</b>。
 * 深度上限不在此类——它在 commander 结构上保证（每轮最多 1 路由 + 1 执行）。
 */
@Slf4j
@Component
public class RouteGuard {

    private static final int REDIS_FAIL_THRESHOLD = 3;
    private static final long REDIS_COOLDOWN_MS = 30_000;

    private final RouteGuardStore primary;
    private final RouteGuardStore memory;
    private final boolean primaryIsRedis;
    private final int repeatTimes;
    private final int repeatWindowSeconds;
    private final boolean oscillationLock;

    private int consecutiveRedisFailures = 0;
    private long redisCooldownUntil = 0L;

    @org.springframework.beans.factory.annotation.Autowired
    public RouteGuard(StringRedisTemplate redis,
                      @Value("${zhiyu.dialogue.guard-store:redis}") String store,
                      @Value("${zhiyu.dialogue.repeat-intent-times:3}") int repeatTimes,
                      @Value("${zhiyu.dialogue.repeat-window-seconds:600}") int repeatWindowSeconds,
                      @Value("${zhiyu.dialogue.oscillation-lock:true}") boolean oscillationLock) {
        this.repeatTimes = repeatTimes;
        this.repeatWindowSeconds = repeatWindowSeconds;
        this.oscillationLock = oscillationLock;
        this.primaryIsRedis = "redis".equals(store) && redis != null;
        this.memory = new MemoryRouteGuardStore();
        this.primary = primaryIsRedis ? new RedisRouteGuardStore(redis) : this.memory;
        log.info("RouteGuard 初始化完成: store={} ({}), 重复意图≥{}次/{}s, 振荡锁定={}",
                store, primaryIsRedis ? "redis" : "memory", repeatTimes, repeatWindowSeconds, oscillationLock);
    }

    /** 测试用：注入自定义 store。 */
    RouteGuard(RouteGuardStore store, int repeatTimes, int repeatWindowSeconds, boolean oscillationLock) {
        this.primary = store;
        this.primaryIsRedis = false;
        this.memory = store;
        this.repeatTimes = repeatTimes;
        this.repeatWindowSeconds = repeatWindowSeconds;
        this.oscillationLock = oscillationLock;
    }

    // ── 预算（会话轮次） ──

    public int currentTurns(String sessionKey) {
        return exec(s -> s.currentTurns(sessionKey));
    }

    /** commander 每完成一轮调用一次。 */
    public int incrementTurns(String sessionKey) {
        return exec(s -> s.incrementTurns(sessionKey, 24 * 3600));
    }

    // ── 重复意图 ──

    /** 规范化：去标点（含中文标点 \p{P}）/空白/小写化后取 SHA-256（前 16 字节 hex）。 */
    public static String normalizeHash(String text) {
        String normalized = text == null ? "" : text.replaceAll("[\\s\\p{P}]+", "").toLowerCase();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 登记本次意图并返回窗口内累计次数。 */
    public int registerIntent(long userId, String sessionKey, String normalizedHash) {
        return exec(s -> s.incrementIntent(userId + ":" + sessionKey + ":" + normalizedHash, repeatWindowSeconds));
    }

    public boolean isRepeatLocked(int intentCount) {
        return intentCount >= repeatTimes;
    }

    // ── 振荡锁定 ──

    /**
     * A→B→A 振荡检测与锁定。规则：路由历史最近 3 条为 [X, Y, X] 且本次计算为 Y ⇒ 强制 X。
     * 每次调用都会把最终能力写进历史（含被锁定后的值）。
     */
    public String applyOscillationLock(String sessionKey, String computed) {
        String result = computed;
        if (oscillationLock) {
            List<String> hist = exec(s -> s.routeHistory(sessionKey, 3));
            if (hist.size() >= 3) {
                List<String> last3 = hist.subList(hist.size() - 3, hist.size());
                if (last3.get(0).equals(last3.get(2)) && !last3.get(0).equals(last3.get(1))
                        && computed.equals(last3.get(1))) {
                    result = last3.get(0);
                    log.info("振荡锁定: session={}, {}→{}→{} 后计算为 {}，强制留 {}",
                            sessionKey, last3.get(0), last3.get(1), last3.get(2), computed, result);
                }
            }
        }
        String finalResult = result;
        exec(s -> {
            s.pushRoute(sessionKey, finalResult, 3);
            return null;
        });
        return result;
    }

    // ── Redis 降级（连续失败冷却 + 内存重放；不得直接放行） ──

    private <T> T exec(java.util.function.Function<RouteGuardStore, T> op) {
        if (!primaryIsRedis) {
            return op.apply(primary);
        }
        if (System.currentTimeMillis() < redisCooldownUntil) {
            return op.apply(memory);
        }
        try {
            T r = op.apply(primary);
            consecutiveRedisFailures = 0;
            return r;
        } catch (RuntimeException e) {
            consecutiveRedisFailures++;
            if (consecutiveRedisFailures >= REDIS_FAIL_THRESHOLD) {
                redisCooldownUntil = System.currentTimeMillis() + REDIS_COOLDOWN_MS;
                log.warn("RouteGuard Redis 连续失败 {} 次，冷却 {}ms 降级内存", consecutiveRedisFailures, REDIS_COOLDOWN_MS);
            }
            return op.apply(memory);
        }
    }
}
