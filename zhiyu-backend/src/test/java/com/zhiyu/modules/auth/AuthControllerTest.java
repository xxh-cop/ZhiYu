package com.zhiyu.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.auth.controller.AuthController} 测试（批次 3）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>注册成功 → 200 + code=0</li>
 *   <li>username 过短（<3）→ 1001</li>
 *   <li>username 过长（>50）→ 1001</li>
 *   <li>password 过短（<6）→ 1001</li>
 *   <li>password 过长（>32）→ 1001</li>
 *   <li>nickname 缺失 → 1001</li>
 *   <li>nickname 过长（>50）→ 1001</li>
 *   <li>school 过长（>100）→ 1001</li>
 *   <li>注册后新用户默认字段正确（role=0, status=1, can_contact=0, privacy_agreed=0）</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    private static final String API_REGISTER = "/api/auth/register";

    private String registerJson(String username, String nickname, String school, String password) throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setNickname(nickname);
        dto.setSchool(school);
        dto.setPassword(password);
        return objectMapper.writeValueAsString(dto);
    }

    @Test
    @DisplayName("注册成功 → 200 + code=0")
    void testRegister_Success() throws Exception {
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("testuser_ok", "小测", "测试大学", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("注册成功后新用户默认字段正确（role=0, status=1, can_contact=0, privacy_agreed=0）")
    void testRegister_DefaultFields() throws Exception {
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("testuser_defaults", "默认值用户", null, "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User saved = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, "testuser_defaults"));
        assertNotNull(saved);
        assertEquals(0, saved.getRole(), "新注册用户 role 应为 0（学生）");
        assertEquals(1, saved.getStatus(), "新注册用户 status 应为 1（正常）");
        assertEquals(0, saved.getCanContact(), "新注册用户 can_contact 应为 0");
        assertEquals(0, saved.getPrivacyAgreed(), "新注册用户 privacy_agreed 应为 0");
    }

    // ── 参数校验 ──

    @Test
    @DisplayName("username 过短（<3）→ 1001")
    void testRegister_UsernameTooShort() throws Exception {
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("ab", "小测", null, "pass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("username 过长（>50）→ 1001")
    void testRegister_UsernameTooLong() throws Exception {
        String longName = "a".repeat(51);
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(longName, "小测", null, "pass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("password 过短（<6）→ 1001")
    void testRegister_PasswordTooShort() throws Exception {
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("testuser_short", "小测", null, "12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("password 过长（>32）→ 1001")
    void testRegister_PasswordTooLong() throws Exception {
        String longPwd = "a".repeat(33);
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("testuser_longpwd", "小测", null, longPwd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("nickname 缺失 → 1001")
    void testRegister_NicknameMissing() throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("testuser_nonick");
        dto.setPassword("pass123");
        // nickname 不设置 → @NotBlank 触发
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("nickname 过长（>50）→ 1001")
    void testRegister_NicknameTooLong() throws Exception {
        String longNick = "测".repeat(51);
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("testuser_longnick", longNick, null, "pass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("school 过长（>100）→ 1001")
    void testRegister_SchoolTooLong() throws Exception {
        String longSchool = "校".repeat(101);
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("testuser_longschool", "小测", longSchool, "pass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("username 为空 → 1001")
    void testRegister_UsernameBlank() throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("");
        dto.setNickname("小测");
        dto.setPassword("pass123");
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("password 为空 → 1001")
    void testRegister_PasswordBlank() throws Exception {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("testuser_nopwd");
        dto.setNickname("小测");
        dto.setPassword("");
        mockMvc.perform(post(API_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
