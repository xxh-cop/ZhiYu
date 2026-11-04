package com.zhiyu.modules.practice;

import com.zhiyu.modules.practice.controller.AdaptivePracticeController;
import com.zhiyu.modules.practice.service.AdaptivePracticeService;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdaptivePracticeControllerTest {

    private AdaptivePracticeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdaptivePracticeService.class);
        BusinessAccess access = mock(BusinessAccess.class);
        when(access.consentedUser()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdaptivePracticeController(service, access)).build();
        UserContext.set(new LoginUser(7L, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void returnsAdaptivePlan() throws Exception {
        when(service.plan(7L, 10)).thenReturn(new AdaptivePlanVO(
                10, "入门", 0.5, 1, 1, "MANUAL", true, false,
                "按最近记录安排。只调整练习节奏，不作情绪或疗效判断。",
                List.of(new AdaptivePlanVO.Item("971201", "经纬初见", "竹编（瑞昌竹编）",
                        "weave", "编织", 8, "入门", "focus",
                        List.of("#F5F0DE", "#4B503C", "#A9A477", "#CFB584"), "8 分钟"))));

        mockMvc.perform(get("/api/practice/adaptive-plan").param("minutes", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minutes").value(10))
                .andExpect(jsonPath("$.data.levelBias").value("入门"))
                .andExpect(jsonPath("$.data.generated").value(false))
                .andExpect(jsonPath("$.data.items[0].patternId").value("971201"));
    }
}
