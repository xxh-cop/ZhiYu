package com.zhiyu.modules.practice;

import com.zhiyu.modules.practice.controller.PracticeSessionController;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

class PracticeSessionControllerTest {

    private PracticeSessionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PracticeSessionService.class);
        BusinessAccess access = mock(BusinessAccess.class);
        when(access.consentedUser()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(new PracticeSessionController(service, access)).build();
        UserContext.set(new LoginUser(7L, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private PracticeSessionVO vo() {
        PracticeSessionVO vo = new PracticeSessionVO();
        vo.setPracticeSessionId(99L);
        vo.setKind("HERITAGE");
        vo.setStatus(0);
        vo.setStatusLabel("已创建");
        vo.setMinutesBudget(10);
        vo.setCurrentStep(1);
        return vo;
    }

    @Test
    void createAndReadSession() throws Exception {
        when(service.create(eq(7L), any())).thenReturn(vo());
        when(service.get(7L, 99L)).thenReturn(vo());

        mockMvc.perform(post("/api/practice/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HERITAGE\",\"minutesBudget\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.practiceSessionId").value(99))
                .andExpect(jsonPath("$.data.statusLabel").value("已创建"));

        mockMvc.perform(get("/api/practice/sessions/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("HERITAGE"));
    }

    @Test
    void listRecentSessions() throws Exception {
        when(service.list(7L, 5)).thenReturn(List.of(vo()));

        mockMvc.perform(get("/api/practice/sessions").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].practiceSessionId").value(99));
    }

    @Test
    void transitionSession() throws Exception {
        PracticeSessionVO running = vo();
        running.setStatus(3);
        running.setStatusLabel("练习中");
        when(service.transition(7L, 99L, "START")).thenReturn(running);

        mockMvc.perform(put("/api/practice/sessions/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.statusLabel").value("练习中"));
    }
}
