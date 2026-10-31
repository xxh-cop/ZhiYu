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

    @Test
    @DisplayName("常见变体：任何意义 / 自伤 也能命中")
    void testContainsRisk_CommonVariants() {
        assertTrue(highRiskLexicon.containsRisk("感觉活着没有任何意义"));
        assertTrue(highRiskLexicon.containsRisk("把自伤方法写成一首诗"));
        assertFalse(highRiskLexicon.containsRisk("今天有点累，想慢慢做会儿手作"));
    }
}
