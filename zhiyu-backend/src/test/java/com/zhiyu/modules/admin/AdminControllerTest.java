package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.admin.controller.AdminController} 单测（M2 批 4）。
 *
 * <p>覆盖 4 个接口的 happy path + 异常路径：
 * <ul>
 *   <li>GET /api/admin/audit-queue — 无 token → 401；管理员 → 200</li>
 *   <li>PUT /api/admin/projects/{id}/audit — 非管理员 → 1003；审核不存在项目 → 1004</li>
 *   <li>GET /api/admin/care-reminders — 管理员 → 200</li>
 *   <li>GET /api/admin/dashboard/stats — 管理员 → 200</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    // ── 接口 1：GET /api/admin/audit-queue ──

    @Test
    @DisplayName("无 token 访问 audit-queue → 401")
    void testNoToken_AuditQueue_Returns401() throws Exception {
        mockMvc.perform(get("/api/admin/audit-queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生 token 访问 audit-queue → 403")
    void testStudentToken_AuditQueue_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-queue")
                        .header("Authorization", "Bearer " + token(4L, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员 token 访问 audit-queue → 200 + 分页结构")
    void testAdminToken_AuditQueue_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/audit-queue")
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.pageNum").exists())
                .andExpect(jsonPath("$.data.pageSize").exists())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ── 接口 2：PUT /api/admin/projects/{id}/audit ──

    @Test
    @DisplayName("咨询师审核项目 → 403（仅管理员可审核）")
    void testCounselorAudit_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("action", "approve"));

        mockMvc.perform(put("/api/admin/projects/999/audit")
                        .header("Authorization", "Bearer " + token(3L, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("管理员审核不存在的项目 → 1004")
    void testAdminAudit_NonExistentProject_Returns1004() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("action", "approve"));

        mockMvc.perform(put("/api/admin/projects/99999/audit")
                        .header("Authorization", "Bearer " + token(1L, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 3：GET /api/admin/care-reminders ──

    @Test
    @DisplayName("管理员访问 care-reminders → 200 + 分页结构")
    void testAdminToken_CareReminders_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/care-reminders")
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("无 token 访问 care-reminders → 401")
    void testNoToken_CareReminders_Returns401() throws Exception {
        mockMvc.perform(get("/api/admin/care-reminders"))
                .andExpect(status().isUnauthorized());
    }

    // ── 接口 4：GET /api/admin/dashboard/stats ──

    @Test
    @DisplayName("管理员访问 dashboard/stats → 200 + 指标结构")
    void testAdminToken_DashboardStats_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + token(1L, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalUsers").exists())
                .andExpect(jsonPath("$.data.totalWorks").exists())
                .andExpect(jsonPath("$.data.pendingProjects").exists())
                .andExpect(jsonPath("$.data.pendingCareReminders").exists())
                .andExpect(jsonPath("$.data.moodDistribution").isArray())
                .andExpect(jsonPath("$.data.userTrend7d").isArray());
    }

    @Test
    @DisplayName("学生 token 访问 dashboard/stats → 403")
    void testStudentToken_DashboardStats_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", "Bearer " + token(4L, 0)))
                .andExpect(status().isForbidden());
    }
}
