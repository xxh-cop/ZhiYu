package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.admin.controller.AdminUserController} 单测（P1-A1）。
 *
 * <p>覆盖 6 个接口的 happy path + 异常路径。
 * 测试数据通过 {@link UserMapper} 直接插入 H2 内存库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    private Long adminUserId;
    private Long studentUserId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        // @Transactional 会自动回滚每个测试方法，无需手动清理

        User admin = new User();
        admin.setUsername("admin_test");
        admin.setPassword("$2a$10$dummyhash");
        admin.setNickname("管理员");
        admin.setRole(3);
        admin.setStatus(1);
        admin.setCanContact(0);
        userMapper.insert(admin);
        adminUserId = admin.getId();

        User student = new User();
        student.setUsername("student_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();
    }

    // ── 接口 1：GET /api/admin/users ──

    @Test
    @DisplayName("管理员分页查询用户列表 → 200")
    void testAdmin_ListUsers_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("学生访问用户列表 → 403")
    void testStudent_ListUsers_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isForbidden());
    }

    // ── 接口 2：GET /api/admin/users/{id} ──

    @Test
    @DisplayName("管理员查询用户详情 → 200")
    void testAdmin_GetUser_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", studentUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(studentUserId))
                .andExpect(jsonPath("$.data.username").value("student_test"))
                .andExpect(jsonPath("$.data.craftPreferences").hasJsonPath());
    }

    @Test
    @DisplayName("管理员查询不存在的用户 → 1004")
    void testAdmin_GetUser_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 3：PUT /api/admin/users/{id} ──

    @Test
    @DisplayName("管理员编辑用户 → 200")
    void testAdmin_UpdateUser_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("nickname", "新昵称"));

        mockMvc.perform(put("/api/admin/users/{id}", studentUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }

    @Test
    @DisplayName("咨询师编辑用户 → 403（仅管理员）")
    void testCounselor_UpdateUser_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("nickname", "hack"));

        mockMvc.perform(put("/api/admin/users/{id}", studentUserId)
                        .header("Authorization", "Bearer " + token(99L, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 接口 4：PUT /api/admin/users/{id}/status ──

    @Test
    @DisplayName("管理员禁用用户 → 200")
    void testAdmin_UpdateStatus_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", 0));

        mockMvc.perform(put("/api/admin/users/{id}/status", studentUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("管理员禁用自己 → 1001（不允许操作自己）")
    void testAdmin_UpdateStatus_Self_Returns1001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", 0));

        mockMvc.perform(put("/api/admin/users/{id}/status", adminUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ── 接口 5：PUT /api/admin/users/{id}/role ──

    @Test
    @DisplayName("管理员变更用户角色 → 200")
    void testAdmin_UpdateRole_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", 1));

        mockMvc.perform(put("/api/admin/users/{id}/role", studentUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("管理员修改自己角色 → 1001（不允许修改自己）")
    void testAdmin_UpdateRole_Self_Returns1001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", 0));

        mockMvc.perform(put("/api/admin/users/{id}/role", adminUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ── 接口 6：DELETE /api/admin/users/{id} ──

    @Test
    @DisplayName("管理员删除用户 → 200")
    void testAdmin_DeleteUser_Returns200() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", studentUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("管理员删除自己 → 1001（不允许删除自己）")
    void testAdmin_DeleteUser_Self_Returns1001() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", adminUserId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
