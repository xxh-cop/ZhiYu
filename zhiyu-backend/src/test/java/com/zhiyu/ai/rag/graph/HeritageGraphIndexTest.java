package com.zhiyu.ai.rag.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeritageGraphIndexTest {

    private static HeritageCatalog catalog;

    @BeforeAll
    static void load() throws Exception {
        catalog = new HeritageCatalogService(new ObjectMapper()).baseCatalog();
    }

    @Test
    void findsTraditionPatternAndVerifiedSourceForWeaveQuestion() {
        var hits = HeritageGraphIndex.search("瑞昌竹编的纹样和来源", catalog);
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("970102")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeType().equals("PATTERN")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-bamboo")), hits.toString());
        assertFalse(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-paper")));
        assertTrue(hits.stream().filter(hit -> hit.nodeType().equals("SOURCE"))
                .allMatch(hit -> hit.url().startsWith("https://")));
        assertTrue(hits.stream().filter(hit -> hit.nodeType().equals("SOURCE"))
                .allMatch(hit -> hit.relationPath().contains("来源：")));
    }

    @Test
    void expandsMaterialQuestionInsideTheMatchedTradition() {
        var hits = HeritageGraphIndex.search("夏布绣的材料", catalog);
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("970101")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-xiabu")));
        assertFalse(hits.stream().anyMatch(hit -> hit.nodeId().equals("ihc-bamboo")));
    }

    @Test
    void expandsMaterialAndSafetyNodes() {
        var material = HeritageGraphIndex.search("竹编 材料", catalog);
        assertTrue(material.stream().anyMatch(hit -> hit.nodeType().equals("MATERIAL")
                && hit.title().contains("竹")), material.toString());
        var safety = HeritageGraphIndex.search("剪纸 安全", catalog);
        assertTrue(safety.stream().anyMatch(hit -> hit.nodeType().equals("SAFETY")
                && hit.title().contains("安全")), safety.toString());
        assertTrue(safety.stream().noneMatch(hit -> hit.excerpt().contains("疗效")),
                "安全节点不得输出疗效结论");
    }

    @Test
    void findsWindowPatternUnderPapercutTradition() {
        var hits = HeritageGraphIndex.search("剪纸 方窗", catalog);
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeId().equals("970103")));
        assertTrue(hits.stream().anyMatch(hit -> hit.nodeType().equals("PATTERN")
                && hit.title().contains("窗")));
    }

    @Test
    void unrelatedDiagnosticQuestionReturnsEmpty() {
        assertTrue(HeritageGraphIndex.search("抑郁症是什么", catalog).isEmpty());
    }

    @Test
    void resultsAreBoundedAndDeduplicated() {
        var hits = HeritageGraphIndex.search("瑞昌竹编 竹编 纹样 来源 夏布绣", catalog);
        assertTrue(hits.size() <= HeritageGraphIndex.MAX_HITS);
        assertEquals(hits.size(), hits.stream().map(GraphHit::nodeId).distinct().count());
    }
}
