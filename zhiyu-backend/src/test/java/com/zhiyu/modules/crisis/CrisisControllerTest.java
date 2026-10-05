package com.zhiyu.modules.crisis;

import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link com.zhiyu.modules.crisis.controller.CrisisController} 单测（M3-B1）。
 *
 * <p>覆盖 3 个场景：
 * <ul>
 *   <li>未登录访问 /api/crisis/hotlines → 200（公开接口）</li>
 *   <li>已登录访问 → 200，返回号码与名称</li>
 *   <li>返回体字段完整性校验</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrisisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("未登录获取心理援助热线 → 200（公开接口）")
    void testAnonymous_GetHotlines_Returns200() throws Exception {
        mockMvc.perform(get("/api/crisis/hotlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.psychCenterPhone").isNotEmpty())
                .andExpect(jsonPath("$.data.nationalHotline").isNotEmpty())
                .andExpect(jsonPath("$.data.psychCenterName").isNotEmpty())
                .andExpect(jsonPath("$.data.nationalHotlineName").isNotEmpty());
    }

    @Test
    @DisplayName("已登录获取心理援助热线 → 200，返回号码与名称")
    void testLoggedIn_GetHotlines_Returns200() throws Exception {
        String token = jwtUtil.createToken(99999L, 0);
        mockMvc.perform(get("/api/crisis/hotlines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.psychCenterPhone").value("0791-0000000"))
                .andExpect(jsonPath("$.data.nationalHotline").value("12356"))
                .andExpect(jsonPath("$.data.psychCenterName").value("学校心理中心"))
                .andExpect(jsonPath("$.data.nationalHotlineName").value("全国心理援助热线"));
    }

    @Test
    @DisplayName("返回体字段完整性校验")
    void testGetHotlines_FieldCompleteness() throws Exception {
        mockMvc.perform(get("/api/crisis/hotlines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.psychCenterPhone").isString())
                .andExpect(jsonPath("$.data.nationalHotline").isString())
                .andExpect(jsonPath("$.data.psychCenterName").isString())
                .andExpect(jsonPath("$.data.nationalHotlineName").isString());
    }
}
