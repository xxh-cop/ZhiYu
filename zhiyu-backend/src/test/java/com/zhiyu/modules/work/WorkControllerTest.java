package com.zhiyu.modules.work;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.work.controller.WorkController} 测试（T1.8a）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class WorkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtUtil.createToken(8201L, 0);
    }

    @Test
    @DisplayName("POST /api/works 无 token → 401")
    void testNoToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/works 缺 title → 400 + 1001")
    void testMissingTitle_Returns400() throws Exception {
        mockMvc.perform(post("/api/works")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("GET /api/works 有 token → 200")
    void testListWorks_Success() throws Exception {
        mockMvc.perform(get("/api/works")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
