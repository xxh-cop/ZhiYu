package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.audit.entity.AiAuditLog;
import com.zhiyu.modules.audit.mapper.AiAuditLogMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.admin.controller.AdminAuditLogController} 单测（P1-A1）。
 *
 * <p>覆盖 2 个接口的 happy path + 异常路径。
 * 测试数据通过 {@link AiAuditLogMapper} 直接插入 H2 内存库，
 * rawInput / aiOutput 使用 {@link AesEncryptor} 加密后写入。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class AdminAuditLogControllerTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate authFixtureDb;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiAuditLogMapper auditLogMapper;

    @Autowired
    private AesEncryptor encryptor;

    private Long logId;

    private String token(Long userId, int role) {
        com.zhiyu.support.AuthenticatedTestUser.ensure(authFixtureDb,userId,role);
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        // @Transactional 会自动回滚每个测试方法，无需手动清理

        AiAuditLog log = new AiAuditLog();
        log.setScene("chat");
        log.setRawInput(encryptor.encrypt("用户说：我很焦虑"));
        log.setAiOutput(encryptor.encrypt("我理解你的感受，让我们一起来做手工吧"));
        log.setAction("PASS");
        log.setReason("正常对话");
        auditLogMapper.insert(log);
        logId = log.getId();
    }

    // ── 接口 7：GET /api/admin/audit-logs ──

    @Test
    @DisplayName("管理员查询审计日志列表 → 200")
    void testAdmin_ListAuditLogs_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("咨询师不能访问管理员审计日志 → 403")
    void testCounselor_ListAuditLogs_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + token(2L, 2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("学生查询审计日志列表 → 403")
    void testStudent_ListAuditLogs_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + token(4L, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("审计日志列表不返回 rawInput / aiOutput")
    void testListAuditLogs_NoRawInput() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].rawInput").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].aiOutput").doesNotExist());
    }

    // ── 接口 8：GET /api/admin/audit-logs/{id} ──

    @Test
    @DisplayName("管理员查询审计日志详情 → 200 + 隐私保护占位，不返回原始对话")
    void testAdmin_GetAuditLog_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs/{id}", logId)
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(logId))
                .andExpect(jsonPath("$.data.scene").value("chat"))
                .andExpect(jsonPath("$.data.action").value("PASS"))
                .andExpect(jsonPath("$.data.rawInput").value("[隐私保护：不提供原始输入]"))
                .andExpect(jsonPath("$.data.aiOutput").value("[隐私保护：不提供原始回复]"));
    }

    @Test
    @DisplayName("咨询师查询审计日志详情 → 403（仅管理员）")
    void testCounselor_GetAuditLog_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs/{id}", logId)
                        .header("Authorization", "Bearer " + token(2L, 2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("管理员查询不存在的审计日志 → 1004")
    void testAdmin_GetAuditLog_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
