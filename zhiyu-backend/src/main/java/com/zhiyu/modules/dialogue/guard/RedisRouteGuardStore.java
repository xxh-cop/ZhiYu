package com.zhiyu.modules.dialogue.guard;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 版闸口状态存储（P2 默认）。固定窗口计数 + capped list 路由历史。
 * 纯类，由 {@link RouteGuard} 内部装配（配合内存降级）。
 */
public class RedisRouteGuardStore implements RouteGuardStore {

    private static final String PREFIX = "dlg:g:";

    private final StringRedisTemplate redis;

    public RedisRouteGuardStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int incrementTurns(String sessionKey, int ttlSeconds) {
        return incr(PREFIX + "turns:" + sessionKey, ttlSeconds);
    }

    @Override
    public int currentTurns(String sessionKey) {
        String v = redis.opsForValue().get(PREFIX + "turns:" + sessionKey);
        return v == null ? 0 : Integer.parseInt(v);
    }

    @Override
    public int incrementIntent(String intentKey, int windowSeconds) {
        return incr(PREFIX + "intent:" + intentKey, windowSeconds);
    }

    @Override
    public List<String> routeHistory(String sessionKey, int keep) {
        List<String> all = redis.opsForList().range(PREFIX + "hist:" + sessionKey, 0, -1);
        return all == null ? List.of() : all;
    }

    @Override
    public void pushRoute(String sessionKey, String capability, int keep) {
        String key = PREFIX + "hist:" + sessionKey;
        redis.opsForList().rightPush(key, capability);
        redis.opsForList().trim(key, -keep, -1);
        redis.expire(key, 1, TimeUnit.DAYS);
    }

    private int incr(String key, int ttlSeconds) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return v == null ? 0 : v.intValue();
    }
}
