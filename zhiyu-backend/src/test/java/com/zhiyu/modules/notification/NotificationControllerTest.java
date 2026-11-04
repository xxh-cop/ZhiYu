package com.zhiyu.modules.notification;

import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BJ（2026-11-05）：{@code GET /api/notifications} 的 refType 参数接线验证
 * （服务层筛选语义见 {@link NotificationServiceTest#refTypeFilterNarrowsWithinType}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate db;

    private String token(long uid, int role) {
        com.zhiyu.support.AuthenticatedTestUser.ensure(db, uid, role);
        return jwtUtil.createToken(uid, role);
    }

    @Test
    @DisplayName("BJ：refType 合法值 200（total 随筛选），非法值 400/1001，不传正常")
    void refTypeParamWired() throws Exception {
        String tk = token(950001L, 0);
        mockMvc.perform(get("/api/notifications?type=WORK_AUDIT&refType=project")
                        .header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/notifications?refType=bogus")
                        .header("Authorization", "Bearer " + tk))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
