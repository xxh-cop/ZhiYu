package com.zhiyu.modules.dialogue.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调度三道闸单测（P2）：重复意图、振荡锁定、会话预算与 Redis 故障降级。
 */
class RouteGuardTest {

    private RouteGuard memoryGuard() {
        return new RouteGuard(null, "memory", 3, 600, true);
    }

    @Test
    @DisplayName("重复意图：同哈希第 3 次达到锁定阈值")
    void repeatIntentLocksAtThreshold() {
        RouteGuard guard = memoryGuard();
        String h = RouteGuard.normalizeHash("我总想同一件事");
        assertEquals(1, guard.registerIntent(7L, "s1", h));
        assertFalse(guard.isRepeatLocked(guard.registerIntent(7L, "s1", h)));
        assertTrue(guard.isRepeatLocked(guard.registerIntent(7L, "s1", h)));
    }

    @Test
    @DisplayName("重复意图：不同会话/不同文本互不计数")
    void repeatIntentIsolatedBySessionAndHash() {
        RouteGuard guard = memoryGuard();
        String h = RouteGuard.normalizeHash("同一件事");
        guard.registerIntent(7L, "s1", h);
        guard.registerIntent(7L, "s1", h);
        assertEquals(1, guard.registerIntent(7L, "s2", h));
        assertEquals(1, guard.registerIntent(7L, "s1", RouteGuard.normalizeHash("另一件事")));
    }

    @Test
    @DisplayName("规范化哈希：标点/空白/大小写差异归一")
    void normalizeHashIgnoresPunctuationAndCase() {
        assertEquals(RouteGuard.normalizeHash("我总想同一件事"),
                RouteGuard.normalizeHash(" 我总想，同一件事！"));
        assertNotEquals(RouteGuard.normalizeHash("我总想同一件事"),
                RouteGuard.normalizeHash("我总想另一件事"));
    }

    @Test
    @DisplayName("振荡锁定：A→B→A 后计算为 B → 强制留 A 并写历史")
    void oscillationLockForcesBackToA() {
        RouteGuard guard = memoryGuard();
        assertEquals("LISTENING", guard.applyOscillationLock("s1", "LISTENING"));
        assertEquals("KNOWLEDGE", guard.applyOscillationLock("s1", "KNOWLEDGE"));
        assertEquals("LISTENING", guard.applyOscillationLock("s1", "LISTENING"));
        // 历史 [L,K,L]，本轮计算 KNOWLEDGE（=B）→ 强制 LISTENING（=A）
        assertEquals("LISTENING", guard.applyOscillationLock("s1", "KNOWLEDGE"));
    }

    @Test
    @DisplayName("振荡锁定：连续同能力（A→A→A）不触发")
    void oscillationNotTriggeredForSameCapability() {
        RouteGuard guard = memoryGuard();
        guard.applyOscillationLock("s1", "LISTENING");
        guard.applyOscillationLock("s1", "LISTENING");
        guard.applyOscillationLock("s1", "LISTENING");
        assertEquals("KNOWLEDGE", guard.applyOscillationLock("s1", "KNOWLEDGE"));
    }

    @Test
    @DisplayName("振荡锁定关闭开关：直接透传计算结果")
    void oscillationDisabledPassesThrough() {
        RouteGuard guard = new RouteGuard(null, "memory", 3, 600, false);
        guard.applyOscillationLock("s1", "LISTENING");
        guard.applyOscillationLock("s1", "KNOWLEDGE");
        guard.applyOscillationLock("s1", "LISTENING");
        assertEquals("KNOWLEDGE", guard.applyOscillationLock("s1", "KNOWLEDGE"));
    }

    @Test
    @DisplayName("会话预算：轮次计数递增")
    void turnBudgetCounts() {
        RouteGuard guard = memoryGuard();
        assertEquals(0, guard.currentTurns("s1"));
        guard.incrementTurns("s1");
        guard.incrementTurns("s1");
        assertEquals(2, guard.currentTurns("s1"));
    }

    @Test
    @DisplayName("Redis 连续失败 3 次 → 冷却降级内存（不直接放行、不再触碰 Redis）")
    void redisFailureFallsBackToMemory() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("mock 断连"));
        when(redis.opsForList()).thenThrow(new RedisConnectionFailureException("mock 断连"));
        RouteGuard guard = new RouteGuard(redis, "redis", 3, 600, true);

        // 前 3 次失败都降级到内存重放（结果为内存侧的合法值，不是放行跳过）
        assertEquals(0, guard.currentTurns("s1"));
        assertEquals(0, guard.currentTurns("s1"));
        assertEquals(0, guard.currentTurns("s1"));
        // 已进入冷却：第 4 次起不再调 Redis，直接走内存
        assertEquals(1, guard.incrementTurns("s1"));
        assertEquals(1, guard.currentTurns("s1"));
        verify(redis, times(3)).opsForValue();
    }
}
