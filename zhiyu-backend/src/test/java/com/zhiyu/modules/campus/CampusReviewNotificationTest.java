package com.zhiyu.modules.campus;

import com.zhiyu.common.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AH3（2026-11-04）：校园核验审核结果通知。
 * happy path（通过）+ 驳回带理由 + 重复审核不重复通知。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampusReviewNotificationTest {

    @Autowired
    private CampusService campus;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN = 981003L;
    private static final long APPLICANT = 981100L;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(981003,'ah3_admin','unused','管理员',3,1,1,0,0)");
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(981100,'ah3_student','unused','学生',0,1,1,0,0)");
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private long insertPending(long userId) {
        long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbc.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,"
                        + "create_time,update_time,deleted) VALUES(?,?,?,?,?,'PENDING',NOW(),NOW(),0)",
                id, userId, "测试学校", "测试学院", "cipher");
        return id;
    }

    private Map<String, Object> notificationOf(long verificationId) {
        return jdbc.queryForMap("SELECT user_id,type,title,content,ref_type,ref_id,read_flag "
                + "FROM notification WHERE ref_type='campus_verification' AND ref_id=?", verificationId);
    }

    @Test
    @DisplayName("审核通过 → 申请人收到 CAMPUS_AUDIT 通知（happy path）")
    void approveNotifiesApplicant() {
        long vid = insertPending(APPLICANT);
        campus.review(vid, new CampusService.Review("APPROVED", "信息核实无误"));

        Map<String, Object> n = notificationOf(vid);
        assertEquals(APPLICANT, ((Number) n.get("user_id")).longValue(), "接收者必须是申请人");
        assertEquals("CAMPUS_AUDIT", n.get("type"));
        assertEquals("校园身份核验已通过", n.get("title"));
        assertFalse(String.valueOf(n.get("content")).contains("原因"),
                "通过通知不带理由: " + n.get("content"));
        assertEquals(0, ((Number) n.get("read_flag")).intValue());
    }

    @Test
    @DisplayName("审核驳回 → 通知正文带审核理由（与作品/评论驳回同风格）")
    void rejectNotifiesWithReason() {
        long vid = insertPending(APPLICANT);
        campus.review(vid, new CampusService.Review("REJECTED", "证件照片不清晰，请重新上传"));

        Map<String, Object> n = notificationOf(vid);
        assertEquals("校园身份核验未通过", n.get("title"));
        assertTrue(String.valueOf(n.get("content")).contains("原因：证件照片不清晰，请重新上传"),
                "驳回通知必须带理由正文: " + n.get("content"));
    }

    @Test
    @DisplayName("重复审核被拒绝（3001）且不产生第二条通知")
    void duplicateReviewRejectedWithoutExtraNotification() {
        long vid = insertPending(APPLICANT);
        campus.review(vid, new CampusService.Review("REJECTED", "信息不全"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> campus.review(vid, new CampusService.Review("APPROVED", "补通过")));
        assertEquals(3001, ex.getCode());
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE ref_type='campus_verification' AND ref_id=?",
                Integer.class, vid);
        assertEquals(1, count, "重复审核不得产生第二条通知");
    }
}
