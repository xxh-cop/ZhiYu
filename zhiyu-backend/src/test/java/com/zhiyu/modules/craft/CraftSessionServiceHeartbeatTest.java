package com.zhiyu.modules.craft;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.HeartbeatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CraftSessionService#heartbeat} 集成测试（T1.7 子步骤 3）。
 *
 * <p>@SpringBootTest + H2 + InMemoryFlowStatStore（test profile 默认 store=memory）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftSessionServiceHeartbeatTest {

    @Autowired
    private CraftSessionService craftSessionService;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftSessionMapper craftSessionMapper;

    @Autowired
    private CraftStepMapper craftStepMapper;

    private static final Long USER_ID = 7101L;
    private static final Long OTHER_USER_ID = 7102L;
    private static final Long PROJECT_ID = 9201L;

    @BeforeEach
    void setUp() {
        insertProject(PROJECT_ID, "编织测试");
        insertStep(PROJECT_ID, 1, "准备");
        insertStep(PROJECT_ID, 2, "编织");
        insertStep(PROJECT_ID, 3, "收尾");
    }

    @Test
    @DisplayName("心跳：非 owner → 1003")
    void testHeartbeat_NotOwner() {
        Long sessionId = createSession();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> heartbeat(OTHER_USER_ID, sessionId, 1, 50, false, false));
        assertEquals(1003, ex.getCode());
    }

    @Test
    @DisplayName("心跳：会话不存在 → 1004")
    void testHeartbeat_SessionNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> heartbeat(USER_ID, 99999L, 1, 50, false, false));
        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("心跳：PAUSED 状态拒绝 → 3001")
    void testHeartbeat_PausedRejected() {
        Long sessionId = createSession();
        craftSessionService.changeStatus(USER_ID, sessionId, "pause");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> heartbeat(USER_ID, sessionId, 1, 50, false, false));
        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("心跳：FINISHED 状态拒绝 → 3001")
    void testHeartbeat_FinishedRejected() {
        Long sessionId = createSession();
        craftSessionService.changeStatus(USER_ID, sessionId, "finish");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> heartbeat(USER_ID, sessionId, 1, 50, false, false));
        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("心跳：正常心跳 → 200 + inFlow=false, needBreathing=false")
    void testHeartbeat_NormalFlow() {
        Long sessionId = createSession();

        HeartbeatResult result = heartbeat(USER_ID, sessionId, 1, 50, false, false);

        assertFalse(result.isInFlow());
        assertFalse(result.isNeedBreathing());
    }

    @Test
    @DisplayName("心跳：dwellSeconds=301 → inFlow=true, flow_seconds 累加 301")
    void testHeartbeat_FlowAccumulates() {
        Long sessionId = createSession();

        // 首次心跳初始化（stepNo=1, dwellSeconds=0）
        heartbeat(USER_ID, sessionId, 1, 0, false, false);

        // 第二次心跳 dwellSeconds=301 → FLOW, delta=301
        HeartbeatResult result = heartbeat(USER_ID, sessionId, 1, 301, false, false);

        assertTrue(result.isInFlow());
        assertFalse(result.isNeedBreathing());

        // 验证 flow_seconds 已累加
        CraftSession session = craftSessionMapper.selectById(sessionId);
        assertEquals(301, session.getFlowSeconds());
    }

    // ──────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────

    private Long createSession() {
        return craftSessionService.create(USER_ID, PROJECT_ID).getSessionId();
    }

    private HeartbeatResult heartbeat(Long userId, Long sessionId,
                                      int stepNo, int dwellSeconds,
                                      Boolean isExited, Boolean isBreathingResponded) {
        HeartbeatReq req = new HeartbeatReq();
        req.setStepNo(stepNo);
        req.setDwellSeconds(dwellSeconds);
        req.setIsExited(isExited);
        req.setIsBreathingResponded(isBreathingResponded);
        return craftSessionService.heartbeat(userId, sessionId, req);
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
