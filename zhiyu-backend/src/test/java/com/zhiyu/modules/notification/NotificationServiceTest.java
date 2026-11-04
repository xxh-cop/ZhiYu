package com.zhiyu.modules.notification;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.notification.service.NotificationService;
import com.zhiyu.modules.notification.vo.NotificationVO;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通知系统 H2 单测（2026-11-04）。
 *
 * <p>覆盖 features/45-notification.feature：未读计数、分页、按类型筛选、
 * 越权 1003、标记已读幂等、全部已读幂等、事件去重、逻辑删除、关怀/评论类正文脱敏。
 * 每个用例独立用户 ID，互不影响；数据库写入随事务回滚。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("通知系统：查询、幂等与事件源")
class NotificationServiceTest {

    private static final long ALICE = 910001L;
    private static final long BOB = 910002L;

    @Autowired
    private NotificationService service;

    @BeforeEach
    void loginAlice() {
        UserContext.set(new LoginUser(ALICE, 0));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @SuppressWarnings("unchecked")
    private List<NotificationVO> records(Map<String, Object> data) {
        return (List<NotificationVO>) data.get("records");
    }

    @Test
    @DisplayName("未读计数随事件创建与已读变化（缓存不失效错误）")
    void unreadCountTracksEvents() {
        assertThat(service.unreadCount()).isZero();

        service.notifyWorkAudit(ALICE, 5001L, "竹编小篮", 1);
        service.notifyCommentAudit(ALICE, 6001L, 7001L, 1, null);
        assertThat(service.unreadCount()).isEqualTo(2);

        var page = records(service.list(1, 10, null));
        service.markRead(Long.parseLong(page.get(0).getId()));
        assertThat(service.unreadCount()).isEqualTo(1);

        service.markAllRead();
        assertThat(service.unreadCount()).isZero();
    }

    @Test
    @DisplayName("分页：25 条分 2 页，倒序返回，id 为字符串")
    void paginationWorks() {
        for (int i = 0; i < 25; i++) {
            service.push(ALICE, "WORK_AUDIT", "作品审核已通过并发布", "内容" + i,
                    "work", 7000L + i, "work:" + (7000L + i) + ":1");
        }
        Map<String, Object> page1 = service.list(1, 20, null);
        assertThat(records(page1)).hasSize(20);
        assertThat(page1.get("total")).isEqualTo(25);
        assertThat(page1.get("page")).isEqualTo(1);
        assertThat(page1.get("size")).isEqualTo(20);
        assertThat(records(page1).get(0).getId()).matches("^[0-9]+$");

        Map<String, Object> page2 = service.list(2, 20, null);
        assertThat(records(page2)).hasSize(5);
        // 两页无交集
        assertThat(records(page2).stream().map(NotificationVO::getId))
                .doesNotContainAnyElementsOf(records(page1).stream().map(NotificationVO::getId).toList());
    }

    @Test
    @DisplayName("按类型筛选；非法类型返回 1001")
    void typeFilterAndInvalidType() {
        service.notifyWorkAudit(ALICE, 5002L, "剪纸", 1);
        service.notifyCommentAudit(ALICE, 6002L, 7002L, 2, "含广告信息");
        service.notifyBookingReply(ALICE, 8001L, "一对一指导", true);

        var workOnly = records(service.list(1, 20, "WORK_AUDIT"));
        assertThat(workOnly).hasSize(1);
        assertThat(workOnly.get(0).getType()).isEqualTo("WORK_AUDIT");

        var commentOnly = records(service.list(1, 20, "COMMENT_AUDIT"));
        assertThat(commentOnly).hasSize(1);
        // S2：refId=所属作品（跳转目标），subRefId=评论锚点
        assertThat(commentOnly.get(0).getRefId()).isEqualTo("7002");
        assertThat(commentOnly.get(0).getSubRefId()).isEqualTo("6002");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.list(1, 20, "HACKED"));
        assertThat(ex.getCode()).isEqualTo(1001);
    }

    @Test
    @DisplayName("越权标记他人通知返回 1003；列表互不可见")
    void crossUserForbidden() {
        service.notifyWorkAudit(BOB, 5003L, "bob 的作品", 1);
        // alice 的列表不含 bob 的通知
        assertThat(records(service.list(1, 10, null))).isEmpty();

        UserContext.set(new LoginUser(BOB, 0));
        var bobRecords = records(service.list(1, 10, null));
        assertThat(bobRecords).hasSize(1);
        long bobNotificationId = Long.parseLong(bobRecords.get(0).getId());

        UserContext.set(new LoginUser(ALICE, 0));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(bobNotificationId));
        assertThat(ex.getCode()).isEqualTo(1003);
        // bob 的未读数不受影响
        UserContext.set(new LoginUser(BOB, 0));
        assertThat(service.unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("标记已读幂等：重复标记无副作用；标记不存在返回 1004")
    void markReadIdempotentAndNotFound() {
        service.notifyWorkAudit(ALICE, 5004L, "陶艺", 1);
        long id = Long.parseLong(records(service.list(1, 10, null)).get(0).getId());

        service.markRead(id);
        assertThat(service.unreadCount()).isZero();
        // 重复标记不抛错、无副作用
        service.markRead(id);
        assertThat(service.unreadCount()).isZero();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(999999999L));
        assertThat(ex.getCode()).isEqualTo(1004);
    }

    @Test
    @DisplayName("全部已读幂等：第一次返回实际条数，第二次返回 0")
    void markAllReadIdempotent() {
        service.notifyWorkAudit(ALICE, 5005L, "竹编", 1);
        service.notifyCommentAudit(ALICE, 6005L, 7005L, 1, null);
        service.notifyBookingReply(ALICE, 8005L, "指导", false);
        assertThat(service.markAllRead()).isEqualTo(3);
        assertThat(service.markAllRead()).isZero();
    }

    @Test
    @DisplayName("事件去重：同对象同结果只生成一条；同对象不同结果各自一条")
    void dedupeByEventKey() {
        assertThat(service.notifyWorkAudit(ALICE, 5006L, "竹编小篮", 1)).isTrue();
        assertThat(service.notifyWorkAudit(ALICE, 5006L, "竹编小篮", 1)).isFalse();
        assertThat(service.notifyWorkAudit(ALICE, 5006L, "竹编小篮", 2)).isTrue();
        assertThat(records(service.list(1, 10, null))).hasSize(2);

        // 小组：同一人重复加入不重复；不同成员各自一条
        assertThat(service.notifyGroupJoin(ALICE, 9001L, "编织互助社", 3001L)).isTrue();
        assertThat(service.notifyGroupJoin(ALICE, 9001L, "编织互助社", 3001L)).isFalse();
        assertThat(service.notifyGroupJoin(ALICE, 9001L, "编织互助社", 3002L)).isTrue();
        assertThat(records(service.list(1, 10, "GROUP_JOIN"))).hasSize(2);
    }

    @Test
    @DisplayName("关怀通知正文脱敏：不含用户 ID 与会话内容")
    void careReminderContentIsSanitized() {
        service.notifyCareReminder(ALICE, 4001L);
        var records = records(service.list(1, 10, "CARE_REMINDER"));
        assertThat(records).hasSize(1);
        String text = records.get(0).getTitle() + records.get(0).getContent();
        assertThat(text).contains("关怀");
        assertThat(text).doesNotContain("910001");
        assertThat(text).doesNotContain("会话");
        assertThat(text).doesNotContain("summary");
    }

    @Test
    @DisplayName("评论审核通知不含评论原文")
    void commentAuditContentHasNoOriginalText() {
        service.notifyCommentAudit(ALICE, 6006L, 7006L, 2, "违规内容");
        var records = records(service.list(1, 10, "COMMENT_AUDIT"));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getContent()).doesNotContain("原文");
        assertThat(records.get(0).getContent()).contains("隐藏");
    }

    @Test
    @DisplayName("S2 评论通知 ref 语义：refId=作品、subRefId=评论锚点，dedupe 按评论键不重复")
    void commentAuditRefSemantics() {
        assertThat(service.notifyCommentAudit(ALICE, 6010L, 7010L, 1, null)).isTrue();
        // 同一事件重推：dedupe_key 按评论 id 生成，ref 语义不变则不重复
        assertThat(service.notifyCommentAudit(ALICE, 6010L, 7010L, 1, null)).isFalse();
        var records = records(service.list(1, 10, "COMMENT_AUDIT"));
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getRefType()).isEqualTo("comment");
        assertThat(records.get(0).getRefId()).isEqualTo("7010");
        assertThat(records.get(0).getSubRefId()).isEqualTo("6010");
        // 其他类型通知 subRefId 为 null
        service.notifyWorkAudit(ALICE, 5010L, "竹编", 1);
        var works = records(service.list(1, 10, "WORK_AUDIT"));
        assertThat(works.get(0).getSubRefId()).isNull();
    }

    @Test
    @DisplayName("逻辑删除后不再计数、不再返回；标记已删通知返回 1004")
    void logicDeleteHidesNotification() {
        service.notifyWorkAudit(ALICE, 5007L, "竹编", 1);
        long id = Long.parseLong(records(service.list(1, 10, null)).get(0).getId());

        // 逻辑删除（走 MP @TableLogic）
        notificationMapper.deleteById(id);

        assertThat(service.unreadCount()).isZero();
        assertThat(records(service.list(1, 10, null))).isEmpty();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(id));
        assertThat(ex.getCode()).isEqualTo(1004);
    }

    @Autowired
    private com.zhiyu.modules.notification.mapper.NotificationMapper notificationMapper;
}
