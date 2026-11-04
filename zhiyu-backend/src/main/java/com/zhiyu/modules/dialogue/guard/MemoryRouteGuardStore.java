package com.zhiyu.modules.dialogue.guard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版闸口状态存储（单实例/测试/Redis 降级用）。条目带到期时间，读取时惰性过期。
 * 纯类，由 {@link RouteGuard} 内部装配（与 FallbackCircuitBreakerStore 同族结构）。
 */
public class MemoryRouteGuardStore implements RouteGuardStore {

    private record Counter(int value, long expireAtMs) {
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> histories = new ConcurrentHashMap<>();

    @Override
    public int incrementTurns(String sessionKey, int ttlSeconds) {
        return incr("turns:" + sessionKey, ttlSeconds);
    }

    @Override
    public int currentTurns(String sessionKey) {
        Counter c = counters.get("turns:" + sessionKey);
        if (c == null || c.expireAtMs() < System.currentTimeMillis()) {
            return 0;
        }
        return c.value();
    }

    @Override
    public int incrementIntent(String intentKey, int windowSeconds) {
        return incr("intent:" + intentKey, windowSeconds);
    }

    @Override
    public List<String> routeHistory(String sessionKey, int keep) {
        Deque<String> d = histories.get("hist:" + sessionKey);
        if (d == null) {
            return List.of();
        }
        synchronized (d) {
            return new ArrayList<>(d);
        }
    }

    @Override
    public void pushRoute(String sessionKey, String capability, int keep) {
        Deque<String> d = histories.computeIfAbsent("hist:" + sessionKey, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(capability);
            while (d.size() > keep) {
                d.removeFirst();
            }
        }
    }

    private int incr(String key, int ttlSeconds) {
        long now = System.currentTimeMillis();
        return counters.compute(key, (k, old) -> {
            if (old == null || old.expireAtMs() < now) {
                return new Counter(1, now + ttlSeconds * 1000L);
            }
            return new Counter(old.value() + 1, old.expireAtMs());
        }).value();
    }
}
