package com.zhiyu.modules.craft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.craft.controller.CraftController} 会话端点测试（T1.6 + 批次 6-F）。
 *
 * <p>加 @Transactional 让每个测试方法结束后回滚，避免 @BeforeEach 重复插入同 ID 数据冲突。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftSessionControllerTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate authFixtureDb;

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

    @Autowired
    private CraftSessionMapper craftSessionMapper;

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
        com.zhiyu.support.AuthenticatedTestUser.ensure(authFixtureDb,userId,role);
        return jwtUtil.createToken(userId, role);
    }

    // ── 原有 3 个用例 ──

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

    // ── 批次 6-F 新增用例 ──

    @Test
    @DisplayName("非法状态流转（FINISHED → pause）→ 200 + 3001")
    void testIllegalStateTransition_Returns3001() throws Exception {
        String token = validToken(7101L, 0);

        // 1. 创建 session（DOING=1）
        String createBody = objectMapper.writeValueAsString(Map.of("projectId", PROJECT_ID));
        String resp = mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = objectMapper.readTree(resp).at("/data/sessionId").asLong();

        // 2. finish（DOING → FINISHED，合法）
        String finishBody = objectMapper.writeValueAsString(Map.of("action", "finish"));
        mockMvc.perform(put("/api/craft/session/{id}/status", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finishBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.statusLabel").value("已完成"));

        // 3. 再 pause（FINISHED → PAUSED，非法）→ 3001
        String pauseBody = objectMapper.writeValueAsString(Map.of("action", "pause"));
        mockMvc.perform(put("/api/craft/session/{id}/status", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pauseBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    @DisplayName("操作非本人 session → 403 + 1003")
    void testOperateOtherUserSession_Returns1003() throws Exception {
        // 用户 A 创建 session
        String tokenA = validToken(7201L, 0);
        String createBody = objectMapper.writeValueAsString(Map.of("projectId", PROJECT_ID));
        String resp = mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = objectMapper.readTree(resp).at("/data/sessionId").asLong();

        // 用户 B 尝试查询该 session → 403
        String tokenB = validToken(7202L, 0);
        mockMvc.perform(get("/api/craft/session/{id}", sessionId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("heartbeat 上报 isExited=true → 200 + 正确处理")
    void testHeartbeat_IsExited_Returns200() throws Exception {
        String token = validToken(7301L, 0);

        // 1. 创建 session
        String createBody = objectMapper.writeValueAsString(Map.of("projectId", PROJECT_ID));
        String resp = mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = objectMapper.readTree(resp).at("/data/sessionId").asLong();

        // 2. heartbeat 上报 isExited=true
        String heartbeatBody = objectMapper.writeValueAsString(Map.of(
                "stepNo", 1,
                "dwellSeconds", 30,
                "isExited", true
        ));

        mockMvc.perform(post("/api/craft/session/{id}/heartbeat", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(heartbeatBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.inFlow").exists())
                .andExpect(jsonPath("$.data.needBreathing").exists());
    }
}
