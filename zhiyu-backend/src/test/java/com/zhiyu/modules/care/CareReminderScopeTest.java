package com.zhiyu.modules.care;

import com.zhiyu.modules.audit.service.CareReminderService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AQ3（2026-11-04）：关怀提醒通知范围收窄——
 * 只发管理员 + 已认领该用户个案的咨询师；未认领咨询师不再收到。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CareReminderScopeTest {

    @Autowired
    private CareReminderService careReminderService;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN = 987003L;
    private static final long CONSULTANT_CLAIMED = 987002L;
    private static final long CONSULTANT_FREE = 987004L;
    private static final long STUDENT = 987100L;

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private void seedUser(long id, int role) {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted)"
                + " VALUES(?,?,'unused','AQ3用户',?,1,1,0)", id, "aq3_" + id, role);
    }

    private int notificationsOf(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND type='CARE_REMINDER'",
                Integer.class, userId);
    }

    @Test
    @DisplayName("未认领咨询师不收到；已认领咨询师与管理员收到")
    void narrowedRecipients() {
        seedUser(ADMIN, 3);
        seedUser(CONSULTANT_CLAIMED, 2);
        seedUser(CONSULTANT_FREE, 2);
        seedUser(STUDENT, 0);
        // 已认领：咨询师 A 认领了该学生既有提醒的个案
        jdbc.update("INSERT INTO care_reminder(id,user_id,risk_level,summary,status,deleted)"
                + " VALUES(987200,?,2,'旧提醒',1,0)", STUDENT);
        jdbc.update("INSERT INTO care_case(id,reminder_id,owner_id,state,create_time,update_time,deleted)"
                + " VALUES(987201,987200,?,'OPEN',NOW(),NOW(),0)", CONSULTANT_CLAIMED);

        careReminderService.trigger(STUDENT, null, 2, "脱敏摘要");

        assertEquals(1, notificationsOf(ADMIN), "管理员必须收到");
        assertEquals(1, notificationsOf(CONSULTANT_CLAIMED), "已认领咨询师必须收到");
        assertEquals(0, notificationsOf(CONSULTANT_FREE), "未认领咨询师不得收到（AQ3 收窄）");
    }
}
