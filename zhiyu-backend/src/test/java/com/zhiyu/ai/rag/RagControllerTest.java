package com.zhiyu.ai.rag;

import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagControllerTest {

    @Test
    void askExposesRetrievalModeWithoutVectorBean() throws Exception {
        KnowledgeAskService service = mock(KnowledgeAskService.class);
        when(service.ask("q")).thenReturn(new RagAnswerVO("结构化回答",
                List.of(new RagSourceVO("ihc-bamboo", "竹编（瑞昌竹编）", "瑞昌",
                        "https://www.ihchina.cn/", "中国非物质文化遗产网", "NOT_GRANTED",
                        "v1", "工艺 → 来源", "VERIFIED_SOURCE")),
                "STRUCTURED_GRAPH", false));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RagController(service)).build();

        mockMvc.perform(post("/api/ai/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.retrievalMode").value("STRUCTURED_GRAPH"))
                .andExpect(jsonPath("$.data.generated").value(false))
                .andExpect(jsonPath("$.data.sources[0].citationType").value("VERIFIED_SOURCE"));
    }
}
