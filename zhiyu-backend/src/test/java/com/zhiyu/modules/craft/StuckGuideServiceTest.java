package com.zhiyu.modules.craft;

import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import com.zhiyu.modules.craft.service.impl.StuckGuideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link StuckGuideService} 单测（2026-11-05 卡点引导）。
 *
 * <p>覆盖：异常触发（dwell 反复回落 → 触点）/ 正常不触发 / 用户关闭后不触发 /
 * AI 失败降级静态模板 / 超时降级静态模板 / 频率上限（会话级、用户日级）。
 * executor 注入同步实现（Runnable::run）使「异步生成」在测试内确定性完成；
 * 超时路径注入不执行的 executor 让 future 永不就绪。
 */
class StuckGuideServiceTest {

    private static final Long USER = 7101L;
    private static final Long SESSION = 8801L;
    private static final Long PROJECT = 9201L;
    private static final String PROMPT_TPL = "引导 {projectTitle} 第 {stepNo} 步";

    private JdbcTemplate jdbc;
    private AiClient aiClient;
    private CraftSessionEventService eventService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        aiClient = mock(AiClient.class);
        eventService = mock(CraftSessionEventService.class);
        // 默认：开关开、频率计数为 0
        when(jdbc.queryForObject(contains("stuck_guide_enabled"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("COUNT"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
    }

    private StuckGuideService service(Executor executor, int sessionMax, int dailyMax, long timeoutMs) {
        return new StuckGuideService(jdbc, aiClient, eventService, PROMPT_TPL,
                executor, sessionMax, dailyMax, timeoutMs);
    }

    private CraftSession session() {
        CraftSession s = new CraftSession();
        s.setId(SESSION);
        s.setUserId(USER);
        s.setProjectId(PROJECT);
        return s;
    }

    @Test
    @DisplayName("异常触发：dwell 反复回落 → 触点事件落库 + 下一拍取回 AI 文案（prompt 已渲染）")
    void triggerDeliversAiText() {
        when(aiClient.stuckGuide(eq(USER), anyString())).thenReturn("可以先看一眼示例，慢慢来。");
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);

        svc.onStagnation(session(), 3, "编织杯垫");

        verify(eventService).recordServerEvent(eq(SESSION), eq(USER), eq(PROJECT),
                eq(CraftSessionEventType.STUCK_GUIDE_SHOWN), eq(3), any(), isNull());
        String text = svc.poll(SESSION);
        assertEquals("可以先看一眼示例，慢慢来。", text);
        // prompt 渲染：项目名与步骤序号已代入，不含裸占位符
        verify(aiClient).stuckGuide(eq(USER), argThat(p ->
                p.contains("编织杯垫") && p.contains("3") && !p.contains("{projectTitle}")));
        // 消费后不再重复下发
        assertNull(svc.poll(SESSION));
    }

    @Test
    @DisplayName("正常不触发：无 onStagnation 调用时 poll 恒为 null")
    void noSignalNoGuide() {
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);
        assertNull(svc.poll(SESSION));
        verifyNoInteractions(aiClient);
    }

    @Test
    @DisplayName("用户关闭后不触发：stuck_guide_enabled=0 → 无事件、不调 AI、不下发")
    void disabledUserNoTrigger() {
        when(jdbc.queryForObject(contains("stuck_guide_enabled"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);

        svc.onStagnation(session(), 3, "编织杯垫");

        verifyNoInteractions(aiClient);
        verify(eventService, never()).recordServerEvent(any(), any(), any(), any(), any(), any(), any());
        assertNull(svc.poll(SESSION));
    }

    @Test
    @DisplayName("AI 失败降级：stuckGuide 返回 null → 下发静态模板文案（非空、非诊断）")
    void aiFailureFallsBackToTemplate() {
        when(aiClient.stuckGuide(eq(USER), anyString())).thenReturn(null);
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);

        svc.onStagnation(session(), 3, "编织杯垫");

        assertEquals(StuckGuideService.TEMPLATE, svc.poll(SESSION));
    }

    @Test
    @DisplayName("生成超时降级：超过 async-timeout 未就绪 → 下发静态模板；未超时前不下发")
    void timeoutFallsBackToTemplate() {
        Executor neverRun = r -> { /* future 永不就绪 */ };
        // 未超时：timeout 很大 → poll null
        StuckGuideService waiting = service(neverRun, 2, 3, 60_000);
        waiting.onStagnation(session(), 3, "编织杯垫");
        assertNull(waiting.poll(SESSION));

        // 已超时：timeout=0 → 直接落模板
        StuckGuideService timedOut = service(neverRun, 2, 3, 0);
        timedOut.onStagnation(session(), 3, "编织杯垫");
        assertEquals(StuckGuideService.TEMPLATE, timedOut.poll(SESSION));
    }

    @Test
    @DisplayName("频率上限·会话级：本会话 stuck_guide_shown 计数达上限 → 不再触发")
    void sessionCapBlocks() {
        when(jdbc.queryForObject(contains("session_id"), eq(Long.class), any(Object[].class)))
                .thenReturn(2L);
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);

        svc.onStagnation(session(), 3, "编织杯垫");

        verifyNoInteractions(aiClient);
        verify(eventService, never()).recordServerEvent(any(), any(), any(), any(), any(), any(), any());
        assertNull(svc.poll(SESSION));
    }

    @Test
    @DisplayName("频率上限·用户日级：今日全局限额用完 → 不再触发")
    void dailyCapBlocks() {
        when(jdbc.queryForObject(contains("user_id"), eq(Long.class), any(Object[].class)))
                .thenReturn(3L);
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);

        svc.onStagnation(session(), 3, "编织杯垫");

        verifyNoInteractions(aiClient);
        verify(eventService, never()).recordServerEvent(any(), any(), any(), any(), any(), any(), any());
        assertNull(svc.poll(SESSION));
    }

    @Test
    @DisplayName("开关设置：setEnabled 写 user 表（永久关闭/开启的后端落点）")
    void settingWritesUserColumn() {
        StuckGuideService svc = service(Runnable::run, 2, 3, 5000);
        svc.setEnabled(USER, false);
        verify(jdbc).update(contains("stuck_guide_enabled"), eq(0), eq(USER));
        svc.setEnabled(USER, true);
        verify(jdbc).update(contains("stuck_guide_enabled"), eq(1), eq(USER));
    }
}
