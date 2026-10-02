package com.zhiyu.modules.emotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.emotion.dto.ChatDTO;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.emotion.controller.EmotionController} 单测（T1.3a）。
 *
 * <p>测试鉴权 + 参数校验。SSE 流式端到端在冒烟测试中验证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class EmotionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private String validToken(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @Test
    @DisplayName("无 token 访问 → 401")
    void testNoToken_Returns401() throws Exception {
        ChatDTO dto = new ChatDTO();
        dto.setContent("你好");
        mockMvc.perform(post("/api/emotion/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("请求体缺 content → 400 + code 1001")
    void testMissingContent_Returns400() throws Exception {
        String token = validToken(9001L, 0);
        mockMvc.perform(post("/api/emotion/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("有 token + 合法 DTO → 200 + SSE 流启动")
    void testValidToken_SseStarted() throws Exception {
        String token = validToken(9001L, 0);

        String body = "{\"sessionId\":null,\"mood\":\"焦虑\",\"content\":\"考试压力好大\"}";

        // SSE 请求应返回 200 且异步开始
        // 不做 asyncDispatch（MockMvc + Security + async 兼容性问题）
        // SSE 端到端在冒烟测试中验证
        mockMvc.perform(post("/api/emotion/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("有 token + sessionId 为空 → 200（创建新会话）")
    void testWithNullSessionId() throws Exception {
        String token = validToken(9002L, 0);

        String body = "{\"sessionId\":null,\"content\":\"第一轮对话\"}";

        mockMvc.perform(post("/api/emotion/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    // ── T1.3b 增加安全过滤场景 ──

    @Test
    @DisplayName("高危输入 → 200 + SSE 流启动（危机干预不调 LLM）")
    void testHighRiskInput_SseStarted() throws Exception {
        String token = validToken(9003L, 0);
        String body = "{\"sessionId\":null,\"mood\":\"低落\",\"content\":\"我不想活了，我想自杀\"}";

        // MockMvc + SSE + async 兼容性限制：只验证 status + asyncStarted。
        // 危机干预路径不调 LLM，响应应更快（端到端冒烟验证完整流）。
        mockMvc.perform(post("/api/emotion/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("正常输入 → 200 + SSE 流启动（走完整流程，MockLlmClient 接管）")
    void testNormalInput_SseStarted() throws Exception {
        String token = validToken(9004L, 0);
        String body = "{\"sessionId\":null,\"mood\":\"焦虑\",\"content\":\"最近考试压力大\"}";

        mockMvc.perform(post("/api/emotion/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
