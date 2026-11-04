package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminWorkService;
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
 * O2 管理端三缺口 H2 单测（2026-11-04）。
 *
 * <p>覆盖：GET /api/admin/comments/{id} 脱敏详情（role∈{2,3}、1003、1004、截断）、
 * GET /api/admin/comments?workId= 按作品过滤、WorkDetailVO.auditReason 抽屉字段。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("O2 管理端评论详情/作品过滤/驳回理由展示")
class AdminCommentQueryTest {

    private static final long AUTHOR = 993001L;
    private static final long ADMIN = 993002L;
    private static final long COUNSELOR = 993003L;
    private static final long WORK_A = 993010L;
    private static final long WORK_B = 993011L;

    @Autowired SqlSessionTemplate session;
    @Autowired JdbcTemplate db;
    @Autowired CommentModerationService moderation;
    @Autowired WorkInteractionService interactions;
    @Autowired AdminWorkService adminWorkService;

    @BeforeEach
    void setup() {
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,'o2_author','hash','小织作者',0,1,1,0)", AUTHOR);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,'o2_admin','hash','管理员',3,1,1,0)", ADMIN);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,'o2_counselor','hash','咨询师',2,1,1,0)", COUNSELOR);
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) VALUES(?,?,'作品A','text',1,0,0,1,0)", WORK_A, AUTHOR);
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) VALUES(?,?,'作品B','text',1,0,0,1,0)", WORK_B, AUTHOR);
        actor(ADMIN);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    private void actor(long id) {
        session.clearCache();
        UserContext.set(new LoginUser(id, id == ADMIN ? 3 : (id == COUNSELOR ? 2 : 0)));
    }

    private long submit(long workId, String content) {
        actor(AUTHOR);
        long id = interactions.comment(AUTHOR, workId, content).getId();
        actor(ADMIN);
        return id;
    }

    @Test
    @DisplayName("评论详情：role∈{2,3} 返回脱敏摘要（workId/status/昵称/≤50 字截断），雪花按字符串")
    void detailReturnsSanitizedSummary() {
        String longContent = "这是一段很长的评论内容，".repeat(6) + "结尾。";
        long id = submit(WORK_A, longContent);

        var detail = moderation.detail(id);
        assertThat(detail.get("id")).isEqualTo(String.valueOf(id));
        assertThat(detail.get("workId")).isEqualTo(String.valueOf(WORK_A));
        assertThat(((Number) detail.get("status")).intValue()).isEqualTo(0);
        assertThat(detail.get("authorName")).isEqualTo("小织作者");
        String excerpt = (String) detail.get("contentExcerpt");
        assertThat(excerpt.length()).isLessThanOrEqualTo(51); // 50 字 + 省略号
        assertThat(excerpt).endsWith("…");
        assertThat(excerpt).doesNotContain("结尾");

        actor(COUNSELOR);
        assertThat(moderation.detail(id).get("workId")).isEqualTo(String.valueOf(WORK_A));
    }

    @Test
    @DisplayName("评论详情：学生 1003；已删除评论 1004")
    void detailPermissionAndDeleted() {
        long id = submit(WORK_A, "普通评论");
        actor(AUTHOR);
        assertThat(assertThrows(BusinessException.class, () -> moderation.detail(id)).getCode())
                .isEqualTo(1003);
        actor(ADMIN);
        db.update("UPDATE work_comment SET deleted=1 WHERE id=?", id);
        assertThat(assertThrows(BusinessException.class, () -> moderation.detail(id)).getCode())
                .isEqualTo(1004);
    }

    @Test
    @DisplayName("评论列表按 workId 过滤：只返回目标作品的评论，total 正确，旧参数兼容")
    void listFilteredByWorkId() {
        submit(WORK_A, "A 的评论一");
        submit(WORK_A, "A 的评论二");
        submit(WORK_B, "B 的评论");

        var filtered = moderation.list(1, 0, WORK_A);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) filtered.get("records");
        assertThat(records).hasSize(2);
        assertThat(((Number) filtered.get("total")).longValue()).isEqualTo(2);
        assertThat(records).allSatisfy(r -> assertThat(r.get("workTitle")).isEqualTo("作品A"));

        // 旧调用（不传 workId）不受影响
        var all = moderation.list(1, 0);
        assertThat(((Number) all.get("total")).longValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("WorkDetailVO 携带 auditReason：下架理由落库后详情抽屉可读；未审核时为 NULL")
    void workDetailCarriesAuditReason() {
        actor(ADMIN);
        adminWorkService.updateWorkStatus(WORK_A, 2, "含未经证实的疗效宣传");
        var detail = adminWorkService.getWork(WORK_A);
        assertThat(detail.getAuditReason()).isEqualTo("含未经证实的疗效宣传");

        var untouched = adminWorkService.getWork(WORK_B);
        assertThat(untouched.getAuditReason()).isNull();
    }
}
