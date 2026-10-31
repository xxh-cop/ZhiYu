package com.zhiyu.modules.companion;

import com.zhiyu.modules.companion.controller.CompanionMemoryController;
import com.zhiyu.modules.companion.memory.CompanionMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanionMemoryControllerTest {

    private CompanionMemoryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CompanionMemoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CompanionMemoryController(service)).build();
    }

    @Test
    void getMemoryState() throws Exception {
        when(service.get()).thenReturn(Map.of("enabled", false, "items", List.of(), "ttlDays", 180));

        mockMvc.perform(get("/api/companion/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.ttlDays").value(180));
    }

    @Test
    void enableMemory() throws Exception {
        when(service.enable(true, true)).thenReturn(Map.of("enabled", true, "items", List.of()));

        mockMvc.perform(put("/api/companion/memory/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void savePreferences() throws Exception {
        when(service.save(any(), any())).thenReturn(Map.of("enabled", true, "items", List.of()));

        mockMvc.perform(put("/api/companion/memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true,\"preferences\":{\"preferred_minutes\":\"15\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deleteAllMemory() throws Exception {
        when(service.deleteAll()).thenReturn(Map.of("enabled", false, "items", List.of()));

        mockMvc.perform(delete("/api/companion/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(service).deleteAll();
    }
}
