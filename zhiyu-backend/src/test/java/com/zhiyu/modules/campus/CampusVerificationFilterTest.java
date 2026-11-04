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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AG1（2026-11-04）：GET /api/admin/campus-verifications 的 status 过滤。
 * 覆盖：带过滤只返回该状态 / 不传与旧行为等价（含排序与 total 口径）/ 非法值 1001。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampusVerificationFilterTest {

    @Autowired
    private CampusService campus;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long ADMIN = 980003L;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(980003,'ag_admin','unused','管理员',3,1,1,0,0)");
        for (long uid : List.of(980100L, 980101L, 980102L)) {
            jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                    + " VALUES(?,?, 'unused','学生',0,1,1,0,0)", uid, "ag_student_" + uid);
        }
        insertVerification(980100L, "PENDING", "2026-11-04 10:00:00");
        insertVerification(980101L, "APPROVED", "2026-11-04 11:00:00");
        insertVerification(980102L, "REJECTED", "2026-11-04 12:00:00");
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private void insertVerification(long userId, String status, String updateTime) {
        jdbc.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,"
                        + "create_time,update_time,deleted) VALUES(?,?,?,?,?,?,'2026-11-04 09:00:00',?,0)",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), userId,
                "测试学校", "测试学院", "not-real-cipher", status, updateTime);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("records");
    }

    @Test
    @DisplayName("status=PENDING → 只返回待审记录，total 同步过滤")
    void filterByPendingOnly() {
        Map<String, Object> page = campus.list(1, "PENDING");
        assertEquals(1, page.get("total"));
        assertEquals(1, records(page).size());
        assertEquals("PENDING", records(page).get(0).get("status"));
    }

    @Test
    @DisplayName("status 小写输入归一（pending → PENDING）")
    void filterIsCaseInsensitive() {
        Map<String, Object> page = campus.list(1, "approved");
        assertEquals(1, page.get("total"));
        assertEquals("APPROVED", records(page).get(0).get("status"));
    }

    @Test
    @DisplayName("不传 status → 全状态混排，与旧行为等价（update_time DESC 排序、total 不过滤）")
    void noFilterKeepsLegacyBehavior() {
        Map<String, Object> page = campus.list(1, null);
        assertEquals(3, page.get("total"));
        assertEquals(List.of("REJECTED", "APPROVED", "PENDING"),
                records(page).stream().map(r -> r.get("status")).toList());
    }

    @Test
    @DisplayName("空白 status 视同不传（向后兼容）")
    void blankStatusEqualsNoFilter() {
        assertEquals(3, campus.list(1, "  ").get("total"));
    }

    @Test
    @DisplayName("非法 status → 1001（与 offset() 同风格的显式校验）")
    void invalidStatusRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> campus.list(1, "BOGUS"));
        assertEquals(1001, ex.getCode());
    }

    @Test
    @DisplayName("过滤后 total 仍是 Integer（JSON number 口径不被顺手改回）")
    void totalStaysInteger() {
        assertTrue(campus.list(1, "PENDING").get("total") instanceof Integer);
    }
}
