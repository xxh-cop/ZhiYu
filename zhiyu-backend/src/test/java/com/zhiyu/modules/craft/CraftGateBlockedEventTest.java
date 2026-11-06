package com.zhiyu.modules.craft;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminService;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.service.impl.CraftSessionServiceImpl;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 心跳被下架闸口拒绝时落 step_gate_blocked 事件（2026-11-05）。
 *
 * <p>覆盖：首次被拒落一条、重复被拒幂等不刷行、3001 对外语义不变、
 * 事件落库失败不影响被拒响应（Mockito 构造，mock 事件服务抛异常）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CraftGateBlockedEventTest {

    @Autowired
    private CraftSessionService craftSessionService;
    @Autowired
    private AdminService adminService;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN = 990001L;
    private static final long INHERITOR = 990002L;
    private static final long STUDENT = 990003L;
    private static final long PROJECT = 990100L;

    private Long sessionId;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", ADMIN, "gb_admin", "管理员", 3);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR, "gb_inh", "传承人", 1);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", STUDENT, "gb_stu", "学生", 0);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'扎染',1,NOW(),NOW(),0)", PROJECT, INHERITOR, "闸口事件项目");
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(990300,?,1,'步骤一','引导',NOW(),NOW(),0)", PROJECT);
        sessionId = craftSessionService.create(STUDENT, PROJECT, null).getSessionId();
        UserContext.set(new LoginUser(ADMIN, 3));
        adminService.takedownProject(PROJECT, "内容需复核", "compliance", null);
        UserContext.set(new LoginUser(STUDENT, 0));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private HeartbeatReq req() {
        HeartbeatReq r = new HeartbeatReq();
        r.setStepNo(1);
        r.setDwellSeconds(60);
        r.setIsExited(false);
        r.setIsBreathingResponded(true);
        return r;
    }

    @Test
    @DisplayName("首次被拒落一条 step_gate_blocked（step_no/context 齐全）；重复被拒幂等不刷行；响应仍 3001")
    void gateRejectionWritesEventOnce() {
        // 首次被拒：3001 + 落事件
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> craftSessionService.heartbeat(STUDENT, sessionId, req())).getCode());
        var rows = jdbc.queryForList(
                "SELECT event_type,step_no,context FROM craft_session_event"
                        + " WHERE session_id=? AND event_type='step_gate_blocked' AND deleted=0", sessionId);
        assertEquals(1, rows.size(), "首次被拒落一条证据事件");
        assertEquals(1, ((Number) rows.get(0).get("step_no")).intValue());
        assertTrue(String.valueOf(rows.get(0).get("context")).contains("takedown"), "context 带闸口语义");

        // 重复被拒：仍 3001，不刷行（幂等）
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> craftSessionService.heartbeat(STUDENT, sessionId, req())).getCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM craft_session_event WHERE session_id=? AND event_type='step_gate_blocked' AND deleted=0",
                Integer.class, sessionId), "重复拒绝不刷行");
    }

    @Test
    @DisplayName("事件落库失败不影响被拒响应：mock 事件服务抛异常，仍抛 3001 而非 5000")
    void eventFailureKeepsRejection() {
        // 纯 Mockito 构造（不起 Spring）：事件服务写库即炸
        var sessionMapper = Mockito.mock(com.zhiyu.modules.craft.mapper.CraftSessionMapper.class);
        var projectMapper = Mockito.mock(com.zhiyu.modules.craft.mapper.CraftProjectMapper.class);
        var eventService = Mockito.mock(com.zhiyu.modules.craft.service.CraftSessionEventService.class);
        var jdbc = Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);

        CraftSession session = new CraftSession();
        session.setId(990400L);
        session.setUserId(STUDENT);
        session.setProjectId(PROJECT);
        session.setStatus(1); // DOING
        session.setCurrentStep(1);
        Mockito.when(sessionMapper.selectById(990400L)).thenReturn(session);

        CraftProject project = new CraftProject();
        project.setId(PROJECT);
        project.setStatus(2);
        project.setTakedownKind("compliance");
        project.setTakedownAt(LocalDateTime.now().minusHours(1));
        project.setExportUntil(LocalDateTime.now().plusHours(71));
        Mockito.when(projectMapper.selectById(PROJECT)).thenReturn(project);

        Mockito.when(jdbc.queryForObject(Mockito.contains("step_gate_blocked"),
                Mockito.eq(Integer.class), Mockito.any(Object[].class))).thenReturn(0);
        Mockito.doThrow(new RuntimeException("事件表炸了"))
                .when(eventService).recordServerEventStandalone(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        var svc = new CraftSessionServiceImpl(sessionMapper, projectMapper, null, null,
                new TakedownProperties(), eventService, null, jdbc, null, null, null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.heartbeat(STUDENT, 990400L, req()));
        assertEquals(3001, ex.getCode(), "事件失败不得改变 3001 被拒语义");
        assertTrue(ex.getMessage().contains("只读"), ex.getMessage());
    }
}
