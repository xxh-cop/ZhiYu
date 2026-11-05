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
    @DisplayName("dwellSeconds=301 → FLOW, delta=1（修复灌水：只计跨阈值后的 1 秒）")
    void testFlowAfterIdle() {
        // 首次心跳初始化 stepNo=1, dwellSeconds=0
        service.evaluate(1L, 1, 0, false, false);

        // 第二次心跳 dwellSeconds=301
        FlowDetectionResult result = service.evaluate(1L, 1, 301, false, false);

        assertEquals(FlowState.FLOW, result.state());
        assertEquals(1, result.flowDelta());
    }

    @Test
    @DisplayName("恰好跨阈值：dwell=300 → FLOW, delta=0（刚达标不白送 300 秒）")
    void testExactlyAtThreshold_ZeroDelta() {
        service.evaluate(1L, 1, 0, false, false);

        FlowDetectionResult result = service.evaluate(1L, 1, 300, false, false);

        assertEquals(FlowState.FLOW, result.state());
        assertEquals(0, result.flowDelta());
    }

    @Test
    @DisplayName("阈值后持续：300→310→320，切片累加 0/10/10，总量=320-300")
    void testPostThresholdAccumulatesBySlices() {
        service.evaluate(1L, 1, 0, false, false);

        FlowDetectionResult r1 = service.evaluate(1L, 1, 300, false, false);
        FlowDetectionResult r2 = service.evaluate(1L, 1, 310, false, false);
        FlowDetectionResult r3 = service.evaluate(1L, 1, 320, false, false);

        assertEquals(0, r1.flowDelta());
        assertEquals(10, r2.flowDelta());
        assertEquals(10, r3.flowDelta());
        assertEquals(20, r1.flowDelta() + r2.flowDelta() + r3.flowDelta());
    }

    @Test
    @DisplayName("反复跨越：跨阈值→跌出→再跨入，重新切片且不重复累计")
    void testReCrossingThreshold_NoDoubleCount() {
        service.evaluate(1L, 1, 0, false, false);

        // 第一次跨越：dwell=350 → delta=50（350-300）
        FlowDetectionResult r1 = service.evaluate(1L, 1, 350, false, false);
        assertEquals(FlowState.FLOW, r1.state());
        assertEquals(50, r1.flowDelta());

        // 跌出阈值：dwell=100 → NORMAL，基准清零
        FlowDetectionResult r2 = service.evaluate(1L, 1, 100, false, false);
        assertEquals(FlowState.NORMAL, r2.state());
        assertEquals(0, r2.flowDelta());

        // 再次跨越：dwell=320 → delta=20（320-300），不重复累计 0-300，也不重复 350 的高水位
        FlowDetectionResult r3 = service.evaluate(1L, 1, 320, false, false);
        assertEquals(FlowState.FLOW, r3.state());
        assertEquals(20, r3.flowDelta());

        // 总量 = (350-300) + (320-300) = 70，恰好是两段阈值后时长
        assertEquals(70, r1.flowDelta() + r2.flowDelta() + r3.flowDelta());
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

        // 第一次 dwellSeconds=350 → FLOW, delta=50（350-300，只计跨阈值后）
        FlowDetectionResult r1 = service.evaluate(1L, 1, 350, false, false);
        assertEquals(FlowState.FLOW, r1.state());
        assertEquals(50, r1.flowDelta());

        // 第二次 dwellSeconds 仍=350 → FLOW, delta=0（去重）
        FlowDetectionResult r2 = service.evaluate(1L, 1, 350, false, false);
        assertEquals(FlowState.FLOW, r2.state());
        assertEquals(0, r2.flowDelta());
    }

    // ──────────────────────────────────────────────────────
    //  派生信号（2026-11-05）
    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("步骤前进发 STEP_ADVANCE，回退发 STEP_BACKTRACK，并带切换上下文")
    void testStepSwitchSignals() {
        FlowDetectionResult r1 = service.evaluate(1L, 1, 120, false, false);
        assertTrue(r1.signals().isEmpty());

        // 前进 1→2：STEP_ADVANCE，prevStep=1，prevDwell=120
        FlowDetectionResult r2 = service.evaluate(1L, 2, 0, false, false);
        assertTrue(r2.signals().contains(FlowSignal.STEP_ADVANCE));
        assertEquals(1, r2.prevStepNo());
        assertEquals(120, r2.prevDwellSeconds());

        // 回退 2→1：STEP_BACKTRACK
        FlowDetectionResult r3 = service.evaluate(1L, 1, 0, false, false);
        assertTrue(r3.signals().contains(FlowSignal.STEP_BACKTRACK));
        assertEquals(2, r3.prevStepNo());
    }

    @Test
    @DisplayName("同步骤 dwell 反复回落 >3 次 → 发一次 DWELL_STAGNATION（与回退分开记录）")
    void testDwellStagnationSignal() {
        service.evaluate(1L, 1, 0, false, false);
        // 同一步骤 dwell 回落 4 次（重新停留 4 次）
        FlowDetectionResult result = null;
        for (int i = 0; i < 4; i++) {
            service.evaluate(1L, 1, 100, false, false);
            result = service.evaluate(1L, 1, 10, false, false);  // 回落
        }
        assertNotNull(result);
        assertTrue(result.signals().contains(FlowSignal.DWELL_STAGNATION));
        assertFalse(result.signals().contains(FlowSignal.STEP_BACKTRACK));

        // 信号已发出，本步骤再次回落不重复发
        FlowDetectionResult again = service.evaluate(1L, 1, 5, false, false);
        assertFalse(again.signals().contains(FlowSignal.DWELL_STAGNATION));
    }

    @Test
    @DisplayName("ANXIOUS 首次进入发 BREATHING_PROMPT，持续焦躁不重复发；响应后发 BREATHING_RESPONDED")
    void testBreathingPromptLatch() {
        // 4 次回退 → repeatCount=4 > 3 → ANXIOUS
        FlowDetectionResult anxious = null;
        for (int i = 0; i < 4; i++) {
            service.evaluate(1L, 2, 10, false, false);
            anxious = service.evaluate(1L, 1, 10, false, false);
        }
        assertNotNull(anxious);
        assertEquals(FlowState.ANXIOUS, anxious.state());
        assertTrue(anxious.signals().contains(FlowSignal.BREATHING_PROMPT));

        // 持续焦躁：不重复发 prompt
        service.evaluate(1L, 2, 10, false, false);
        FlowDetectionResult still = service.evaluate(1L, 1, 10, false, false);
        assertFalse(still.signals().contains(FlowSignal.BREATHING_PROMPT));

        // 响应呼吸 → BREATHING_RESPONDED，repeatCount 清零
        FlowDetectionResult responded = service.evaluate(1L, 1, 10, false, true);
        assertEquals(FlowState.NORMAL, responded.state());
        assertTrue(responded.signals().contains(FlowSignal.BREATHING_RESPONDED));

        // 无未完结提醒时再次响应 → 不发 responded 信号
        FlowDetectionResult stray = service.evaluate(1L, 1, 10, false, true);
        assertFalse(stray.signals().contains(FlowSignal.BREATHING_RESPONDED));
    }
}
