package com.zhiyu.modules.companion.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.graph.HeritageGraphIndex;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHeritageToolTest {

    private SearchHeritageTool tool;

    @BeforeEach
    void setUp() throws Exception {
        UserContext.set(new LoginUser(7L, 0));
        var catalogs = new HeritageCatalogService(new ObjectMapper());
        tool = new SearchHeritageTool(new HeritageGraphIndex(catalogs));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void returnsStructuredHitsAndVerifiedSource() {
        var result = tool.execute(new ToolContext(7L, null), Map.of("question", "瑞昌竹编"));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) result.data();
        assertEquals("STRUCTURED_GRAPH", data.get("retrievalMode"));
        @SuppressWarnings("unchecked")
        var hits = (List<GraphHit>) data.get("hits");
        assertTrue(hits.stream().anyMatch(hit -> "ihc-bamboo".equals(hit.nodeId())));
        assertTrue(hits.stream().anyMatch(hit -> hit.url() != null && hit.url().startsWith("https://")));
    }

    @Test
    void blankQuestionIsRejected() {
        var result = tool.execute(new ToolContext(7L, null), Map.of());
        assertFalse(result.success());
    }
}
