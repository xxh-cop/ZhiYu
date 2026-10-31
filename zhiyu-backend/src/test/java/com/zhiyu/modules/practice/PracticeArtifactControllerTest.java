package com.zhiyu.modules.practice;

import com.zhiyu.modules.practice.controller.PracticeArtifactController;
import com.zhiyu.modules.practice.service.PracticeArtifactService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PracticeArtifactControllerTest {

    private PracticeArtifactService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PracticeArtifactService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PracticeArtifactController(service)).build();
        UserContext.set(new LoginUser(7L, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void saveArtifact() throws Exception {
        when(service.save(eq(7L), eq(99L), any()))
                .thenReturn(Map.of("saved", true, "patternName", "经纬初见"));

        mockMvc.perform(post("/api/practice/sessions/99/artifact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consent":true,"structureLabel":"编织 · 平纹","notes":"慢慢来",
                                 "palette":["#F5F0DE","#4B503C","#A9A477","#CFB584"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.saved").value(true))
                .andExpect(jsonPath("$.data.patternName").value("经纬初见"));
    }

    @Test
    void readArtifactState() throws Exception {
        when(service.get(7L, 99L)).thenReturn(Map.of("saved", false));

        mockMvc.perform(get("/api/practice/sessions/99/artifact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    @Test
    void deleteArtifact() throws Exception {
        mockMvc.perform(delete("/api/practice/sessions/99/artifact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(service).delete(7L, 99L);
    }

    @Test
    void rejectsTooLongNotes() throws Exception {
        String notes = "很".repeat(501);
        mockMvc.perform(post("/api/practice/sessions/99/artifact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true,\"notes\":\"" + notes + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
