package com.zhiyu.ai.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InMemoryCircuitBreakerStore} 单测（T1.1b）。
 *
 * <p>用小参数构造：threshold=3, openDurationMillis=100，方便测试状态转换。
 */
class InMemoryCircuitBreakerStoreTest {

    private InMemoryCircuitBreakerStore createStore() {
        return new InMemoryCircuitBreakerStore(3, 100L);
    }

    @Test
    @DisplayName("初始状态 CLOSED")
    void testClosedInitially() {
        InMemoryCircuitBreakerStore store = createStore();
        assertEquals(BreakerState.CLOSED, store.getState("llm-recommend"));
        assertTrue(store.allowRequest("llm-recommend"));
    }

    @Test
    @DisplayName("成功不改变 CLOSED 状态")
    void testSuccessKeepsClosed() {
        InMemoryCircuitBreakerStore store = createStore();
        store.recordSuccess("llm-recommend");
        assertEquals(BreakerState.CLOSED, store.getState("llm-recommend"));
        assertTrue(store.allowRequest("llm-recommend"));
    }

    @Test
    @DisplayName("失败次数未达阈值仍 CLOSED")
    void testFailureUnderThreshold() {
        InMemoryCircuitBreakerStore store = createStore();
        store.recordFailure("llm-recommend");
        store.recordFailure("llm-recommend");
        // 2 < 3，仍 CLOSED
        assertEquals(BreakerState.CLOSED, store.getState("llm-recommend"));
        assertTrue(store.allowRequest("llm-recommend"));
    }

    @Test
    @DisplayName("失败达阈值变 OPEN")
    void testFailureHitsThreshold() {
        InMemoryCircuitBreakerStore store = createStore();
        store.recordFailure("llm-recommend");
        store.recordFailure("llm-recommend");
        store.recordFailure("llm-recommend");
        // 3 >= 3，OPEN
        assertEquals(BreakerState.OPEN, store.getState("llm-recommend"));
        assertFalse(store.allowRequest("llm-recommend"));
    }

    @Test
    @DisplayName("OPEN 到期后 allowRequest 返回 true 并切 HALF_OPEN")
    void testOpenToHalfOpenAfterDuration() throws InterruptedException {
        InMemoryCircuitBreakerStore store = createStore();
        // 触发 OPEN
        store.recordFailure("k");
        store.recordFailure("k");
        store.recordFailure("k");
        assertEquals(BreakerState.OPEN, store.getState("k"));
        assertFalse(store.allowRequest("k"));

        // 等待到期（100ms + Windows sleep 精度余量）
        Thread.sleep(300);

        // allowRequest 应放行并切 HALF_OPEN
        assertTrue(store.allowRequest("k"));
        assertEquals(BreakerState.HALF_OPEN, store.getState("k"));
    }

    @Test
    @DisplayName("HALF_OPEN 试探失败 → 重新 OPEN")
    void testHalfOpenFailureReopens() throws InterruptedException {
        InMemoryCircuitBreakerStore store = createStore();
        // 触发 OPEN
        store.recordFailure("k");
        store.recordFailure("k");
        store.recordFailure("k");
        Thread.sleep(300);
        // 切 HALF_OPEN
        store.allowRequest("k");
        assertEquals(BreakerState.HALF_OPEN, store.getState("k"));

        // 试探失败
        store.recordFailure("k");
        assertEquals(BreakerState.OPEN, store.getState("k"));
        assertFalse(store.allowRequest("k"));
    }
}
