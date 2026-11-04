package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminService;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.inheritor.service.InheritorProjectService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * AY（2026-11-05）：项目下架与删除的服务级闭环测试。
 *
 * <p>覆盖：下架成功（状态/理由/审计/通知）、非法状态流转（3001）、下架后学生端与
 * 传承人端 1004、已下架不可再审核复活、删除的级联与媒体退役（复用 AU3 机制）、
 * 驳回理由落库回归。权限闸（学生/咨询师 403）见 {@link AdminControllerTest}。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminProjectTakedownTest {

    @Autowired
    private AdminService adminService;
    @Autowired
    private CraftProjectService craftProjectService;
    @Autowired
    private CraftSessionService craftSessionService;
    @Autowired
    private InheritorProjectService inheritorProjectService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.zhiyu.modules.notification.service.NotificationService notificationService;

    private static final long ADMIN = 930001L;
    private static final long INHERITOR = 930002L;
    private static final long STUDENT = 930003L;
    private static final long PROJECT = 930100L;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", ADMIN, "ay_admin", "管理员", 3);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR, "ay_inh", "传承人", 1);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", STUDENT, "ay_stu", "学生", 0);
        // 上架项目：2 步骤（一步带音频）+ 1 材料 + 项目视频
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(930200,?, 'ay-audio.mp3',100,NOW(),NOW(),0)", INHERITOR);
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(930201,?, 'ay-video.mp4',100,NOW(),NOW(),0)", INHERITOR);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,video_url,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺','/api/media/930201/content',1,NOW(),NOW(),0)", PROJECT, INHERITOR, "AY测试项目");
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,audio_url,create_time,update_time,deleted)"
                + " VALUES(930300,?,1,'步骤一','引导','/api/media/930200/content',NOW(),NOW(),0)", PROJECT);
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted)"
                + " VALUES(930301,?,2,'步骤二','引导',NOW(),NOW(),0)", PROJECT);
        jdbc.update("INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted)"
                + " VALUES(930400,?,'棉线','1卷',NOW(),NOW(),0)", PROJECT);
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AY1：下架成功——状态/理由落库/审计留痕/通知传承人（WORK_AUDIT refType=project 带理由）")
    void takedownSuccess() {
        adminService.takedownProject(PROJECT, "内容需复核");
        var row = jdbc.queryForMap("SELECT status,audit_reason FROM craft_project WHERE id=?", PROJECT);
        assertEquals(2, ((Number) row.get("status")).intValue());
        assertEquals("内容需复核", row.get("audit_reason"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log WHERE action='project.takedown' AND target_id=?", Integer.class, PROJECT));
        var n = jdbc.queryForMap(
                "SELECT type,ref_type,ref_id,title,content FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                INHERITOR, PROJECT);
        assertEquals("WORK_AUDIT", n.get("type"));
        assertEquals("project", n.get("ref_type"));
        assertTrue(String.valueOf(n.get("content")).contains("内容需复核"), "通知正文应带理由: " + n.get("content"));
        assertTrue(String.valueOf(n.get("title")).contains("下架"));
    }

    @Test
    @DisplayName("AY1：非法状态流转——待审(0)/已下架(2)下架 → 3001；不存在 → 1004；空理由 → 1001")
    void takedownBadState() {
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(PROJECT, " ")).getCode());
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(939999L, "理由")).getCode());
        jdbc.update("UPDATE craft_project SET status=0 WHERE id=?", PROJECT);
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(PROJECT, "理由")).getCode());
        jdbc.update("UPDATE craft_project SET status=2 WHERE id=?", PROJECT);
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> adminService.takedownProject(PROJECT, "理由")).getCode(), "重复下架应 3001");
    }

    @Test
    @DisplayName("AY1：下架后学生端（步骤/详情/会话）与传承人端详情均 1004；再审核 → 3001 不可复活")
    void takedownBlocksEverywhere() {
        adminService.takedownProject(PROJECT, "违规内容");
        UserContext.set(new LoginUser(STUDENT, 0));
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> craftProjectService.listSteps(PROJECT)).getCode(), "学生步骤列表应 1004");
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> craftSessionService.create(STUDENT, PROJECT, null)).getCode(), "学生建会话应 1004");
        UserContext.set(new LoginUser(INHERITOR, 1));
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> inheritorProjectService.getProjectDetail(INHERITOR, PROJECT)).getCode(), "传承人详情应 1004");
        UserContext.set(new LoginUser(ADMIN, 3));
        var req = new com.zhiyu.modules.admin.dto.AuditRequest();
        req.setAction("approve");
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> adminService.auditProject(PROJECT, req)).getCode(), "已下架不得再审核通过");
    }

    @Test
    @DisplayName("AY2：删除——级联逻辑删除步骤/材料，步骤音频与项目视频退役，审计+通知+理由")
    void deleteCascadeAndMediaRetired() {
        adminService.takedownProject(PROJECT, "先下架");
        adminService.deleteProject(PROJECT, "违规内容根除");
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM craft_project WHERE id=?", Integer.class, PROJECT));
        assertEquals("违规内容根除", jdbc.queryForObject("SELECT audit_reason FROM craft_project WHERE id=?", String.class, PROJECT));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM craft_step WHERE project_id=? AND deleted=0", Integer.class, PROJECT));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM craft_material WHERE project_id=? AND deleted=0", Integer.class, PROJECT));
        // 步骤音频 + 项目视频均退役（逻辑注销 + 清理任务）
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM media_asset WHERE id=930200", Integer.class), "步骤音频应退役");
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM media_asset WHERE id=930201", Integer.class), "项目视频应退役");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE' AND storage_key LIKE 'ay-%'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log WHERE action='project.delete' AND target_id=?", Integer.class, PROJECT));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND ref_id=? AND title LIKE '%删除%' AND deleted=0",
                Integer.class, INHERITOR, PROJECT));
    }

    @Test
    @DisplayName("AY2：删除不存在项目 → 1004；空理由 → 1001")
    void deleteValidation() {
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> adminService.deleteProject(939999L, "理由")).getCode());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> adminService.deleteProject(PROJECT, null)).getCode());
    }

    @Test
    @DisplayName("AY1：驳回理由同列落库（回归：此前校验非空后丢弃）")
    void rejectReasonPersisted() {
        jdbc.update("UPDATE craft_project SET status=0 WHERE id=?", PROJECT);
        var req = new com.zhiyu.modules.admin.dto.AuditRequest();
        req.setAction("reject");
        req.setReason("材料说明不完整");
        adminService.auditProject(PROJECT, req);
        assertEquals("材料说明不完整",
                jdbc.queryForObject("SELECT audit_reason FROM craft_project WHERE id=?", String.class, PROJECT));
        assertEquals(2, jdbc.queryForObject("SELECT status FROM craft_project WHERE id=?", Integer.class, PROJECT));
    }

    // ── BC1（2026-11-05）：理由下发 + 动作来源区分 ──

    @Test
    @DisplayName("BC1：详情下发 auditReason；评审驳回 vs 事后下架来源正确")
    void projectDetailReasonAndOrigin() {
        // 评审驳回路径：reject → statusOrigin=REJECT，lastAction=REJECT，理由下发
        jdbc.update("UPDATE craft_project SET status=0 WHERE id=?", PROJECT);
        var reject = new com.zhiyu.modules.admin.dto.AuditRequest();
        reject.setAction("reject");
        reject.setReason("内容不达标");
        adminService.auditProject(PROJECT, reject);
        var rejected = adminService.projectDetail(PROJECT);
        assertEquals("内容不达标", rejected.get("auditReason"), "管理端应拿到驳回理由");
        assertEquals("REJECT", rejected.get("lastAction"));
        assertEquals("REJECT", rejected.get("statusOrigin"));
        assertEquals(String.valueOf(ADMIN), rejected.get("lastActorId"));
        assertNotNull(rejected.get("lastActionTime"));

        // 另一项目：先通过后下架 → statusOrigin=TAKEDOWN
        long p2 = 930101L;
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',0,NOW(),NOW(),0)", p2, INHERITOR, "BC1项目");
        var approve = new com.zhiyu.modules.admin.dto.AuditRequest();
        approve.setAction("approve");
        adminService.auditProject(p2, approve);
        var approved = adminService.projectDetail(p2);
        assertEquals("APPROVE", approved.get("lastAction"));
        assertNull(approved.get("statusOrigin"), "上架项目无来源推导");
        adminService.takedownProject(p2, "事后抽检不合格");
        var takenDown = adminService.projectDetail(p2);
        assertEquals("TAKEDOWN", takenDown.get("lastAction"));
        assertEquals("TAKEDOWN", takenDown.get("statusOrigin"), "最近一次是事后下架");
        assertEquals("事后抽检不合格", takenDown.get("auditReason"));

        // 历史数据：status=2 且无动作日志（AY 前只有驳回）→ REJECT，不猜
        long p3 = 930102L;
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,audit_reason,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',2,'历史驳回',NOW(),NOW(),0)", p3, INHERITOR, "历史项目");
        var historical = adminService.projectDetail(p3);
        assertNull(historical.get("lastAction"));
        assertEquals("REJECT", historical.get("statusOrigin"));
    }

    @Test
    @DisplayName("BC1：已删除项目的详情 → 1004（删除动作经 operation-logs 追溯）")
    void projectDetailDeletedIs404() {
        adminService.deleteProject(PROJECT, "问责测试");
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> adminService.projectDetail(PROJECT)).getCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_log WHERE target_id=? AND action='project.delete'",
                Integer.class, PROJECT), "删除动作仍可经操作日志追溯");
    }

    // ── BC2（2026-11-05）：删除级联真实验证 + 共享封面不误删 ──

    @Test
    @DisplayName("BC2：三步骤（一步音频）+封面+视频项目删除后逐表清零；共享封面不误退役")
    void deleteCascadeCompleteAndSharedCoverSafe() {
        long pA = 930500L, pB = 930501L;
        // 共享封面（同一媒体引用被两个项目使用）+ A 的视频 + A 的第三步音频
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(930600,?,'bc-cover.png',100,NOW(),NOW(),0)", INHERITOR);
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(930601,?,'bc-video.mp4',100,NOW(),NOW(),0)", INHERITOR);
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(930602,?,'bc-audio.mp3',100,NOW(),NOW(),0)", INHERITOR);
        for (long pid : new long[]{pA, pB}) {
            jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,cover_url,status,create_time,update_time,deleted)"
                    + " VALUES(?,?,?,1,'纸艺','/api/media/930600/content',1,NOW(),NOW(),0)", pid, INHERITOR, "BC2项目" + pid);
        }
        jdbc.update("UPDATE craft_project SET video_url='/api/media/930601/content' WHERE id=?", pA);
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,audio_url,create_time,update_time,deleted)"
                            + " VALUES(?,?,?,?,?,?,NOW(),NOW(),0)",
                    930700L + i, pA, i, "步骤" + i, "引导", i == 2 ? "/api/media/930602/content" : null);
        }
        jdbc.update("INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted)"
                + " VALUES(930800,?,'丝线','2把',NOW(),NOW(),0)", pA);

        // 删除 A：A 的视频/音频退役；共享封面因 B 仍引用不得退役
        adminService.deleteProject(pA, "BC2 级联验证");
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM craft_project WHERE id=?", Integer.class, pA));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM craft_step WHERE project_id=? AND deleted=0", Integer.class, pA));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM craft_material WHERE project_id=? AND deleted=0", Integer.class, pA));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM media_asset WHERE id=930601", Integer.class), "A 的视频应退役");
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM media_asset WHERE id=930602", Integer.class), "A 的音频应退役");
        assertEquals(0, jdbc.queryForObject("SELECT deleted FROM media_asset WHERE id=930600", Integer.class),
                "共享封面在 B 存活期间不得退役");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE' AND storage_key LIKE 'bc-%'", Integer.class));

        // 删除 B：封面引用归零 → 退役
        adminService.deleteProject(pB, "BC2 级联验证");
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM media_asset WHERE id=930600", Integer.class), "B 删除后封面应退役");
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE' AND storage_key LIKE 'bc-%'", Integer.class));
    }

    // ── BE（2026-11-05）：审核结果触达传承人 ──

    @Test
    @DisplayName("BE：通过通知（无原因段）与驳回通知（带理由，超 120 字截断）")
    void auditResultNotifiesInheritor() {
        // 通过
        jdbc.update("UPDATE craft_project SET status=0 WHERE id=?", PROJECT);
        var approve = new com.zhiyu.modules.admin.dto.AuditRequest();
        approve.setAction("approve");
        adminService.auditProject(PROJECT, approve);
        var n1 = jdbc.queryForMap(
                "SELECT title,content,type,ref_type FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                INHERITOR, PROJECT);
        assertEquals("WORK_AUDIT", n1.get("type"));
        assertEquals("project", n1.get("ref_type"));
        assertTrue(String.valueOf(n1.get("title")).contains("已通过审核"), String.valueOf(n1.get("title")));
        assertFalse(String.valueOf(n1.get("content")).contains("原因"), "通过通知不应带原因段");

        // 驳回：理由 200 字 → 通知内截断到 120 字（audit_reason 列完整保留 300 以内）
        long p2 = 930103L;
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',0,NOW(),NOW(),0)", p2, INHERITOR, "BE驳回项目");
        var reject = new com.zhiyu.modules.admin.dto.AuditRequest();
        reject.setAction("reject");
        reject.setReason("因".repeat(200));
        adminService.auditProject(p2, reject);
        var n2 = jdbc.queryForMap(
                "SELECT title,content FROM notification WHERE user_id=? AND ref_id=? AND deleted=0", INHERITOR, p2);
        assertTrue(String.valueOf(n2.get("title")).contains("未通过审核"));
        String content = String.valueOf(n2.get("content"));
        assertTrue(content.contains("原因："), "驳回通知必须带理由");
        assertTrue(content.contains("因".repeat(120)) && !content.contains("因".repeat(121)),
                "理由应截断到 120 字: len=" + content.length());
        assertEquals("因".repeat(200),
                jdbc.queryForObject("SELECT audit_reason FROM craft_project WHERE id=?", String.class, p2),
                "库里 audit_reason 应保留完整理由");
    }

    @Test
    @DisplayName("BE：同一项目同一结果重复推送被 dedupe 去重（状态机之外的兜底）")
    void auditNotificationDeduped() {
        assertTrue(notificationService.notifyProjectAudit(INHERITOR, PROJECT, "项目", true, null));
        assertFalse(notificationService.notifyProjectAudit(INHERITOR, PROJECT, "项目", true, null),
                "同项目同结果第二次推送应被去重");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND ref_id=? AND dedupe_key LIKE 'project%audit%'",
                Integer.class, INHERITOR, PROJECT));
        // 不同结果（如未来重审驳回）是不同事件，不去重
        assertTrue(notificationService.notifyProjectAudit(INHERITOR, PROJECT, "项目", false, "理由"));
    }

    @Test
    @DisplayName("BE：通知写入失败不阻断审核主流程（状态与理由仍落库）")
    void auditSurvivesNotificationFailure() {
        // 用 mock 组装 AdminServiceImpl：通知抛异常时审核必须成功
        var projects = org.mockito.Mockito.mock(com.zhiyu.modules.craft.mapper.CraftProjectMapper.class);
        var project = new com.zhiyu.modules.craft.entity.CraftProject();
        project.setId(PROJECT);
        project.setStatus(0);
        project.setInheritorId(INHERITOR);
        project.setTitle("BE容错项目");
        org.mockito.Mockito.when(projects.selectById(PROJECT)).thenReturn(project);
        var failingNotify = org.mockito.Mockito.mock(
                com.zhiyu.modules.notification.service.NotificationService.class);
        org.mockito.Mockito.when(failingNotify.notifyProjectAudit(org.mockito.Mockito.any(), org.mockito.Mockito.any(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyBoolean(), org.mockito.Mockito.any()))
                .thenThrow(new RuntimeException("通知存储挂了"));
        var svc = new com.zhiyu.modules.admin.service.impl.AdminServiceImpl(projects, null, null, null, null,
                jdbc, failingNotify,
                org.mockito.Mockito.mock(com.zhiyu.modules.care.OperationLog.class), null);
        var req = new com.zhiyu.modules.admin.dto.AuditRequest();
        req.setAction("approve");
        assertDoesNotThrow(() -> svc.auditProject(PROJECT, req), "通知失败不得阻断审核");
        assertEquals(1, project.getStatus(), "审核状态仍已推进");
    }
}
