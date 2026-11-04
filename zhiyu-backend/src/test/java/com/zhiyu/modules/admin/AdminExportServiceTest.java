package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminExportService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * N2 导出审计 H2 单测（2026-11-04，设计 docs/export-audit-20261104.md）。
 *
 * <p>覆盖：登记落库（actor/字段/批次/文件名）、字段黑名单拒绝、评论导出 3001、
 * 超阈值告警标记、用途必填、权限分离（非管理员 1003）、审计自查分页。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("N2 导出审计：留痕、黑名单、告警与权限")
class AdminExportServiceTest {

    private static final long ADMIN = 994001L;
    private static final long STUDENT = 994002L;

    @Autowired AdminExportService service;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void setup() {
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                + "VALUES(?, 'exp_admin', 'hash', '导出管理员', 3, 1, 1, 0)", ADMIN);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                + "VALUES(?, 'exp_student', 'hash', '导出学生', 0, 1, 1, 0)", STUDENT);
        UserContext.set(new LoginUser(ADMIN, 3));
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    @DisplayName("登记落库：actor/字段/用途/批次/文件名/alert=false，批次唯一")
    void registerPersists() {
        Map<String, Object> data = service.register("works", "status=1", "id,title,createTime", 42, "运营月报");
        assertThat(data.get("batchNo")).asString().matches("\\d+");
        assertThat((String) data.get("fileName")).contains((String) data.get("batchNo"))
                .contains("works").endsWith(".csv");
        assertThat(data.get("alert")).isEqualTo(false);

        var row = db.queryForMap("SELECT actor_id,actor_role,target_type,field_list,record_count,purpose,alert_flag "
                + "FROM admin_export_log WHERE batch_no=?", (String) data.get("batchNo"));
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(ADMIN);
        assertThat(row.get("target_type")).isEqualTo("works");
        assertThat(row.get("field_list")).isEqualTo("id,title,createTime");
        assertThat(((Number) row.get("record_count")).intValue()).isEqualTo(42);
        assertThat(((Number) row.get("alert_flag")).intValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("字段黑名单：含 password/secret/cipher/token 直接 1001 拒绝")
    void fieldBlacklistRejected() {
        for (String banned : new String[]{"id,password", "secret_key", "identity_cipher", "access_token"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.register("users", null, banned, 1, "测试"));
            assertThat(ex.getCode()).isEqualTo(1001);
        }
        // 拒绝后不落库
        Long count = db.queryForObject("SELECT COUNT(*) FROM admin_export_log", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("评论导出当前不开放（3001），开放条件见设计文档")
    void commentsExportClosed() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register("comments", null, "id,content", 10, "内容分析"));
        assertThat(ex.getCode()).isEqualTo(3001);
    }

    @Test
    @DisplayName("超阈值导出标记 alert=true 且 alert_flag=1（阈值默认 5000）")
    void alertAboveThreshold() {
        Map<String, Object> data = service.register("users", null, "id,username", 6000, "全量备份");
        assertThat(data.get("alert")).isEqualTo(true);
        Integer flag = db.queryForObject("SELECT alert_flag FROM admin_export_log WHERE batch_no=?",
                Integer.class, (String) data.get("batchNo"));
        assertThat(flag).isEqualTo(1);
    }

    @Test
    @DisplayName("用途必填：空用途 1001；类型非法 1001")
    void purposeAndTypeRequired() {
        assertThat(assertThrows(BusinessException.class,
                () -> service.register("works", null, "id", 1, "")).getCode()).isEqualTo(1001);
        assertThat(assertThrows(BusinessException.class,
                () -> service.register("hacked", null, "id", 1, "用途")).getCode()).isEqualTo(1001);
    }

    @Test
    @DisplayName("权限分离：学生登记与查询均 1003")
    void nonAdminForbidden() {
        UserContext.set(new LoginUser(STUDENT, 0));
        assertThat(assertThrows(BusinessException.class,
                () -> service.register("works", null, "id", 1, "用途")).getCode()).isEqualTo(1003);
        assertThat(assertThrows(BusinessException.class,
                () -> service.list(1, null)).getCode()).isEqualTo(1003);
    }

    @Test
    @DisplayName("审计自查：分页 + targetType 过滤 + 不含业务原文列")
    void auditListPaginationAndFilter() {
        service.register("works", null, "id,title", 3, "月报");
        service.register("users", null, "id,username", 7, "账号核对");

        Map<String, Object> all = service.list(1, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) all.get("records");
        assertThat(records).hasSize(2);
        assertThat(all.get("total")).isEqualTo(2);
        assertThat(records.toString()).doesNotContain("password");

        Map<String, Object> worksOnly = service.list(1, "works");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filtered = (List<Map<String, Object>>) worksOnly.get("records");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).get("targetType")).isEqualTo("works");
        // 批次号字符串传递（雪花）
        assertThat((String) filtered.get(0).get("batchNo")).matches("\\d+");
    }
}
