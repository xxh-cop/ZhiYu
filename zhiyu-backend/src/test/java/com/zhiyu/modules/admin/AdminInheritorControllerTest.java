package com.zhiyu.modules.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
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
 * {@link com.zhiyu.modules.admin.controller.AdminInheritorController} 单测（P1-B1）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class AdminInheritorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private InheritorProfileMapper inheritorProfileMapper;

    private Long adminUserId;
    private Long studentUserId;
    private Long inheritorProfileId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setUsername("admin_inh_test");
        admin.setPassword("$2a$10$dummyhash");
        admin.setNickname("管理员");
        admin.setRole(3);
        admin.setStatus(1);
        admin.setCanContact(0);
        userMapper.insert(admin);
        adminUserId = admin.getId();

        User inheritorUser = new User();
        inheritorUser.setUsername("inheritor_test");
        inheritorUser.setPassword("$2a$10$dummyhash");
        inheritorUser.setNickname("赣绣老师");
        inheritorUser.setRole(1);
        inheritorUser.setStatus(1);
        inheritorUser.setCanContact(0);
        userMapper.insert(inheritorUser);

        User student = new User();
        student.setUsername("student_inh_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        InheritorProfile profile = new InheritorProfile();
        profile.setUserId(inheritorUser.getId());
        profile.setSkill("赣绣");
        profile.setIntro("从事赣绣四十余年");
        profile.setHonor("省级非遗传承人");
        profile.setVerified(0);
        inheritorProfileMapper.insert(profile);
        inheritorProfileId = profile.getId();
    }

    // ── 接口 1：GET /api/admin/inheritors ──

    @Test
    @DisplayName("管理员分页查询传承人列表 → 200")
    void testAdmin_ListInheritors_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/inheritors")
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("学生访问传承人列表 → 403")
    void testStudent_ListInheritors_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/inheritors")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isForbidden());
    }

    // ── 接口 2：GET /api/admin/inheritors/{id} ──

    @Test
    @DisplayName("管理员查询传承人详情 → 200")
    void testAdmin_GetInheritor_Returns200() throws Exception {
        mockMvc.perform(get("/api/admin/inheritors/{id}", inheritorProfileId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(inheritorProfileId))
                .andExpect(jsonPath("$.data.skill").value("赣绣"));
    }

    @Test
    @DisplayName("管理员查询不存在的传承人 → 1004")
    void testAdmin_GetInheritor_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/admin/inheritors/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 3：PUT /api/admin/inheritors/{id}/verify ──

    @Test
    @DisplayName("管理员认证传承人 → 200")
    void testAdmin_VerifyInheritor_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("verified", 1));

        mockMvc.perform(put("/api/admin/inheritors/{id}/verify", inheritorProfileId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("咨询师认证传承人 → 403（仅管理员）")
    void testCounselor_VerifyInheritor_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("verified", 1));

        mockMvc.perform(put("/api/admin/inheritors/{id}/verify", inheritorProfileId)
                        .header("Authorization", "Bearer " + token(99L, 2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 接口 4：PUT /api/admin/inheritors/{id} ──

    @Test
    @DisplayName("管理员编辑传承人资料 → 200")
    void testAdmin_UpdateInheritor_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skill", "陶艺", "honor", "国家级传承人"));

        mockMvc.perform(put("/api/admin/inheritors/{id}", inheritorProfileId)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skill").value("陶艺"));
    }

    @Test
    @DisplayName("管理员编辑不存在的传承人 → 1004")
    void testAdmin_UpdateInheritor_NotFound_Returns1004() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skill", "陶艺"));

        mockMvc.perform(put("/api/admin/inheritors/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(adminUserId, 3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
