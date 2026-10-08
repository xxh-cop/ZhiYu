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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PasswordChange 测试（批次 4.5）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>正确旧密码 + 新密码 → 200</li>
 *   <li>旧密码错 → 2001</li>
 *   <li>新密码过短 → 1001</li>
 *   <li>未登录 → 401</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PasswordChangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    private static final Long USER_ID = 8602L;
    private String token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("test_pwd_user");
        user.setPassword(PasswordUtil.encode("oldpass123"));
        user.setNickname("密码测试用户");
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
    @DisplayName("正确旧密码 + 新密码 → 200")
    void testChangePassword_Success() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "oldPassword", "oldpass123",
                "newPassword", "newpass456"
        ));

        mockMvc.perform(put("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证数据库里的新密码能匹配
        User saved = userMapper.selectById(USER_ID);
        assertTrue(PasswordUtil.matches("newpass456", saved.getPassword()));
    }

    @Test
    @DisplayName("旧密码错 → 2001")
    void testChangePassword_WrongOldPassword() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "oldPassword", "wrongpassword",
                "newPassword", "newpass456"
        ));

        mockMvc.perform(put("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("新密码过短（<6）→ 1001")
    void testChangePassword_NewPasswordTooShort() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "oldPassword", "oldpass123",
                "newPassword", "12345"
        ));

        mockMvc.perform(put("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("未登录 → 401")
    void testChangePassword_Unauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "oldPassword", "oldpass123",
                "newPassword", "newpass456"
        ));

        mockMvc.perform(put("/api/user/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
