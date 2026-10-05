package com.zhiyu.modules.auth;

import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UserPrivacyController} 单测（M3-B1）。
 *
 * <p>覆盖 6 个场景：
 * <ul>
 *   <li>查询隐私状态（默认未同意）→ 200</li>
 *   <li>同意隐私政策 → 200，再查状态 → privacyAgreed=1</li>
 *   <li>撤回隐私政策 → 200，privacyAgreed=0 且 canContact=0</li>
 *   <li>未同意隐私政策时设 canContact=1 → 1001</li>
 *   <li>已同意后设 canContact=1 → 200</li>
 *   <li>注销账号 confirm=false → 1001；confirm=true → 200，用户被逻辑删除</li>
 *   <li>未登录访问 → 401</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserPrivacyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    private Long studentUserId;
    private String token;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setUsername("privacy_test_user");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("隐私测试学生");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        student.setPrivacyAgreed(0);
        userMapper.insert(student);
        studentUserId = student.getId();
        token = jwtUtil.createToken(studentUserId, 0);
    }

    // ── 接口 1：GET /api/user/privacy/status ──

    @Test
    @DisplayName("查询隐私状态（默认未同意）→ 200")
    void testGetPrivacyStatus_Default_Returns200() throws Exception {
        mockMvc.perform(get("/api/user/privacy/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.privacyAgreed").value(0))
                .andExpect(jsonPath("$.data.canContact").value(0));
    }

    @Test
    @DisplayName("未登录查询隐私状态 → 401")
    void testAnonymous_GetPrivacyStatus_Returns401() throws Exception {
        mockMvc.perform(get("/api/user/privacy/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    // ── 接口 2：POST /api/user/privacy/agree ──

    @Test
    @DisplayName("同意隐私政策 → 200，再查状态 privacyAgreed=1")
    void testAgreePrivacy_Returns200() throws Exception {
        mockMvc.perform(post("/api/user/privacy/agree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证状态已更新
        mockMvc.perform(get("/api/user/privacy/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.privacyAgreed").value(1));
    }

    // ── 接口 3：POST /api/user/privacy/withdraw ──

    @Test
    @DisplayName("撤回隐私政策 → 200，privacyAgreed=0 且 canContact=0")
    void testWithdrawPrivacy_Returns200() throws Exception {
        // 先同意
        mockMvc.perform(post("/api/user/privacy/agree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 再撤回
        mockMvc.perform(post("/api/user/privacy/withdraw")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证状态
        mockMvc.perform(get("/api/user/privacy/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.privacyAgreed").value(0))
                .andExpect(jsonPath("$.data.canContact").value(0));
    }

    // ── 接口 4：PUT /api/user/can-contact ──

    @Test
    @DisplayName("未同意隐私政策时设 canContact=1 → 1001")
    void testSetCanContact_WithoutPrivacyAgreed_Returns1001() throws Exception {
        mockMvc.perform(put("/api/user/can-contact")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"canContact\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("已同意隐私政策后设 canContact=1 → 200")
    void testSetCanContact_WithPrivacyAgreed_Returns200() throws Exception {
        // 先同意
        mockMvc.perform(post("/api/user/privacy/agree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 再设 canContact=1
        mockMvc.perform(put("/api/user/can-contact")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"canContact\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证状态
        mockMvc.perform(get("/api/user/privacy/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.canContact").value(1));
    }

    // ── 接口 5：POST /api/user/delete ──

    @Test
    @DisplayName("注销账号 confirm=false → 1001")
    void testDeleteAccount_ConfirmFalse_Returns1001() throws Exception {
        mockMvc.perform(post("/api/user/delete")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirm\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("注销账号 confirm=true → 200，用户被逻辑删除")
    void testDeleteAccount_ConfirmTrue_Returns200() throws Exception {
        mockMvc.perform(post("/api/user/delete")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证用户已被逻辑删除（用旧 token 再调接口 → 1002）
        mockMvc.perform(get("/api/user/privacy/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }
}
