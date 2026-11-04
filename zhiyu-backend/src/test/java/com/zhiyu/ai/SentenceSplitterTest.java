package com.zhiyu.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SentenceSplitter} 单测（SSE A 方案）。
 */
class SentenceSplitterTest {

    @Test
    @DisplayName("中文句界切分：。！？；与换行")
    void testBoundarySplit() {
        SentenceSplitter s = new SentenceSplitter();
        List<String> out = s.feed("我在这里陪着你。慢慢来！");
        assertEquals(2, out.size());
        assertEquals("我在这里陪着你。", out.get(0));
        assertEquals("慢慢来！", out.get(1));

        out = s.feed("好吗？\n第三句；");
        // 换行本身是句界；纯换行的空段被丢弃，不作为句子下发
        assertEquals(2, out.size());
        assertEquals("好吗？", out.get(0));
        assertEquals("第三句；", out.get(1));

        out = s.feed("逗号，不切");
        assertTrue(out.isEmpty());
        assertEquals("逗号，不切", s.flush());
    }

    @Test
    @DisplayName("首句 40 字强制切段（保首字时间）")
    void testFirstSentenceHardCut() {
        SentenceSplitter s = new SentenceSplitter();
        String longToken = "我".repeat(50);
        List<String> out = s.feed(longToken);
        assertEquals(1, out.size());
        assertEquals(40, out.get(0).length());
        // 剩余 10 字留在缓冲，继续喂句界符后按新句发出
        out = s.feed("。");
        assertEquals(1, out.size());
        assertEquals("我".repeat(10) + "。", out.get(0));
    }

    @Test
    @DisplayName("后续单句 80 字强制切段")
    void testMaxSentenceHardCut() {
        SentenceSplitter s = new SentenceSplitter();
        s.feed("首句。");
        List<String> out = s.feed("长".repeat(100));
        assertEquals(1, out.size());
        assertEquals(80, out.get(0).length());
        assertEquals("长".repeat(20), s.flush());
    }

    @Test
    @DisplayName("flush：无剩余返回 null，空白不发出")
    void testFlush() {
        SentenceSplitter s = new SentenceSplitter();
        assertNull(s.flush());
        List<String> out = s.feed("   ");
        assertTrue(out.isEmpty());
        assertNull(s.flush());
    }

    @Test
    @DisplayName("跨 token 的句界：切分点落在后一个 token 内")
    void testBoundaryAcrossTokens() {
        SentenceSplitter s = new SentenceSplitter();
        assertTrue(s.feed("我在这里").isEmpty());
        List<String> out = s.feed("陪着你。下一句");
        assertEquals(1, out.size());
        assertEquals("我在这里陪着你。", out.get(0));
        assertEquals("下一句", s.flush());
    }
}
