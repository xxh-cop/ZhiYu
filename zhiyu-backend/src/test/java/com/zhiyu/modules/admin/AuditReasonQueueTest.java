package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.notification.service.NotificationService;
import com.zhiyu.modules.notification.vo.NotificationVO;
import com.zhiyu.modules.work.service.CommentModerationService;
import com.zhiyu.modules.work.service.WorkInteractionService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * I1 驳回理由落库 + I2 audit-queue work/comment（2026-11-04）。
 *
 * <p>I1 覆盖：驳回/隐藏必填理由（1001）、通过不校验、理由落库可查、
 * 通知正文含「原因：xxx」、历史无理由数据读取兼容。
 * I2 覆盖：audit-queue type=work/comment 返回待审队列、aiAudits 为空数组
 * （历史审计无 ref 关联，不伪造）、auditHint 说明在场、摘要不泄露原文列。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("审核理由落库与审核队列聚合")
class AuditReasonQueueTest {

    private static final long AUTHOR = 996000L;
    private static final long ADMIN = 996002L;
    private static final long WORK_ID = 996010L;

    @Autowired SqlSessionTemplate session;
    @Autowired JdbcTemplate db;
    @Autowired CommentModerationService moderation;
    @Autowired WorkInteractionService interactions;
    @Autowired com.zhiyu.modules.admin.service.AdminService adminService;
    @Autowired NotificationService notificationService;

    @BeforeEach
    void setup() {
        for (int i = 0; i < 3; i++) {
            db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                    + "VALUES(?,?,'unused','审核理由用户',?,1,1,0)", AUTHOR + i, "auditqa" + i, i == 2 ? 3 : 0);
        }
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) "
                + "VALUES(?,?,'理由测试作品','text',1,0,0,1,0)", WORK_ID, AUTHOR);
        actor(ADMIN);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    private void actor(long id) {
        session.clearCache();
        UserContext.set(new LoginUser(id, id == ADMIN ? 3 : 0));
    }

    private long submitComment() {
        actor(AUTHOR);
        long id = interactions.comment(AUTHOR, WORK_ID, "很好的教程，已完成").getId();
        actor(ADMIN);
        return id;
    }

    @Test
    @DisplayName("I1：隐藏评论缺少理由返回 1001，通过时理由可空")
    void rejectRequiresReasonButApproveNot() {
        long id = submitComment();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> moderation.review(id, 2, 0, null));
        assertThat(ex.getCode()).isEqualTo(1001);
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> moderation.review(id, 2, 0, "  "));
        assertThat(ex2.getCode()).isEqualTo(1001);
        // 通过不校验理由
        moderation.review(id, 1, 0, null);
        assertThat(db.queryForObject("SELECT status FROM work_comment WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("I1：理由落库可查，通知正文含「原因：xxx」，历史无理由兼容")
    void reasonPersistedAndInNotification() {
        long id = submitComment();
        moderation.review(id, 2, 0, "含未经证实的疗效宣传");

        // 落库可查
        String stored = db.queryForObject("SELECT audit_reason FROM work_comment WHERE id=?",
                String.class, id);
        assertThat(stored).isEqualTo("含未经证实的疗效宣传");

        // 通知正文含理由，且不含评论原文
        actor(AUTHOR);
        @SuppressWarnings("unchecked")
        List<NotificationVO> records = (List<NotificationVO>)
                notificationService.list(1, 10, "COMMENT_AUDIT").get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getContent()).contains("原因：含未经证实的疗效宣传");
        assertThat(records.get(0).getContent()).doesNotContain("很好的教程");
        actor(ADMIN);

        // 历史无理由（audit_reason 为 NULL）的读取兼容：列表与详情查询不报错
        long legacy = submitComment();
        assertThat(db.queryForObject("SELECT audit_reason FROM work_comment WHERE id=?",
                String.class, legacy)).isNull();
        var queue = adminService.getAuditQueue("comment", 1);
        assertThat(queue.getRecords()).isNotEmpty(); // 读取不因 NULL 理由报错
    }

    @Test
    @DisplayName("I2：audit-queue type=comment 返回待审评论，审计摘要为空并附 auditHint")
    void commentQueueWithEmptyAuditSummary() {
        submitComment();
        var page = adminService.getAuditQueue("comment", 1);
        assertThat(page.getTotal()).isEqualTo(1);
        var item = page.getRecords().get(0);
        assertThat(item.getType()).isEqualTo("comment");
        assertThat(item.getAuthorId()).isEqualTo(AUTHOR);
        assertThat(item.getRefId()).isEqualTo(WORK_ID);
        // 历史审计没有 ref 关联：空数组 + hint，不伪造
        assertThat(item.getAiAudits()).isEmpty();
        assertThat(item.getAuditHint()).contains("暂无对象级 AI 审计记录");
        // 摘要不泄露加密原文列
        assertThat(item.getAiAudits().toString()).doesNotContain("rawInput");
    }

    @Test
    @DisplayName("I2：audit-queue type=work 返回待审作品，审计摘要为空并附 auditHint")
    void workQueueWithEmptyAuditSummary() {
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) "
                + "VALUES(996011,?,'待审作品','text',1,0,0,0,0)", AUTHOR);
        var page = adminService.getAuditQueue("work", 1);
        assertThat(page.getTotal()).isEqualTo(1);
        var item = page.getRecords().get(0);
        assertThat(item.getType()).isEqualTo("work");
        assertThat(item.getId()).isEqualTo(996011L);
        assertThat(item.getAuthorId()).isEqualTo(AUTHOR);
        assertThat(item.getAiAudits()).isEmpty();
        assertThat(item.getAuditHint()).contains("暂无对象级 AI 审计记录");
    }

    @Test
    @DisplayName("I2：ai_audit_log 携带 ref 后聚合返回脱敏摘要（不含原文）")
    void refLinkedAuditAggregates() {
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) "
                + "VALUES(996012,?,'待审作品2','text',1,0,0,0,0)", AUTHOR);
        // 模拟 2026-11-04 后的带 ref 审计写入（只读脱敏字段聚合）
        db.update("INSERT INTO ai_audit_log(id,user_id,scene,action,reason,ref_type,ref_id,create_time) "
                        + "VALUES(996020,?, 'work','BLOCK','命中高危词','work',996012,NOW())", AUTHOR);
        var page = adminService.getAuditQueue("work", 1);
        var item = page.getRecords().stream().filter(i -> i.getId() == 996012L).findFirst().orElseThrow();
        assertThat(item.getAiAudits()).hasSize(1);
        var audit = item.getAiAudits().get(0);
        assertThat(audit.get("scene")).isEqualTo("work");
        assertThat(audit.get("action")).isEqualTo("BLOCK");
        assertThat(audit.get("reason")).isEqualTo("命中高危词");
        // 脱敏：不返回加密原文列
        assertThat(audit).doesNotContainKeys("raw_input", "ai_output", "rawInput", "aiOutput");
        assertThat(item.getAuditHint()).isNull();
    }
}
