package com.zhiyu.modules.dialogue.guard;

import java.util.List;

/**
 * 调度闸口状态存储（P2）。默认 Redis；不可用时由 {@link FallbackRouteGuardStore}
 * 降级内存 + 冷却（与既有 FallbackCircuitBreakerStore 同族语义），不得直接放行。
 */
public interface RouteGuardStore {

    /** 会话轮次计数+1，返回最新值（key 带 TTL）。 */
    int incrementTurns(String sessionKey, int ttlSeconds);

    /** 读取会话轮次计数（无 key 返回 0）。 */
    int currentTurns(String sessionKey);

    /** 记录一次意图哈希（窗口 TTL = repeatWindowSeconds），返回窗口内该哈希累计次数。 */
    int incrementIntent(String intentKey, int windowSeconds);

    /** 最近 N 次路由的能力序列（最旧→最新）。 */
    List<String> routeHistory(String sessionKey, int keep);

    /** 追加一次路由结果，只保留最近 keep 条。 */
    void pushRoute(String sessionKey, String capability, int keep);
}
