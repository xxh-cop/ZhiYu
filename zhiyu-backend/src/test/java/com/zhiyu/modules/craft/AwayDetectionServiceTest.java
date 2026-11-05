package com.zhiyu.modules.craft;

import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.mapper.CraftSessionEventMapper;
import com.zhiyu.modules.craft.service.impl.AwayDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AwayDetectionService} 单元测试（2026-11-05 心流度量修复）。
 *
 * <p>口径：不要求输入事件；强信号走生命周期；前台静置默认算沉浸；
 * 只标记不扣除。阈值经反射注入（p95Multiplier=2.0, minSamples=30,
 * floorSeconds=300, sampleLimit=500）。
 */
class AwayDetectionServiceTest {

    private CraftSessionEventMapper eventMapper;
    private AwayDetectionService service;

    private static final Long SESSION_ID = 8801L;
    private static final Long PROJECT_ID = 9901L;

    @BeforeEach
    void setUp() throws Exception {
        eventMapper = mock(CraftSessionEventMapper.class);
        service = new AwayDetectionService(eventMapper);
        setField("p95Multiplier", 2.0);
        setField("minSamples", 30);
        setField("floorSeconds", 300);
        setField("sampleLimit", 500);
    }

    private void setField(String name, Object value) throws Exception {
        var field = AwayDetectionService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private CraftSession session() {
        CraftSession s = new CraftSession();
        s.setId(SESSION_ID);
        s.setProjectId(PROJECT_ID);
        return s;
    }

    /** 造 n 个 step_leave 停留样本：1..n 秒。 */
    private List<Integer> dwellSamples(int n) {
        List<Integer> values = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            values.add(i * 10);
        }
        return values;
    }

    @Test
    @DisplayName("停留低于绝对地板（<300s）→ 不判定")
    void testBelowFloor_NotAway() {
        boolean result = service.isSuspectedAway(session(), 1, 299);
        assertFalse(result);
        verifyNoInteractions(eventMapper);
    }

    @Test
    @DisplayName("P95 样本不足（<30）→ 不判定（冷启动期不标记）")
    void testInsufficientSamples_NotAway() {
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(1), anyInt()))
                .thenReturn(dwellSamples(10));

        assertFalse(service.isSuspectedAway(session(), 1, 3600));
        assertNull(service.estimateP95(PROJECT_ID, 1));
    }

    @Test
    @DisplayName("停留未超过 P95 × 2 → 默认算沉浸，不标记")
    void testBelowP95Multiplier_NotAway() {
        // 样本 1..30 的 P95 = 290（nearest-rank：ceil(0.95*30)=29 → 第 29 个值）
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(1), anyInt()))
                .thenReturn(dwellSamples(30));

        assertEquals(290.0, service.estimateP95(PROJECT_ID, 1));
        // P95×2 = 580；dwell=580 不超 → 不标记；dwell=581 超 → 进入后续判定
        assertFalse(service.isSuspectedAway(session(), 1, 580));
    }

    @Test
    @DisplayName("老客户端无生命周期上报能力 → 不标记（不误读未上报为静置）")
    void testLifecycleIncapable_NotAway() {
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(1), anyInt()))
                .thenReturn(dwellSamples(30));
        when(eventMapper.countLifecycleEvents(SESSION_ID)).thenReturn(0L);

        assertFalse(service.isSuspectedAway(session(), 1, 3600));
    }

    @Test
    @DisplayName("窗口内有生命周期/音频强信号 → 算沉浸，不标记")
    void testActivityInWindow_NotAway() {
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(1), anyInt()))
                .thenReturn(dwellSamples(30));
        when(eventMapper.countLifecycleEvents(SESSION_ID)).thenReturn(1L);
        when(eventMapper.countActivityInWindow(eq(SESSION_ID), any())).thenReturn(1L);

        assertFalse(service.isSuspectedAway(session(), 1, 3600));
    }

    @Test
    @DisplayName("同一停留窗口已标记 → 不重复标记")
    void testAlreadyMarked_NotAway() {
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(1), anyInt()))
                .thenReturn(dwellSamples(30));
        when(eventMapper.countLifecycleEvents(SESSION_ID)).thenReturn(1L);
        when(eventMapper.countActivityInWindow(eq(SESSION_ID), any())).thenReturn(0L);
        when(eventMapper.countAwayMarkedInWindow(eq(SESSION_ID), eq(1), any())).thenReturn(1L);

        assertFalse(service.isSuspectedAway(session(), 1, 3600));
    }

    @Test
    @DisplayName("远超 P95 且页面可见但无任何生命周期事件 → 标记疑似离席（只标记不扣除）")
    void testSuspectedAway_Marks() {
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(1), anyInt()))
                .thenReturn(dwellSamples(30));
        when(eventMapper.countLifecycleEvents(SESSION_ID)).thenReturn(2L);
        when(eventMapper.countActivityInWindow(eq(SESSION_ID), any())).thenReturn(0L);
        when(eventMapper.countAwayMarkedInWindow(eq(SESSION_ID), eq(1), any())).thenReturn(0L);

        assertTrue(service.isSuspectedAway(session(), 1, 3600));
    }

    @Test
    @DisplayName("P95 估计：样本乱序也按 nearest-rank 取值")
    void testEstimateP95_NearestRank() {
        List<Integer> shuffled = dwellSamples(40);
        Collections.shuffle(shuffled);
        when(eventMapper.selectStepLeaveDwells(eq(PROJECT_ID), eq(2), anyInt()))
                .thenReturn(shuffled);

        // ceil(0.95*40)=38 → 排序后第 38 个值 = 380
        assertEquals(380.0, service.estimateP95(PROJECT_ID, 2));
    }
}
