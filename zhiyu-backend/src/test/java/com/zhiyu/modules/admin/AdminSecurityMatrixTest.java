package com.zhiyu.modules.admin;

import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端授权矩阵（SecurityConfig 收紧后）。
 *
 * <p>{@code /api/admin/**} 默认仅 role=3；只有 {@code /api/admin/care-reminders/**}
 * 显式放行 role ∈ {2,3}。{@code /api/inheritor/**} 保持仅 role=1。
 * role=0/1/2 访问未放行的管理端接口必须是 403 + {@code Result(1003)}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminSecurityMatrixTest {

    @Autowired
    private JdbcTemplate authFixtureDb;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token(long userId, int role) {
        com.zhiyu.support.AuthenticatedTestUser.ensure(authFixtureDb, userId, role);
        return "Bearer " + jwtUtil.createToken(userId, role);
    }

    @Test
    @DisplayName("role=0：管理端全部 403")
    void studentDeniedOnAdminEndpoints() throws Exception {
        String t = token(881001L, 0);
        for (String path : new String[]{
                "/api/admin/dashboard/stats", "/api/admin/audit-queue", "/api/admin/care-reminders"}) {
            mockMvc.perform(get(path).header("Authorization", t))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(1003));
        }
    }

    @Test
    @DisplayName("role=1（传承人）：管理端 403，但 /api/inheritor/** 可进入业务层")
    void inheritorDeniedOnAdminButAllowedOnOwnEndpoints() throws Exception {
        String t = token(881002L, 1);
        for (String path : new String[]{
                "/api/admin/dashboard/stats", "/api/admin/audit-queue", "/api/admin/care-reminders"}) {
            mockMvc.perform(get(path).header("Authorization", t))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(1003));
        }
        // 数据库中无 profile → 1004/404，证明已通过 Security（不是 403）
        mockMvc.perform(get("/api/inheritor/profile").header("Authorization", t))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("role=2（咨询师）：仅 care-reminders 放行，其余管理端 403")
    void counselorOnlyAllowedOnCareReminders() throws Exception {
        String t = token(881003L, 2);
        for (String path : new String[]{
                "/api/admin/dashboard/stats", "/api/admin/audit-queue",
                "/api/admin/audit-logs", "/api/admin/works", "/api/admin/groups", "/api/admin/inheritors"}) {
            mockMvc.perform(get(path).header("Authorization", t))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(1003));
        }
        mockMvc.perform(get("/api/admin/care-reminders").header("Authorization", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("role=3（管理员）：代表性接口 200")
    void adminAllowedOnAdminEndpoints() throws Exception {
        String t = token(881004L, 3);
        for (String path : new String[]{
                "/api/admin/dashboard/stats", "/api/admin/audit-queue",
                "/api/admin/audit-logs", "/api/admin/works",
                "/api/admin/groups", "/api/admin/inheritors"}) {
            mockMvc.perform(get(path).header("Authorization", t))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Test
    @DisplayName("/api/inheritor/** 仍仅 role=1：role=2/0 一律 403")
    void inheritorEndpointsStayRoleOne() throws Exception {
        mockMvc.perform(get("/api/inheritor/profile")
                        .header("Authorization", token(881003L, 2)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/inheritor/profile")
                        .header("Authorization", token(881001L, 0)))
                .andExpect(status().isForbidden());
    }
}
