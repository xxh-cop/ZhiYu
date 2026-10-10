package com.zhiyu.modules.inheritor;

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
 * 传承人自更新资料接口单测（批次 6-A）。
 *
 * <p>覆盖 GET /api/inheritor/profile 和 PUT /api/inheritor/profile。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class InheritorProfileSelfControllerTest {

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

    private Long inheritorUserId;
    private Long studentUserId;
    private Long profileId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        // 传承人用户
        User inheritor = new User();
        inheritor.setUsername("inheritor_profile_test");
        inheritor.setPassword("$2a$10$dummyhash");
        inheritor.setNickname("传承人丙");
        inheritor.setRole(1);
        inheritor.setStatus(1);
        inheritor.setCanContact(0);
        userMapper.insert(inheritor);
        inheritorUserId = inheritor.getId();

        // 传承人 profile（skill 留空，模拟首次登录）
        InheritorProfile profile = new InheritorProfile();
        profile.setUserId(inheritorUserId);
        profile.setSkill("");
        profile.setIntro("");
        profile.setHonor("");
        profile.setVerified(0);
        inheritorProfileMapper.insert(profile);
        profileId = profile.getId();

        // 学生用户
        User student = new User();
        student.setUsername("student_profile_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生乙");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();
    }

    // ── GET /api/inheritor/profile ──

    @Test
    @DisplayName("GET 未登录 → 401")
    void testGetProfile_Unauthorized_Returns401() throws Exception {
        mockMvc.perform(get("/api/inheritor/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET 学生（role=0）→ 403")
    void testGetProfile_Student_Returns403() throws Exception {
        mockMvc.perform(get("/api/inheritor/profile")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET 传承人 → 200 + 数据")
    void testGetProfile_Inheritor_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritor/profile")
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(profileId))
                .andExpect(jsonPath("$.data.userId").value(inheritorUserId))
                .andExpect(jsonPath("$.data.skill").value(""))
                .andExpect(jsonPath("$.data.verified").value(0));
    }

    // ── PUT /api/inheritor/profile ──

    @Test
    @DisplayName("PUT 更新 skill/intro/honor → 200 + 数据验证")
    void testPutProfile_UpdateAll_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "skill", "赣绣",
                "intro", "从事赣绣技艺 20 年",
                "honor", "省级非遗传承人"));

        mockMvc.perform(put("/api/inheritor/profile")
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skill").value("赣绣"))
                .andExpect(jsonPath("$.data.intro").value("从事赣绣技艺 20 年"))
                .andExpect(jsonPath("$.data.honor").value("省级非遗传承人"));

        // 数据库验证
        InheritorProfile updated = inheritorProfileMapper.selectById(profileId);
        assert updated.getSkill().equals("赣绣");
        assert updated.getIntro().equals("从事赣绣技艺 20 年");
        assert updated.getHonor().equals("省级非遗传承人");
    }

    @Test
    @DisplayName("PUT 更新 avatarUrl → user.avatar_url 同步")
    void testPutProfile_UpdateAvatar_SyncUser() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "avatarUrl", "/uploads/images/2026/10/10/test.jpg"));

        mockMvc.perform(put("/api/inheritor/profile")
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl").value("/uploads/images/2026/10/10/test.jpg"));

        // 验证 user 表同步
        User user = userMapper.selectById(inheritorUserId);
        assert user.getAvatarUrl().equals("/uploads/images/2026/10/10/test.jpg");
    }

    @Test
    @DisplayName("PUT 部分更新（只传 skill）→ 其他字段不变")
    void testPutProfile_PartialUpdate_Returns200() throws Exception {
        // 先设置初始值
        InheritorProfile profile = inheritorProfileMapper.selectById(profileId);
        profile.setSkill("初始技艺");
        profile.setIntro("初始简介");
        profile.setHonor("初始荣誉");
        inheritorProfileMapper.updateById(profile);

        // 只更新 skill
        String body = objectMapper.writeValueAsString(Map.of("skill", "新技艺"));

        mockMvc.perform(put("/api/inheritor/profile")
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skill").value("新技艺"))
                .andExpect(jsonPath("$.data.intro").value("初始简介"))
                .andExpect(jsonPath("$.data.honor").value("初始荣誉"));

        // 数据库验证
        InheritorProfile updated = inheritorProfileMapper.selectById(profileId);
        assert updated.getSkill().equals("新技艺");
        assert updated.getIntro().equals("初始简介");
        assert updated.getHonor().equals("初始荣誉");
    }

    @Test
    @DisplayName("PUT 学生（role=0）→ 403")
    void testPutProfile_Student_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skill", "hack"));

        mockMvc.perform(put("/api/inheritor/profile")
                        .header("Authorization", "Bearer " + token(studentUserId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
