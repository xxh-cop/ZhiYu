package com.zhiyu.ai.flow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FlowDetectionService} 单元测试（T1.7 子步骤 2）。
 *
 * <p>用 {@link InMemoryFlowStatStore}（纯内存，不依赖 Redis / Spring 上下文）。
 * 阈值通过反射注入（focusIdleSeconds=300, stuckRepeatTimes=3）。
 */
class FlowDetectionServiceTest {

    private InMemoryFlowStatStore store;
    private FlowDetectionService service;

    @BeforeEach
    void setUp() throws Exception {
        store = new InMemoryFlowStatStore();
        service = new FlowDetectionService(store);
        // 注入 @Value 字段（不启动 Spring）
        var fieldFocus = FlowDetectionService.class.getDeclaredField("focusIdleSeconds");
        fieldFocus.setAccessible(true);
        fieldFocus.set(service, 300);
        var fieldStuck = FlowDetectionService.class.getDeclaredField("stuckRepeatTimes");
        fieldStuck.setAccessible(true);
        fieldStuck.set(service, 3);
    }

    @Test
    @DisplayName("首次心跳 → NORMAL, delta=0")
    void testFirstHeartbeat_Normal() {
        FlowDetectionResult result = service.evaluate(1L, 1, 50, false, false);

        assertEquals(FlowState.NORMAL, result.state());
        assertEquals(0, result.flowDelta());
    }

    @Test
    @DisplayName("dwellSeconds=301 → FLOW, delta=301（首步）")
    void testFlowAfterIdle() {
        // 首次心跳初始化 stepNo=1, dwellSeconds=0
        service.evaluate(1L, 1, 0, false, false);

        // 第二次心跳 dwellSeconds=301
        FlowDetectionResult result = service.evaluate(1L, 1, 301, false, false);

        assertEquals(FlowState.FLOW, result.state());
        assertEquals(301, result.flowDelta());
    }

    @Test
    @DisplayName("dwellSeconds=301 但 isExited=true → NORMAL")
    void testIsExited_NotFlow() {
        service.evaluate(1L, 1, 0, false, false);

        FlowDetectionResult result = service.evaluate(1L, 1, 301, true, false);

        assertEquals(FlowState.NORMAL, result.state());
        assertEquals(0, result.flowDelta());
    }

    @Test
    @DisplayName("3 次回退 → repeatCount=3, NORMAL（未超阈值 >3）")
    void testRepeatAccumulates() {
        // stepNo 1→2→1→2→1 → 3 次回退
        service.evaluate(1L, 1, 10, false, false);
        service.evaluate(1L, 2, 10, false, false);  // 前进
        service.evaluate(1L, 1, 10, false, false);  // 回退 1
        service.evaluate(1L, 2, 10, false, false);  // 前进
        service.evaluate(1L, 1, 10, false, false);  // 回退 2
        service.evaluate(1L, 2, 10, false, false);  // 前进
        service.evaluate(1L, 1, 10, false, false);  // 回退 3

        // repeatCount=3, stuckRepeatTimes=3, 判定条件是 >3，所以 3 不满足
        FlowDetectionResult result = service.evaluate(1L, 1, 10, false, false);
        // stepNo 没变（还是1），repeatCount 不变
        assertEquals(FlowState.NORMAL, result.state());
    }

    @Test
    @DisplayName("4 次回退 → ANXIOUS（repeatCount > 3）")
    void testRepeatOverThreshold_Anxious() {
        // 1→2→1→2→1→2→1→2→1 → 4 次回退
        service.evaluate(1L, 1, 10, false, false);
        service.evaluate(1L, 2, 10, false, false);
        service.evaluate(1L, 1, 10, false, false);  // 回退 1
        service.evaluate(1L, 2, 10, false, false);
        service.evaluate(1L, 1, 10, false, false);  // 回退 2
        service.evaluate(1L, 2, 10, false, false);
        service.evaluate(1L, 1, 10, false, false);  // 回退 3
        service.evaluate(1L, 2, 10, false, false);
        // 第 4 次回退
        FlowDetectionResult result = service.evaluate(1L, 1, 10, false, false);

        assertEquals(FlowState.ANXIOUS, result.state());
    }

    @Test
    @DisplayName("4 次回退后 isBreathingResponded=true → NORMAL（repeatCount 清零）")
    void testBreathingResponded_ResetsRepeat() {
        // 4 次回退 → ANXIOUS
        for (int i = 0; i < 4; i++) {
            service.evaluate(1L, 2, 10, false, false);
            service.evaluate(1L, 1, 10, false, false);
        }

        // 响应呼吸提醒 → repeatCount 清零
        FlowDetectionResult result = service.evaluate(1L, 1, 10, false, true);

        assertEquals(FlowState.NORMAL, result.state());
    }

    @Test
    @DisplayName("回退 2 次后前进，repeatCount 仍是 2（不清零）")
    void testStepAdvance_KeepsRepeat() {
        // stepNo 1→2→1（回退1）→2→1（回退2）
        service.evaluate(1L, 1, 10, false, false);
        service.evaluate(1L, 2, 10, false, false);
        service.evaluate(1L, 1, 10, false, false);  // 回退 1
        service.evaluate(1L, 2, 10, false, false);  // 前进
        service.evaluate(1L, 1, 10, false, false);  // 回退 2
        service.evaluate(1L, 2, 10, false, false);  // 前进

        // 验证 repeatCount 仍是 2：再回退 1 次到 1，repeatCount=3，>3 仍不满足
        FlowDetectionResult result = service.evaluate(1L, 1, 10, false, false);
        assertEquals(FlowState.NORMAL, result.state());  // repeatCount=3, 不 >3

        // 再回退 1 次，repeatCount=4 > 3 → ANXIOUS
        service.evaluate(1L, 2, 10, false, false);
        FlowDetectionResult result2 = service.evaluate(1L, 1, 10, false, false);
        assertEquals(FlowState.ANXIOUS, result2.state());
    }

    @Test
    @DisplayName("两次相同 dwellSeconds，delta 第二次为 0")
    void testFlowDeltaDeduplication() {
        // 首次心跳初始化
        service.evaluate(1L, 1, 0, false, false);

        // 第一次 dwellSeconds=350 → FLOW, delta=350
        FlowDetectionResult r1 = service.evaluate(1L, 1, 350, false, false);
        assertEquals(FlowState.FLOW, r1.state());
        assertEquals(350, r1.flowDelta());

        // 第二次 dwellSeconds 仍=350 → FLOW, delta=0（去重）
        FlowDetectionResult r2 = service.evaluate(1L, 1, 350, false, false);
        assertEquals(FlowState.FLOW, r2.state());
        assertEquals(0, r2.flowDelta());
    }
}
