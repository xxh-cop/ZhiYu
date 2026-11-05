package com.zhiyu.modules.craft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminService;
import com.zhiyu.modules.craft.controller.CraftController;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 下架窗口导出端点（2026-11-05）闭环测试。
 *
 * <p>覆盖：窗口内成功（附件头 + 载荷内容 + export 事件落库）、窗口过期 3001、
 * 非本人 1003（与既有会话端点口径一致）、未登录 1002、export-enabled=false 明确拒绝、
 * 上架项目不在窗口 3001、会话不存在 1004。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CraftSessionExportTest {

    @Autowired
    private CraftController craftController;
    @Autowired
    private CraftSessionService craftSessionService;
    @Autowired
    private AdminService adminService;
    @Autowired
    private TakedownProperties takedownProperties;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    private static final long ADMIN = 970001L;
    private static final long INHERITOR = 970002L;
    private static final long STUDENT = 970003L;
    private static final long OTHER = 970004L;
    private static final long PROJECT = 970100L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long sessionId;

    @BeforeEach
    void setup() {
        for (Object[] u : new Object[][]{
                {ADMIN, "ex_admin", 3}, {INHERITOR, "ex_inh", 1}, {STUDENT, "ex_stu", 0}, {OTHER, "ex_other", 0}}) {
            jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                    + " VALUES(?,?, 'unused',?,?,1,1,0,0)", u[0], u[1], u[2] == null ? null : "用户", u[2]);
        }
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'扎染',1,NOW(),NOW(),0)", PROJECT, INHERITOR, "窗口导出测试项目");
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(970300,?,1,'步骤一','引导',NOW(),NOW(),0)", PROJECT);
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(970301,?,2,'步骤二','引导',NOW(),NOW(),0)", PROJECT);
        sessionId = craftSessionService.create(STUDENT, PROJECT, null).getSessionId();
        // 该会话产出的作品 + 两条行为事件
        jdbc.update("INSERT INTO work(id,user_id,project_id,session_id,title,media_type,visible,anonymous,status,"
                + "create_time,update_time,deleted) VALUES(970400,?,?,?,'我的扎染','image',1,0,1,NOW(),NOW(),0)",
                STUDENT, PROJECT, sessionId);
        jdbc.update("INSERT INTO craft_session_event(id,session_id,user_id,craft_project_id,event_type,step_no,"
                + "occurred_at,client_seq,context,dwell_seconds,create_time,update_time,deleted)"
                + " VALUES(970500,?,?,?,'step_enter',1,NOW(),1,NULL,NULL,NOW(),NOW(),0)", sessionId, STUDENT, PROJECT);
        jdbc.update("INSERT INTO craft_session_event(id,session_id,user_id,craft_project_id,event_type,step_no,"
                + "occurred_at,client_seq,context,dwell_seconds,create_time,update_time,deleted)"
                + " VALUES(970501,?,?,?,'audio_play',1,NOW(),2,'{\"pos\":12}',NULL,NOW(),NOW(),0)", sessionId, STUDENT, PROJECT);
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private void complianceTakedown() {
        UserContext.set(new LoginUser(ADMIN, 3));
        adminService.takedownProject(PROJECT, "内容需复核", "compliance", null);
        UserContext.set(new LoginUser(STUDENT, 0));
    }

    @Test
    @DisplayName("窗口内导出成功：附件头/文件名/载荷内容齐全，export 事件落库且自证在导出文件里")
    void exportInWindowSuccess() throws Exception {
        complianceTakedown();
        ResponseEntity<byte[]> resp = craftController.exportSession(sessionId);

        // 附件头
        assertEquals(200, resp.getStatusCode().value());
        String disposition = resp.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"), "必须是附件下载: " + disposition);
        assertTrue(disposition.contains("zhiyu-craft-session-" + sessionId + "-p" + PROJECT + ".json"),
                "文件名含会话与项目标识: " + disposition);
        assertEquals("application/json;charset=UTF-8", resp.getHeaders().getFirst("Content-Type"));
        assertEquals("no-store", resp.getHeaders().getFirst("Cache-Control"));

        // 载荷
        JsonNode root = MAPPER.readTree(new String(resp.getBody(), StandardCharsets.UTF_8));
        assertEquals(1, root.get("version").intValue());
        assertEquals(String.valueOf(PROJECT), root.at("/project/id").asText());
        assertEquals("窗口导出测试项目", root.at("/project/title").asText());
        assertEquals("compliance", root.at("/project/takedownKind").asText());
        assertFalse(root.at("/exportUntil").isNull(), "响应含真实窗口截止");
        assertEquals(String.valueOf(sessionId), root.at("/session/id").asText());
        assertEquals(1, root.at("/session/currentStep").intValue());
        assertEquals(2, root.at("/session/totalSteps").intValue());
        assertTrue(root.at("/session").has("flowSeconds"), "心流快照字段在");

        // 事件：两条种子 + 本次导出自证（先记事件再装配）
        JsonNode events = root.get("events");
        assertEquals(3, events.size(), "事件含种子两条 + export 自证: " + events);
        assertEquals("step_enter", events.get(0).get("eventType").asText());
        assertEquals("audio_play", events.get(1).get("eventType").asText());
        assertEquals(12, events.get(1).at("/context/pos").intValue(), "context 还成 JSON 值");
        assertEquals("export", events.get(2).get("eventType").asText());

        // 作品标识（仅标识，无媒体内容）
        JsonNode works = root.get("works");
        assertEquals(1, works.size());
        assertEquals("970400", works.get(0).get("id").asText());
        assertEquals("我的扎染", works.get(0).get("title").asText());
        assertFalse(works.get(0).has("coverUrl"), "不导出作品内容字段");

        // 事件落库（不静默）
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM craft_session_event WHERE session_id=? AND event_type='export' AND deleted=0",
                Integer.class, sessionId));
    }

    @Test
    @DisplayName("窗口过期 → 3001（报文带窗口关闭时间，不用 1004 伪装）")
    void exportWindowExpired3001() {
        complianceTakedown();
        jdbc.update("UPDATE craft_project SET export_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(1)), PROJECT);
        // JdbcTemplate 直写绕过 MyBatis 会话缓存，清缓存再读
        sqlSessionTemplate.clearCache();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.exportSession(STUDENT, sessionId));
        assertEquals(3001, ex.getCode());
        assertTrue(ex.getMessage().contains("导出窗口已于"), ex.getMessage());
    }

    @Test
    @DisplayName("非本人 → 1003（与既有会话端点口径一致）；会话不存在 → 1004")
    void exportOwnership() {
        complianceTakedown();
        assertEquals(1003, assertThrows(BusinessException.class,
                () -> craftSessionService.exportSession(OTHER, sessionId)).getCode(), "非本人 1003");
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> craftSessionService.exportSession(STUDENT, 979999L)).getCode(), "不存在 1004");
    }

    @Test
    @DisplayName("未登录 → 1002（access.user() 口径，与 data-export 一致）")
    void exportNotLoggedIn1002() {
        complianceTakedown();
        UserContext.clear();
        assertEquals(1002, assertThrows(BusinessException.class,
                () -> craftController.exportSession(sessionId)).getCode());
    }

    @Test
    @DisplayName("export-enabled=false → 3001「导出功能暂未开放」（明确拒绝，不假装成功）")
    void exportDisabledRejected() {
        complianceTakedown();
        takedownProperties.setExportEnabled(false);
        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> craftSessionService.exportSession(STUDENT, sessionId));
            assertEquals(3001, ex.getCode());
            assertTrue(ex.getMessage().contains("暂未开放"), ex.getMessage());
        } finally {
            takedownProperties.setExportEnabled(true);
        }
    }

    @Test
    @DisplayName("上架项目（无下架窗口）→ 3001「不在可导出窗口内」；运营下架同理")
    void exportLiveProjectRejected() {
        // 未下架：不在窗口
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> craftSessionService.exportSession(STUDENT, sessionId)).getCode());
        // 运营下架：无导出窗口（存量会话可继续，但导出仅合规窗口）
        UserContext.set(new LoginUser(ADMIN, 3));
        adminService.takedownProject(PROJECT, "运营调整", "operational", LocalDateTime.now().plusDays(10));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> craftSessionService.exportSession(STUDENT, sessionId));
        assertEquals(3001, ex.getCode());
        assertTrue(ex.getMessage().contains("不在可导出窗口"), ex.getMessage());
    }
}
