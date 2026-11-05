package com.zhiyu.modules.craft;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.dto.SessionEventBatchReq;
import com.zhiyu.modules.craft.dto.SessionEventItem;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftSessionEvent;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionEventMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.SessionEventBatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 创作会话行为事件集成测试（2026-11-05 心流度量修复）。
 *
 * <p>覆盖：客户端批量上报（幂等/脱敏/状态门禁）、服务端派生事件
 * （步骤切换、焦躁双信号、结束钩子）、疑似离席「只标记不扣除」。
 *
 * <p>@SpringBootTest + H2，fixture 与 {@link CraftSessionServiceHeartbeatTest} 同型。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftSessionEventIngestTest {

    @Autowired
    private CraftSessionEventService eventService;
    @Autowired
    private CraftSessionService craftSessionService;
    @Autowired
    private CraftProjectMapper craftProjectMapper;
    @Autowired
    private CraftSessionMapper craftSessionMapper;
    @Autowired
    private CraftStepMapper craftStepMapper;
    @Autowired
    private CraftSessionEventMapper eventMapper;

    private static final Long USER_ID = 7201L;
    private static final Long OTHER_USER_ID = 7202L;
    private static final Long PROJECT_ID = 9301L;

    @BeforeEach
    void setUp() {
        insertProject(PROJECT_ID, "编织事件测试");
        insertStep(PROJECT_ID, 1, "准备");
        insertStep(PROJECT_ID, 2, "编织");
    }

    // ──────────────────────────────────────────────────────
    //  客户端批量上报
    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("批量上报：2 条合法事件 → accepted=2，step_leave 的 dwellSeconds 提取成列")
    void testIngest_HappyPath() {
        Long sessionId = createSession();

        SessionEventBatchResult result = eventService.ingest(USER_ID, sessionId, batch(
                item("visibility_change", null, 1L, Map.of("visible", false)),
                item("step_leave", 1, 2L, Map.of("dwellSeconds", 42))));

        assertEquals(2, result.getAccepted());
        assertEquals(0, result.getDeduped());
        assertEquals(0, result.getFailed());

        List<CraftSessionEvent> rows = eventsOf(sessionId);
        assertEquals(2, rows.size());
        CraftSessionEvent visibility = rows.stream()
                .filter(e -> e.getEventType().equals("visibility_change")).findFirst().orElseThrow();
        assertEquals(1L, visibility.getClientSeq());
        assertTrue(visibility.getContext().contains("\"visible\":false"));
        assertEquals(PROJECT_ID, visibility.getCraftProjectId());
        assertEquals(USER_ID, visibility.getUserId());

        CraftSessionEvent leave = rows.stream()
                .filter(e -> e.getEventType().equals("step_leave")).findFirst().orElseThrow();
        assertEquals(42, leave.getDwellSeconds());
        assertEquals(1, leave.getStepNo());
    }

    @Test
    @DisplayName("重复 client_seq 重放 → 计 deduped 不重复入库（幂等）")
    void testIngest_DuplicateSeqDeduped() {
        Long sessionId = createSession();
        SessionEventBatchReq req = batch(
                item("visibility_change", null, 1L, Map.of("visible", false)),
                item("audio_play", 1, 2L, Map.of("track", "rain")));

        SessionEventBatchResult first = eventService.ingest(USER_ID, sessionId, req);
        assertEquals(2, first.getAccepted());

        SessionEventBatchResult second = eventService.ingest(USER_ID, sessionId, req);
        assertEquals(0, second.getAccepted());
        assertEquals(2, second.getDeduped());

        assertEquals(2, eventsOf(sessionId).size());
    }

    @Test
    @DisplayName("未知事件类型 → 1001")
    void testIngest_UnknownType() {
        Long sessionId = createSession();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(item("typing_fast", null, 1L, null))));
        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("服务端派生类型（suspected_away）客户端上报 → 1001")
    void testIngest_ServerOnlyType() {
        Long sessionId = createSession();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(item("suspected_away", 1, 1L, null))));
        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("服务端证据类型（step_gate_blocked）客户端上报 → 1001（防伪造拒绝证据）")
    void testIngest_GateBlockedNotClientReportable() {
        Long sessionId = createSession();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(item("step_gate_blocked", 1, 1L, null))));
        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("context 含嵌套结构 → 1001（防自由文本入口）")
    void testIngest_NestedContext() {
        Long sessionId = createSession();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(
                        item("audio_play", 1, 1L, Map.of("meta", Map.of("note", "我想说的话"))))));
        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("context 字符串值超 64 字符 → 1001")
    void testIngest_LongStringContext() {
        Long sessionId = createSession();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(
                        item("audio_play", 1, 1L, Map.of("note", "x".repeat(65))))));
        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("非会话归属者上报 → 1003")
    void testIngest_NotOwner() {
        Long sessionId = createSession();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(OTHER_USER_ID, sessionId, batch(item("audio_play", 1, 1L, null))));
        assertEquals(1003, ex.getCode());
    }

    @Test
    @DisplayName("会话不存在 → 1004")
    void testIngest_SessionNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, 99999L, batch(item("audio_play", 1, 1L, null))));
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("已完成会话上报 → 3001")
    void testIngest_FinishedSession() {
        Long sessionId = createSession();
        craftSessionService.changeStatus(USER_ID, sessionId, "finish");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(item("audio_play", 1, 1L, null))));
        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("发生时刻超前服务端 5 分钟以上 → 计 failed 丢弃")
    void testIngest_FutureOccurredAt() {
        Long sessionId = createSession();
        SessionEventItem item = item("audio_play", 1, 1L, null);
        item.setOccurredAt(LocalDateTime.now().plusMinutes(10).toString());

        SessionEventBatchResult result = eventService.ingest(USER_ID, sessionId, batch(item));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getFailed());
        assertTrue(eventsOf(sessionId).isEmpty());
    }

    @Test
    @DisplayName("occurredAt 无法解析 → 1001；带 Z 的 RFC3339 正常受理")
    void testIngest_OccurredAtParsing() {
        Long sessionId = createSession();

        // 无法解析 → 1001
        SessionEventItem bad = item("audio_play", 1, 1L, null);
        bad.setOccurredAt("昨天下午三点");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.ingest(USER_ID, sessionId, batch(bad)));
        assertEquals(1001, ex.getCode());

        // 带 Z 的 UTC RFC3339（前端 toISOString() 产物）→ 正常受理
        SessionEventItem utc = item("audio_play", 1, 2L, null);
        utc.setOccurredAt(java.time.Instant.now().toString());
        SessionEventBatchResult result = eventService.ingest(USER_ID, sessionId, batch(utc));
        assertEquals(1, result.getAccepted());
    }

    // ──────────────────────────────────────────────────────
    //  服务端派生事件
    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("心跳步骤切换 → step_leave（带最终停留）+ step_enter + step_advance；回退 → step_backtrack")
    void testHeartbeatDerivedEvents_StepSwitch() {
        Long sessionId = createSession();

        heartbeat(sessionId, 1, 50);
        heartbeat(sessionId, 2, 0);   // 前进 1→2
        heartbeat(sessionId, 1, 0);   // 回退 2→1

        List<CraftSessionEvent> rows = eventsOf(sessionId);
        assertEvent(rows, "step_leave", 1, 50);
        assertEvent(rows, "step_enter", 2, null);
        assertEvent(rows, "step_advance", 2, null);
        assertEvent(rows, "step_backtrack", 1, null);
    }

    @Test
    @DisplayName("同步骤 dwell 反复回落 >3 次 → dwell_stagnation 事件（与 step_backtrack 分开记录）")
    void testHeartbeatDerivedEvents_DwellStagnation() {
        Long sessionId = createSession();

        heartbeat(sessionId, 1, 0);
        for (int i = 0; i < 4; i++) {
            heartbeat(sessionId, 1, 100);
            heartbeat(sessionId, 1, 10);  // dwell 回落 = 重新停留
        }

        List<CraftSessionEvent> rows = eventsOf(sessionId);
        assertEvent(rows, "dwell_stagnation", 1, 10);
        assertEquals(0, rows.stream().filter(e -> e.getEventType().equals("step_backtrack")).count());
    }

    @Test
    @DisplayName("会话完成 → session_finish 结束事件（context 含 flowSeconds/currentStep）")
    void testSessionFinishHook() {
        Long sessionId = createSession();
        heartbeat(sessionId, 1, 0);
        heartbeat(sessionId, 1, 350);   // flowDelta=50

        craftSessionService.changeStatus(USER_ID, sessionId, "finish");

        List<CraftSessionEvent> rows = eventsOf(sessionId);
        CraftSessionEvent finish = rows.stream()
                .filter(e -> e.getEventType().equals("session_finish")).findFirst().orElseThrow();
        assertNull(finish.getStepNo());
        assertTrue(finish.getContext().contains("\"flowSeconds\":50"));
        assertTrue(finish.getContext().contains("\"currentStep\":1"));
    }

    @Test
    @DisplayName("会话放弃 → session_abandon 结束事件")
    void testSessionAbandonHook() {
        Long sessionId = createSession();
        craftSessionService.changeStatus(USER_ID, sessionId, "abandon");

        assertEvent(eventsOf(sessionId), "session_abandon", null, null);
    }

    @Test
    @DisplayName("疑似离席：远超 P95 且无窗口生命周期事件 → 标记 suspected_away，flow_seconds 不扣除")
    void testSuspectedAway_MarkOnlyNoDeduction() {
        // 30 个同类步骤停留样本（10..300s，P95=290，×2=580）
        for (int i = 1; i <= 30; i++) {
            insertStepLeaveEvent(PROJECT_ID, 1, i * 10);
        }
        Long sessionId = createSession();
        // 会话具备生命周期上报能力（事件在窗口外：8 倍 dwell 之前）
        insertLifecycleEvent(sessionId, LocalDateTime.now().minusSeconds(28800));

        heartbeat(sessionId, 1, 0);
        heartbeat(sessionId, 1, 3600);   // dwell=3600 > 580，窗口内无生命周期事件

        // 标记事件已写入
        assertEvent(eventsOf(sessionId), "suspected_away", 1, 3600);
        // 只标记不扣除：flow_seconds = 3600 - 300 = 3300（疑似离席不扣减）
        CraftSession session = craftSessionMapper.selectById(sessionId);
        assertEquals(3300, session.getFlowSeconds());

        // 同一停留窗口重复心跳不重复标记
        heartbeat(sessionId, 1, 3660);
        assertEquals(1, eventsOf(sessionId).stream()
                .filter(e -> e.getEventType().equals("suspected_away")).count());
    }

    @Test
    @DisplayName("窗口内有音频播放 → 算沉浸，不标记疑似离席")
    void testSuspectedAway_AudioInWindowSkips() {
        for (int i = 1; i <= 30; i++) {
            insertStepLeaveEvent(PROJECT_ID, 1, i * 10);
        }
        Long sessionId = createSession();
        insertLifecycleEvent(sessionId, LocalDateTime.now().minusSeconds(28800));
        // 窗口内（1 分钟前）有音频播放
        insertEvent(sessionId, "audio_play", LocalDateTime.now().minusSeconds(60));

        heartbeat(sessionId, 1, 0);
        heartbeat(sessionId, 1, 3600);

        assertEquals(0, eventsOf(sessionId).stream()
                .filter(e -> e.getEventType().equals("suspected_away")).count());
    }

    @Test
    @DisplayName("老客户端无生命周期上报 → 不标记（默认算沉浸）")
    void testSuspectedAway_LifecycleIncapableSkips() {
        for (int i = 1; i <= 30; i++) {
            insertStepLeaveEvent(PROJECT_ID, 1, i * 10);
        }
        Long sessionId = createSession();

        heartbeat(sessionId, 1, 0);
        heartbeat(sessionId, 1, 3600);

        assertEquals(0, eventsOf(sessionId).stream()
                .filter(e -> e.getEventType().equals("suspected_away")).count());
    }

    // ──────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────

    private Long createSession() {
        return craftSessionService.create(USER_ID, PROJECT_ID, null).getSessionId();
    }

    private void heartbeat(Long sessionId, int stepNo, int dwellSeconds) {
        HeartbeatReq req = new HeartbeatReq();
        req.setStepNo(stepNo);
        req.setDwellSeconds(dwellSeconds);
        craftSessionService.heartbeat(USER_ID, sessionId, req);
    }

    private SessionEventBatchReq batch(SessionEventItem... items) {
        SessionEventBatchReq req = new SessionEventBatchReq();
        req.setEvents(List.of(items));
        return req;
    }

    private SessionEventItem item(String type, Integer stepNo, Long clientSeq, Map<String, Object> context) {
        SessionEventItem item = new SessionEventItem();
        item.setEventType(type);
        item.setStepNo(stepNo);
        item.setOccurredAt(LocalDateTime.now().toString());
        item.setClientSeq(clientSeq);
        item.setContext(context);
        return item;
    }

    private List<CraftSessionEvent> eventsOf(Long sessionId) {
        LambdaQueryWrapper<CraftSessionEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftSessionEvent::getSessionId, sessionId);
        return eventMapper.selectList(wrapper);
    }

    private void assertEvent(List<CraftSessionEvent> rows, String type, Integer stepNo, Integer dwellSeconds) {
        CraftSessionEvent event = rows.stream()
                .filter(e -> e.getEventType().equals(type))
                .filter(e -> stepNo == null ? e.getStepNo() == null : stepNo.equals(e.getStepNo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "缺少事件: type=" + type + ", stepNo=" + stepNo + ", rows=" + rows.size()));
        if (dwellSeconds != null) {
            assertEquals(dwellSeconds, event.getDwellSeconds(), "事件 dwellSeconds 不符: " + type);
        }
    }

    private void insertStepLeaveEvent(Long projectId, int stepNo, int dwellSeconds) {
        CraftSessionEvent event = new CraftSessionEvent();
        event.setSessionId(-1L);
        event.setUserId(USER_ID);
        event.setCraftProjectId(projectId);
        event.setEventType(CraftSessionEventType.STEP_LEAVE.getValue());
        event.setStepNo(stepNo);
        event.setOccurredAt(LocalDateTime.now().minusDays(1));
        event.setDwellSeconds(dwellSeconds);
        eventMapper.insert(event);
    }

    private void insertLifecycleEvent(Long sessionId, LocalDateTime occurredAt) {
        insertEvent(sessionId, "visibility_change", occurredAt);
    }

    private void insertEvent(Long sessionId, String type, LocalDateTime occurredAt) {
        CraftSessionEvent event = new CraftSessionEvent();
        event.setSessionId(sessionId);
        event.setUserId(USER_ID);
        event.setCraftProjectId(PROJECT_ID);
        event.setEventType(type);
        event.setOccurredAt(occurredAt);
        eventMapper.insert(event);
    }

    private void insertProject(Long id, String title) {
        CraftProject p = new CraftProject();
        p.setId(id);
        p.setInheritorId(1L);
        p.setTitle(title);
        p.setLevel(1);
        p.setEstMinutes(30);
        p.setCategory("其他");
        p.setTherapyTags("专注");
        p.setStatus(1);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(0);
        craftProjectMapper.insert(p);
    }

    private void insertStep(Long projectId, int stepNo, String title) {
        CraftStep step = new CraftStep();
        step.setProjectId(projectId);
        step.setStepNo(stepNo);
        step.setTitle(title);
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setDeleted(0);
        craftStepMapper.insert(step);
    }
}
