package com.zhiyu.modules.craft;

import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.craft.controller.CraftController} 单测（T1.5）。
 *
 * <p>测试鉴权 + GET /api/craft/recommend 接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

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
}
