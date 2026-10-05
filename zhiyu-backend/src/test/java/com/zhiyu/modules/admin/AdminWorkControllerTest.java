package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
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
 * {@link com.zhiyu.modules.admin.controller.AdminWorkController} 单测（P1-B1）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class AdminWorkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkMapper workMapper;

    private Long adminUserId;
    private Long studentUserId;
    private Long workId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setUsername("admin_work_test");
        admin.setPassword("$2a$10$dummyhash");
        admin.setNickname("管理员");
        admin.setRole(3);
        admin.setStatus(1);
        admin.setCanContact(0);
        userMapper.insert(admin);
        adminUserId = admin.getId();

        User student = new User();
        student.setUsername("student_work_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        Work work = new Work();
        work.setUserId(studentUserId);
        work.setTitle("测试作品");
        work.setCoverUrl("/static/cover.jpg");
        work.setMessage("AI寄语");
        work.setVisible(1);
        work.setAnonymous(0);
        work.setLikeCount(0);
        work.setStatus(1);
        workMapper.insert(work);
        workId = work.getId();
    }

    // ── 接口 5：GET /api/admin/works ──

    @Test
    @DisplayName("管理员分页查询作品列表 → 200")
    void testAdmin_ListWorks_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/works")
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("学生访问作品列表 → 403")
    void testStudent_ListWorks_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/works")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isForbidden());
    }

    // ── 接口 6：GET /api/admin/works/{id} ──

    @Test
    @DisplayName("管理员查询作品详情 → 200")
    void testAdmin_GetWork_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/works/{id}", workId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(workId))
                .andExpect(jsonPath("$.data.title").value("测试作品"));
    }

    @Test
    @DisplayName("管理员查询不存在的作品 → 1004")
    void testAdmin_GetWork_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/admin/works/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 7：PUT /api/admin/works/{id}/status ──

    @Test
    @DisplayName("管理员下架作品 → 200")
    void testAdmin_UpdateWorkStatus_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", 2));

        mockMvc.perform(put("/api/admin/works/{id}/status", workId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("咨询师下架作品 → 403（仅管理员）")
    void testCounselor_UpdateWorkStatus_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", 2));

        mockMvc.perform(put("/api/admin/works/{id}/status", workId)
                        .header("Authorization", "Bearer " + token(99L, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 接口 8：DELETE /api/admin/works/{id} ──

    @Test
    @DisplayName("管理员删除作品 → 200")
    void testAdmin_DeleteWork_Returns200() throws Exception {
        mockMvc.perform(delete("/api/admin/works/{id}", workId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("管理员删除不存在的作品 → 1004")
    void testAdmin_DeleteWork_NotFound_Returns1004() throws Exception {
        mockMvc.perform(delete("/api/admin/works/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
