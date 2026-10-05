package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.group.entity.SupportGroup;
import com.zhiyu.modules.group.mapper.SupportGroupMapper;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.admin.controller.AdminGroupController} 单测（P1-B1）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class AdminGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SupportGroupMapper supportGroupMapper;

    private Long adminUserId;
    private Long studentUserId;
    private Long groupId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setUsername("admin_grp_test");
        admin.setPassword("$2a$10$dummyhash");
        admin.setNickname("管理员");
        admin.setRole(3);
        admin.setStatus(1);
        admin.setCanContact(0);
        userMapper.insert(admin);
        adminUserId = admin.getId();

        User student = new User();
        student.setUsername("student_grp_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        SupportGroup group = new SupportGroup();
        group.setName("测试小组");
        group.setDescription("一个测试用小组");
        group.setMaxCount(50);
        group.setMemberCount(1);
        group.setStatus(1);
        supportGroupMapper.insert(group);
        groupId = group.getId();
    }

    // ── 接口 9：GET /api/admin/groups ──

    @Test
    @DisplayName("管理员分页查询小组列表 → 200")
    void testAdmin_ListGroups_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/groups")
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("学生访问小组列表 → 403")
    void testStudent_ListGroups_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/groups")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isForbidden());
    }

    // ── 接口 10：GET /api/admin/groups/{id} ──

    @Test
    @DisplayName("管理员查询小组详情 → 200")
    void testAdmin_GetGroup_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(groupId))
                .andExpect(jsonPath("$.data.name").value("测试小组"));
    }

    @Test
    @DisplayName("管理员查询不存在的小组 → 1004")
    void testAdmin_GetGroup_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/admin/groups/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 11：PUT /api/admin/groups/{id}/status ──

    @Test
    @DisplayName("管理员关闭小组 → 200")
    void testAdmin_UpdateGroupStatus_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", 0));

        mockMvc.perform(put("/api/admin/groups/{id}/status", groupId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("咨询师关闭小组 → 403（仅管理员）")
    void testCounselor_UpdateGroupStatus_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", 0));

        mockMvc.perform(put("/api/admin/groups/{id}/status", groupId)
                        .header("Authorization", "Bearer " + token(99L, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 接口 12：DELETE /api/admin/groups/{id} ──

    @Test
    @DisplayName("管理员删除小组 → 200")
    void testAdmin_DeleteGroup_Returns200() throws Exception {
        mockMvc.perform(delete("/api/admin/groups/{id}", groupId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("管理员删除不存在的小组 → 1004")
    void testAdmin_DeleteGroup_NotFound_Returns1004() throws Exception {
        mockMvc.perform(delete("/api/admin/groups/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
