package com.zhiyu.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.util.PasswordUtil;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserProfileController 测试（批次 4.5）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>更新昵称 → 200 + 数据库验证</li>
 *   <li>更新头像 URL → 200</li>
 *   <li>更新学校 → 200</li>
 *   <li>更新简介 → 200</li>
 *   <li>部分字段更新（只传 nickname）→ 其他字段不变</li>
 *   <li>未登录 → 401</li>
 *   <li>昵称超长 → 1001</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    private static final Long USER_ID = 8601L;
    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("test_profile_user");
        user.setPassword(PasswordUtil.encode("pass123"));
        user.setNickname("测试用户");
        user.setRole(0);
        user.setStatus(1);
        user.setCanContact(0);
        user.setPrivacyAgreed(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);

        token = jwtUtil.createToken(USER_ID, 0);
    }

    @Test
    @DisplayName("更新昵称 → 200 + 数据库验证")
    void testUpdateNickname_Success() throws Exception {
        String body = "{\"nickname\":\"新昵称\"}";

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        User saved = userMapper.selectById(USER_ID);
        assertEquals("新昵称", saved.getNickname());
    }

    @Test
    @DisplayName("更新头像 URL → 200")
    void testUpdateAvatarUrl_Success() throws Exception {
        String body = "{\"avatarUrl\":\"/uploads/images/test/avatar.jpg\"}";

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl").value("/uploads/images/test/avatar.jpg"));
    }

    @Test
    @DisplayName("更新学校 → 200")
    void testUpdateSchool_Success() throws Exception {
        String body = "{\"school\":\"江西师范大学\"}";

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.school").value("江西师范大学"));
    }

    @Test
    @DisplayName("更新简介 → 200")
    void testUpdateIntro_Success() throws Exception {
        String body = "{\"intro\":\"热爱非遗手作的同学\"}";

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.intro").value("热爱非遗手作的同学"));
    }

    @Test
    @DisplayName("部分字段更新（只传 nickname）→ 其他字段不变")
    void testPartialUpdate_OnlyNickname() throws Exception {
        // 先设置一些初始值
        User user = userMapper.selectById(USER_ID);
        user.setSchool("原学校");
        user.setIntro("原简介");
        userMapper.updateById(user);

        // 只更新 nickname
        String body = "{\"nickname\":\"只改昵称\"}";
        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("只改昵称"));

        // 其他字段不变
        User saved = userMapper.selectById(USER_ID);
        assertEquals("原学校", saved.getSchool());
        assertEquals("原简介", saved.getIntro());
    }

    @Test
    @DisplayName("未登录 → 401")
    void testUpdateProfile_Unauthorized() throws Exception {
        String body = "{\"nickname\":\"未登录\"}";

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("昵称超长（>50）→ 1001")
    void testUpdateNickname_TooLong() throws Exception {
        String longNick = "测".repeat(51);
        String body = objectMapper.writeValueAsString(java.util.Map.of("nickname", longNick));

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
