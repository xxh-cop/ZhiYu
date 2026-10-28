package com.zhiyu.modules.craft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.vo.RecommendItemVO;
import com.zhiyu.modules.craft.vo.RecommendVO;
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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.craft.controller.CraftController} 单测（T1.5 + 批次 6-F）。
 *
 * <p>覆盖鉴权 + GET /api/craft/recommend + GET /api/craft/projects/{id}/steps
 * + POST /api/craft/session + POST /api/craft/session/{id}/heartbeat。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CraftControllerTest {
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

    private static final Long PROJECT_ID = 8101L;

    @BeforeEach
    void setUp() {
        CraftProject p = new CraftProject();
        p.setId(PROJECT_ID);
        p.setInheritorId(1L);
        p.setTitle("测试编织项目");
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
        step.setTitle("准备材料");
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setDeleted(0);
        craftStepMapper.insert(step);
    }

    private String validToken(Long userId, int role) {
        com.zhiyu.support.AuthenticatedTestUser.ensure(authFixtureDb,userId,role);
        return jwtUtil.createToken(userId, role);
    }

    // ── 原有 2 个用例 ──

    @Test
    @DisplayName("无 token → 401")
    void testNoToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/craft/recommend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("有 token + GET /api/craft/recommend → 200 + RecommendVO 结构")
    void testValidToken_ReturnsRecommendVO() throws Exception {
        String token = validToken(8001L, 0);

        mockMvc.perform(get("/api/craft/recommend")
                        .header("Authorization", "Bearer " + token)
                        .param("skillLevel", "1")
                        .param("minutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.recommendationId").exists())
                .andExpect(jsonPath("$.data.mood").exists())
                .andExpect(jsonPath("$.data.reason").exists())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("推荐 ID 以字符串输出，避免 JS 精度丢失")
    void recommendationIdsSerializeAsStrings() throws Exception {
        RecommendItemVO item = new RecommendItemVO();
        item.setProjectId(940001L);
        RecommendVO vo = new RecommendVO();
        vo.setRecommendationId(900000000000000001L);
        vo.setItems(List.of(item));

        String json = objectMapper.writeValueAsString(vo);

        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"recommendationId\":\"900000000000000001\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"projectId\":\"940001\""));
    }

    @Test
    @DisplayName("推荐点击写入事件并保持幂等")
    void recommendClickIsRecordedOnce() throws Exception {
        long userId = 8004L;
        long recommendationId = 8201L;
        long projectId = 8202L;
        String token = validToken(userId, 0);
        authFixtureDb.update("INSERT INTO craft_recommendation(id,user_id,mood,reason,deleted) VALUES(?,?,?,?,0)",
                recommendationId, userId, "stressed", "test");
        authFixtureDb.update("INSERT INTO craft_recommendation_item(id,recommendation_id,project_id,score,deleted) VALUES(?,?,?,?,0)",
                8203L, recommendationId, projectId, 1.0);
        String body = objectMapper.writeValueAsString(Map.of(
                "recommendationId", String.valueOf(recommendationId),
                "projectId", String.valueOf(projectId),
                "source", "HOME"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/craft/recommend/click")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        Integer count = authFixtureDb.queryForObject(
                "SELECT COUNT(*) FROM craft_recommend_event WHERE user_id=? AND recommendation_id=? AND event_type='CLICK'",
                Integer.class, userId, recommendationId);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    // ── 批次 6-F 新增用例 ──

    @Test
    @DisplayName("有 token + GET /api/craft/recommend 无参数 → 200 + RecommendVO 结构")
    void testRecommend_NoParams_Returns200() throws Exception {
        String token = validToken(8002L, 0);

        mockMvc.perform(get("/api/craft/recommend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.recommendationId").exists())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("查不存在项目的步骤 → 404 + 1004")
    void testListSteps_NotFound_Returns1004() throws Exception {
        String token = validToken(8003L, 0);

        mockMvc.perform(get("/api/craft/projects/{id}/steps", 99999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("查已上架项目的步骤 → 200 + 步骤列表")
    void testListSteps_Success_Returns200() throws Exception {
        String token = validToken(8004L, 0);

        mockMvc.perform(get("/api/craft/projects/{id}/steps", PROJECT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].stepNo").value(1))
                .andExpect(jsonPath("$.data[0].title").value("准备材料"));
    }

    @Test
    @DisplayName("创建 session 时 projectId 不存在 → 404 + 1004")
    void testCreateSession_ProjectNotFound_Returns1004() throws Exception {
        String token = validToken(8005L, 0);
        String body = objectMapper.writeValueAsString(Map.of("projectId", 99999L));

        mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("创建 session → 200 + heartbeat 心跳上报 → 200 + inFlow/needBreathing")
    void testCreateSession_AndHeartbeat_Returns200() throws Exception {
        String token = validToken(8006L, 0);

        // 1. 创建 session
        String createBody = objectMapper.writeValueAsString(Map.of("projectId", PROJECT_ID));

        String response = mockMvc.perform(post("/api/craft/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn().getResponse().getContentAsString();

        // 提取 sessionId
        Long sessionId = objectMapper.readTree(response).at("/data/sessionId").asLong();

        // 2. heartbeat 上报
        String heartbeatBody = objectMapper.writeValueAsString(Map.of(
                "stepNo", 1,
                "dwellSeconds", 10
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
