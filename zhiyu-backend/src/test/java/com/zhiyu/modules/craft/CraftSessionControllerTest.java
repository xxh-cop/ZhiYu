package com.zhiyu.modules.craft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.craft.controller.CraftController} 会话端点测试（T1.6）。
 *
 * <p>加 @Transactional 让每个测试方法结束后回滚，避免 @BeforeEach 重复插入同 ID 数据冲突。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftStepMapper craftStepMapper;

    private static final Long PROJECT_ID = 9101L;

    @BeforeEach
    void setUp() {
        CraftProject p = new CraftProject();
        p.setId(PROJECT_ID);
        p.setInheritorId(1L);
        p.setTitle("测试编织");
        p.setLevel(1);
        p.setEstMinutes(30);
        p.setCategory("其他");
        p.setTherapyTags("专注");
        p.setStatus(1);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(0);
        craftProjectMapper.insert(p);

        CraftStep step = new CraftStep();
        step.setProjectId(PROJECT_ID);
        step.setStepNo(1);
        step.setTitle("准备");
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setDeleted(0);
        craftStepMapper.insert(step);
    }

    private String validToken(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @Test
    @DisplayName("POST /api/craft/session 无 token → 401")
    void testNoToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/craft/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":9101}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/craft/session 缺 projectId → 400 + 1001")
    void testMissingProjectId_Returns400() throws Exception {
        String token = validToken(7001L, 0);
        mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("POST /api/craft/session 有 token → 200 + CraftSessionVO")
    void testCreateSession_Success() throws Exception {
        String token = validToken(7001L, 0);
        mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":9101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andExpect(jsonPath("$.data.projectId").value(9101))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusLabel").value("进行中"))
                .andExpect(jsonPath("$.data.currentStep").value(1))
                .andExpect(jsonPath("$.data.totalSteps").value(1));
    }
}
