package com.zhiyu.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HighRiskLexicon} 集成单测（T1.3b，2026-11-19 补齐告别 / 求助形态）。
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

    // ── 2026-11-19：终局告别 / 求助形态补齐 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "告别这个世界",
            "和这个世界告别",
            "跟这个世界告别",
            "向这个世界告别",
            "永别这个世界",
            "永别了这个世界",
            "永别了，这个世界",
            "永别了,这个世界",
            "和这个世界永别",
            "跟这个世界永别",
            "向这个世界永别",
            "与这个世界永别",
            "来世再见",
            "下辈子再见",
            "我想永别了，这个世界"
    })
    @DisplayName("终局告别短语（带终局宾语）→ 命中且为 2 档（高危）")
    void farewellExpressionsHit(String text) {
        assertTrue(highRiskLexicon.containsRisk(text), "应命中终局告别表达: " + text);
        String matched = highRiskLexicon.firstMatch(text);
        assertNotNull(matched);
        assertEquals(2, highRiskLexicon.levelOf(matched), "终局告别短语应为 2 档: " + matched);
    }

    @ParameterizedTest
    @ValueSource(strings = {"永别", "永别了", "和焦虑永别", "跟失眠永别了", "和过去永别"})
    @DisplayName("「永别」单独出现 → 仅 1 档关注，绝不进 2 档（康复叙事不得升级）")
    void bareFarewellIsOnlyLevel1(String text) {
        assertTrue(highRiskLexicon.containsRisk(text), "单独出现的永别仍应进入关注档: " + text);
        String matched = highRiskLexicon.firstMatch(text);
        assertNotNull(matched);
        assertEquals(1, highRiskLexicon.levelOf(matched), "单独「永别」只能是 1 档: " + text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"遗言", "交代后事", "他留下了遗言"})
    @DisplayName("终局交代表达 → 命中且为 3 档（紧急）")
    void finalArrangementExpressionsHit(String text) {
        assertTrue(highRiskLexicon.containsRisk(text), "应命中终局交代表达: " + text);
        String matched = highRiskLexicon.firstMatch(text);
        assertNotNull(matched);
        assertEquals(3, highRiskLexicon.levelOf(matched), "终局交代应为 3 档: " + matched);
    }

    @ParameterizedTest
    @ValueSource(strings = {"求救", "救我", "救救我", "谁来救救我", "快来人救我", "我向你求救"})
    @DisplayName("求助表达 → 命中且为 1 档（关注）")
    void cryForHelpExpressionsHit(String text) {
        assertTrue(highRiskLexicon.containsRisk(text), "应命中求助表达: " + text);
        String matched = highRiskLexicon.firstMatch(text);
        assertNotNull(matched);
        assertEquals(1, highRiskLexicon.levelOf(matched), "求助表达应为 1 档: " + matched);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "这是我的求救信号",
            "我在发求救信号，有人能看到吗",
            "求你看到我的求救信号"
    })
    @DisplayName("真实求助的「求救信号」必须命中（不得整词掩码）")
    void realCryForHelpSignalHits(String text) {
        assertTrue(highRiskLexicon.containsRisk(text), "真实求助不应被掩码漏放: " + text);
        String matched = highRiskLexicon.firstMatch(text);
        assertNotNull(matched);
        assertTrue(matched.contains("求救"), "应命中求助类关键词: " + matched);
        assertEquals(1, highRiskLexicon.levelOf(matched), "求救信号应走 1 档关注: " + matched);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "消防演练中，大家练习如何发出求救信号",
            "科普讲座介绍了求救信号的几种发送方式",
            "电影里的主角靠求救信号等到了救援",
            "他按下了求救信号器",
            "警报器发出的声音就像求救信号"
    })
    @DisplayName("演练 / 科普 / 影视 / 设备语境的「求救信号」不命中")
    void benignSignalContextsDoNotHit(String text) {
        assertFalse(highRiskLexicon.containsRisk(text), "正常语境不应命中: " + text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "周五的告别演出很感人",
            "毕业典礼上我们唱了告别歌曲",
            "我想和过去告别，重新开始",
            "这本书教人怎么告别过去",
            "这本书讲野外求生时求救电话的使用方法",
            "他把我救我于水火的事记了一辈子"
    })
    @DisplayName("良性语境不命中（不被当成高危）")
    void benignContextsDoNotHit(String text) {
        assertFalse(highRiskLexicon.containsRisk(text), "正常语义不应命中: " + text);
    }

    @Test
    @DisplayName("掩码不吞掉同句里的其它高风险信号")
    void maskDoesNotSwallowOtherSignals() {
        assertTrue(highRiskLexicon.containsRisk("求救信号发不出去，我想自杀"));
        assertTrue(highRiskLexicon.containsRisk("求救电话打不通，我不想活了"));
        assertTrue(highRiskLexicon.containsRisk("演练时我其实想自杀，这是我的求救信号"));
    }

    @Test
    @DisplayName("掩码安全不变量：无条件 / 语境门控中性化都只能屏蔽 1 档，不得屏蔽 2/3 档")
    void benignPhrasesNeverMaskStrongerSignals() {
        assertFalse(highRiskLexicon.benignPhrases().isEmpty(), "良性复合词表不应为空（加载失败应 fail-fast）");
        for (String phrase : highRiskLexicon.benignPhrases()) {
            assertMaskable(phrase);
        }
        assertFalse(highRiskLexicon.contextGatedMasks().isEmpty(), "语境门控段不应为空");
        for (var entry : highRiskLexicon.contextGatedMasks().entrySet()) {
            assertFalse(entry.getValue().isEmpty(), "语境门控必须带良性语境词: " + entry.getKey());
            assertMaskable(entry.getKey());
        }
    }

    /** 中性化项必须含至少一个关键词，且所含关键词全部是 1 档关注。 */
    private void assertMaskable(String phrase) {
        boolean containsKeyword = false;
        for (String kw : highRiskLexicon.keywords()) {
            if (!phrase.contains(kw)) {
                continue;
            }
            containsKeyword = true;
            Integer lv = highRiskLexicon.levelOf(kw);
            assertNotNull(lv, "词表关键词必须定级: " + kw);
            assertEquals(1, lv.intValue(),
                    "中性化项不得屏蔽 2/3 档信号: 「" + phrase + "」含「" + kw + "」");
        }
        assertTrue(containsKeyword, "中性化项必须至少包含一个弱信号词: " + phrase);
    }

    @Test
    @DisplayName("多词命中取最长词（更具体表达优先），不受集合遍历顺序影响")
    void longestMatchWins() {
        assertEquals("救救我", highRiskLexicon.firstMatch("求求你了，救救我"), "「救救我」应优先于「救我」");
        assertEquals("告别这个世界", highRiskLexicon.firstMatch("我想告别这个世界"),
                "「告别这个世界」应作为命中词返回");
        assertEquals(3, highRiskLexicon.levelOf(highRiskLexicon.firstMatch("遗言里写了后事")),
                "遗言应定 3 档");
    }

    @Test
    @DisplayName("归一化：忽略空白后仍能命中终局告别")
    void whitespaceInsensitive() {
        assertTrue(highRiskLexicon.containsRisk("永 别 了"));
        assertTrue(highRiskLexicon.containsRisk("救 救 我"));
    }
}
