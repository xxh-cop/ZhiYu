package com.zhiyu.ai.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link FallbackCircuitBreakerStore} 单测（T1.1b）。
 *
 * <p>验证装饰器模式：Redis 正常用 Redis，Redis 异常切内存。
 */
class FallbackCircuitBreakerStoreTest {

    @Test
    @DisplayName("Redis 正常 → 结果来自 Redis")
    void testRedisHealthy_UsesRedis() {
        RedisCircuitBreakerStore redisStore = mock(RedisCircuitBreakerStore.class);
        InMemoryCircuitBreakerStore memoryStore = mock(InMemoryCircuitBreakerStore.class);

        when(redisStore.allowRequest("k")).thenReturn(true);
        when(redisStore.implementationName()).thenReturn("redis");

        FallbackCircuitBreakerStore fallback = new FallbackCircuitBreakerStore(redisStore, memoryStore);

        assertTrue(fallback.allowRequest("k"));
        assertEquals("redis", fallback.implementationName());

        verify(redisStore).allowRequest("k");
        verify(memoryStore, never()).allowRequest(any());
    }

    @Test
    @DisplayName("Redis 抛异常 → 切内存，implementationName 返回 fallback")
    void testRedisThrows_FallsBackToMemory() {
        RedisCircuitBreakerStore redisStore = mock(RedisCircuitBreakerStore.class);
        InMemoryCircuitBreakerStore memoryStore = mock(InMemoryCircuitBreakerStore.class);

        when(redisStore.allowRequest("k")).thenThrow(new RuntimeException("Connection refused"));
        when(memoryStore.allowRequest("k")).thenReturn(true);

        FallbackCircuitBreakerStore fallback = new FallbackCircuitBreakerStore(redisStore, memoryStore);

        assertTrue(fallback.allowRequest("k"));
        assertEquals("in-memory (fallback)", fallback.implementationName());

        verify(memoryStore).allowRequest("k");
    }
}
