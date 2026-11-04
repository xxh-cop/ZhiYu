package com.zhiyu.modules.notification;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.notification.service.NotificationService;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AQ4（2026-11-04）：通知删除——单条删除 + 批量清空，仅本人、逻辑删除、未读缓存失效。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationDeleteTest {

    @Autowired
    private NotificationService service;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long ME = 988100L;
    private static final long OTHER = 988200L;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted)"
                + " VALUES(?,?,'unused','AQ4本人',0,1,1,0)", ME, "aq4_" + ME);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted)"
                + " VALUES(?,?,'unused','AQ4他人',0,1,1,0)", OTHER, "aq4_" + OTHER);
        UserContext.set(new LoginUser(ME, 0));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private long push(long userId, String type, String dedupe) {
        service.push(userId, type, "标题", "内容", "work", 1L, dedupe);
        return jdbc.queryForObject("SELECT id FROM notification WHERE user_id=? AND dedupe_key=?",
                Long.class, userId, dedupe);
    }

    @Test
    @DisplayName("删除自己的通知成功；删除后列表与未读数同步移除")
    void deleteOwnWorks() {
        long id = push(ME, "WORK_AUDIT", "aq4:a");
        assertEquals(1, service.unreadCount());
        service.deleteOne(id);
        assertEquals(0, service.unreadCount(), "删除后未读数必须同步失效");
        assertEquals(0, ((Number) service.list(1, 10, null).get("total")).intValue());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id=? AND deleted=1",
                Integer.class, id), "逻辑删除（行保留 deleted=1）");
    }

    @Test
    @DisplayName("删他人通知 1003；不存在 1004")
    void deleteOthersForbidden() {
        long foreign = push(OTHER, "WORK_AUDIT", "aq4:b");
        assertThrows(BusinessException.class, () -> service.deleteOne(foreign));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteOne(99999999L));
        assertEquals(1004, ex.getCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id=? AND deleted=0",
                Integer.class, foreign), "他人数据不得被动");
    }

    @Test
    @DisplayName("批量清空只清本人；按类型过滤只清该类型")
    void clearScopedToSelfAndType() {
        push(ME, "WORK_AUDIT", "aq4:c1");
        push(ME, "COMMENT_AUDIT", "aq4:c2");
        push(OTHER, "WORK_AUDIT", "aq4:c3");

        int cleared = service.clearAll("WORK_AUDIT");
        assertEquals(1, cleared, "按类型清空只清本人该类型");
        assertEquals(1, ((Number) service.list(1, 10, null).get("total")).intValue(), "剩评论通知");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND deleted=0", Integer.class, OTHER),
                "他人通知不受影响");

        int all = service.clearAll(null);
        assertEquals(1, all, "全量清空删剩余");
        assertEquals(0, service.unreadCount());
    }

    @Test
    @DisplayName("非法类型清空 → 1001")
    void clearInvalidType() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.clearAll("HACKED"));
        assertEquals(1001, ex.getCode());
    }
}
