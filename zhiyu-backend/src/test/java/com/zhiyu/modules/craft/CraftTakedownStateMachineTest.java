package com.zhiyu.modules.craft;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminService;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.takedown.CraftProjectArchiveWorker;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分层下架状态机（2026-11-05）服务级闭环测试。
 *
 * <p>覆盖：合规下架 happy path（只读 → 窗口内可导出 → 到期归档）、运营下架差异化行为
 * （存量会话不受影响 → 到期归档）、参数校验（缺类型/缺截止/截止太近/合规带截止）、
 * 心跳推进闸口（只读 3001）、归档 Worker（2→3）、配置校验（default-deadline-days ≥ 7）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CraftTakedownStateMachineTest {

    @Autowired
    private AdminService adminService;
    @Autowired
    private CraftProjectService craftProjectService;
    @Autowired
    private CraftSessionService craftSessionService;
    @Autowired
    private CraftProjectArchiveWorker archiveWorker;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private org.mybatis.spring.SqlSessionTemplate sqlSessionTemplate;

    private static final long ADMIN = 940001L;
    private static final long INHERITOR = 940002L;
    private static final long STUDENT = 940003L;
    private static final long PROJECT = 940100L;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", ADMIN, "td_admin", "管理员", 3);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR, "td_inh", "传承人", 1);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", STUDENT, "td_stu", "学生", 0);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',1,NOW(),NOW(),0)", PROJECT, INHERITOR, "分层下架测试项目");
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(940300,?,1,'步骤一','引导',NOW(),NOW(),0)", PROJECT);
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(940301,?,2,'步骤二','引导',NOW(),NOW(),0)", PROJECT);
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private Long createDoingSession() {
        UserContext.set(new LoginUser(STUDENT, 0));
        Long sessionId = craftSessionService.create(STUDENT, PROJECT, null).getSessionId();
        UserContext.set(new LoginUser(ADMIN, 3));
        return sessionId;
    }

    private HeartbeatReq heartbeatReq() {
        HeartbeatReq req = new HeartbeatReq();
        req.setStepNo(1);
        req.setDwellSeconds(60);
        req.setIsExited(false);
        req.setIsBreathingResponded(true);
        return req;
    }

    // ── 合规下架 happy path ──

    @Test
    @DisplayName("合规下架：进行中会话转只读（可回看、窗口内可导出、心跳推进 3001、可 finish 退出）")
    void complianceReadOnlyThenExportWindow() {
        Long sessionId = createDoingSession();
        adminService.takedownProject(PROJECT, "内容需复核", "compliance", null);

        // 导出窗口 ≈ 下架时刻 + 72h
        var row = jdbc.queryForMap("SELECT takedown_at,export_until FROM craft_project WHERE id=?", PROJECT);
        LocalDateTime takenAt = ((Timestamp) row.get("takedown_at")).toLocalDateTime();
        LocalDateTime exportUntil = ((Timestamp) row.get("export_until")).toLocalDateTime();
        assertEquals(72, java.time.Duration.between(takenAt, exportUntil).toHours(), "导出窗口默认 72 小时");

        UserContext.set(new LoginUser(STUDENT, 0));
        var view = craftProjectService.listSteps(PROJECT);
        assertEquals(2, view.getProjectStatus());
        assertTrue(view.getReadOnly());
        assertTrue(view.getCanReview());
        assertTrue(view.getCanExport());
        assertEquals(2, view.getSteps().size(), "历史步骤可回看");
        assertEquals(sessionId, view.getSessionId(), "应带出当前用户最近会话");
        assertEquals(1, view.getSessionStatus(), "会话仍处进行中");
        assertEquals("craft-takedown-compliance", view.getNoticeKey());
        assertTrue(view.getNotice().contains("分层下架测试项目"), "提示语应含项目标题: " + view.getNotice());
        assertFalse(view.getNotice().contains("违规"), "提示语不得带指向性措辞");

        // 只读：心跳推进被拒 3001
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> craftSessionService.heartbeat(STUDENT, sessionId, heartbeatReq())).getCode(),
                "只读会话不得推进步骤");
        // 出口：finish 仍允许（不推进步骤）
        assertDoesNotThrow(() -> craftSessionService.changeStatus(STUDENT, sessionId, "finish"));
    }

    @Test
    @DisplayName("合规下架窗口过期：读路径按归档下发（不可导出）；归档 Worker 落 2→3")
    void complianceWindowExpiryArchives() {
        createDoingSession();
        adminService.takedownProject(PROJECT, "内容需复核", "compliance", null);
        // 窗口拉到过去
        jdbc.update("UPDATE craft_project SET export_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(1)), PROJECT);

        UserContext.set(new LoginUser(STUDENT, 0));
        var view = craftProjectService.listSteps(PROJECT);
        assertEquals(3, view.getProjectStatus(), "窗口已过应按归档下发");
        assertTrue(view.getReadOnly());
        assertFalse(view.getCanExport(), "窗口关闭不可导出");
        assertEquals("craft-archived", view.getNoticeKey());
        assertNotNull(view.getArchiveAt(), "归档时间应用窗口截止推导");
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> craftSessionService.heartbeat(STUDENT,
                        jdbc.queryForObject("SELECT id FROM craft_session WHERE user_id=? AND project_id=?",
                                Long.class, STUDENT, PROJECT),
                        heartbeatReq())).getCode(), "窗口过期后仍尝试推进步骤应被拒");

        int archived = archiveWorker.archiveDueProjects();
        assertEquals(1, archived);
        var row = jdbc.queryForMap("SELECT status,archive_at,notice_key FROM craft_project WHERE id=?", PROJECT);
        assertEquals(3, ((Number) row.get("status")).intValue());
        assertNotNull(row.get("archive_at"), "合规归档回填 archive_at=export_until");
        assertEquals("craft-archived", row.get("notice_key"));
        // 幂等：再扫一轮无事发生
        assertEquals(0, archiveWorker.archiveDueProjects());
    }

    // ── 运营下架 ──

    @Test
    @DisplayName("运营下架：存量会话不受影响（可推进）；到期归档转只读")
    void operationalKeepsSessionsUntilDeadline() {
        Long sessionId = createDoingSession();
        LocalDateTime deadline = LocalDateTime.now().plusDays(10);
        adminService.takedownProject(PROJECT, "运营调整", "operational", deadline);

        UserContext.set(new LoginUser(STUDENT, 0));
        var view = craftProjectService.listSteps(PROJECT);
        assertEquals(2, view.getProjectStatus());
        assertFalse(view.getReadOnly(), "运营下架到期前会话不受影响");
        assertFalse(view.getCanExport(), "运营下架无导出窗口");
        assertEquals("craft-takedown-operational", view.getNoticeKey());
        assertNotNull(view.getArchiveAt());
        assertTrue(view.getNotice().contains("分层下架测试项目"));
        // 会话仍可推进
        assertDoesNotThrow(() -> craftSessionService.heartbeat(STUDENT, sessionId, heartbeatReq()));

        // 到期：读路径即按归档下发，Worker 落库
        jdbc.update("UPDATE craft_project SET archive_at=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)), PROJECT);
        // MyBatis 会话一级缓存不含 JdbcTemplate 直写：同事务内先 selectById 过的实体会读旧值，
        // 清缓存后再走 listSteps（生产请求各自独立 SqlSession，无此问题）
        sqlSessionTemplate.clearCache();
        var expired = craftProjectService.listSteps(PROJECT);
        assertEquals(3, expired.getProjectStatus());
        assertTrue(expired.getReadOnly());
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> craftSessionService.heartbeat(STUDENT, sessionId, heartbeatReq())).getCode());
        assertEquals(1, archiveWorker.archiveDueProjects());
        assertEquals(3, jdbc.queryForObject("SELECT status FROM craft_project WHERE id=?", Integer.class, PROJECT));
    }

    @Test
    @DisplayName("运营下架参数校验：未填截止时间 → 1001；截止距现在不足 7 天 → 1001；合规带截止 → 1001")
    void operationalValidation() {
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(PROJECT, "理由", "operational", null)).getCode(),
                "运营下架未填截止时间应被拒");
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(PROJECT, "理由", "operational",
                        LocalDateTime.now().plusDays(3))).getCode(), "截止不足 7 天应被拒");
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(PROJECT, "理由", "compliance",
                        LocalDateTime.now().plusDays(10))).getCode(), "合规下架不得指定归档时间");
        // 校验失败不落库
        assertEquals(1, jdbc.queryForObject("SELECT status FROM craft_project WHERE id=?", Integer.class, PROJECT));
    }

    @Test
    @DisplayName("下架类型大小写归一：Compliance 等同 compliance")
    void kindNormalized() {
        assertDoesNotThrow(() -> adminService.takedownProject(PROJECT, "理由", " Compliance ", null));
        assertEquals("compliance", jdbc.queryForObject(
                "SELECT takedown_kind FROM craft_project WHERE id=?", String.class, PROJECT));
    }

    @Test
    @DisplayName("配置校验：operational.default-deadline-days 小于 7 被 Bean Validation 拒绝（启动绑定期同口径）")
    void configRejectsBelowSevenDays() {
        var props = new TakedownProperties();
        props.getOperational().setDefaultDeadlineDays(3);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(props).isEmpty(), "小于 7 天必须被校验拒绝");
        }
        props.getOperational().setDefaultDeadlineDays(7);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(props).isEmpty(), "7 天是允许的最小值");
        }
    }
}
