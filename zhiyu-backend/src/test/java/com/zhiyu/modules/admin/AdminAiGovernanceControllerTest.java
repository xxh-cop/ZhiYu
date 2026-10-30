package com.zhiyu.modules.admin;

import com.zhiyu.modules.admin.controller.AdminAiGovernanceController;
import com.zhiyu.modules.admin.service.AdminAiGovernanceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAiGovernanceControllerTest {

    @Test
    void overviewReturnsAggregateAndConfigStatus() throws Exception {
        AdminAiGovernanceService service = mock(AdminAiGovernanceService.class);
        when(service.overview(30)).thenReturn(Map.of(
                "windowDays", 30,
                "totalEvents", 12L,
                "actionCounts", List.of(Map.of("name", "PASS", "value", 9L)),
                "config", Map.of("chatMode", "MOCK", "chatVerified", false),
                "privacyNote", "不返回用户 ID 与原文"
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminAiGovernanceController(service)).build();

        mockMvc.perform(get("/api/admin/ai-governance/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.totalEvents").value(12))
                .andExpect(jsonPath("$.data.config.chatMode").value("MOCK"))
                .andExpect(jsonPath("$.data.privacyNote").value("不返回用户 ID 与原文"));
    }
}
