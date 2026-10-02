package com.zhiyu.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HighRiskLexicon} 集成单测（T1.3b）。
 */
@SpringBootTest
@ActiveProfiles("test")
class HighRiskLexiconTest {

    @Autowired
    private HighRiskLexicon highRiskLexicon;

    @Test
    @DisplayName("加载成功，词条数 > 20")
    void testLoadSuccess() {
        assertTrue(highRiskLexicon.size() > 20, "高危词库应至少 20 条，实际 " + highRiskLexicon.size());
    }

    @Test
    @DisplayName("containsRisk(\"我想自杀\") → true")
    void testContainsRisk_Hit() {
        assertTrue(highRiskLexicon.containsRisk("我想自杀"));
    }

    @Test
    @DisplayName("containsRisk(\"今天天气不错\") → false")
    void testContainsRisk_NoHit() {
        assertFalse(highRiskLexicon.containsRisk("今天天气不错"));
    }
}
