package com.zhiyu.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式输出分句器（SSE A 方案，2026-11-02 拍板，见 docs/project-plan-20261102.md §2.1）。
 *
 * <p>规则：
 * <ul>
 *   <li>句界：{@code 。！？；\n}（逗号/顿号不切，避免碎句）；</li>
 *   <li>首句收紧到 {@value #FIRST_MAX} 字——超过即强制切段，保证首字时间；</li>
 *   <li>后续单句 {@value #MAX} 字强制切段（防止长难句无限缓冲）；</li>
 *   <li>{@link #flush()} 输出流结束时的尾句（可能不含句界符）。</li>
 * </ul>
 */
public class SentenceSplitter {

    /** 首句最大长度（字） */
    static final int FIRST_MAX = 40;
    /** 后续单句最大长度（字） */
    static final int MAX = 80;

    private static final String BOUNDARY = "。！？；\n";

    private final StringBuilder buf = new StringBuilder();
    private int emitted = 0;

    /**
     * 喂入一段流式 token，返回本次切出的完整句（可能多句，可能为空）。
     */
    public List<String> feed(String token) {
        List<String> out = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            return out;
        }
        buf.append(token);
        while (true) {
            int limit = emitted == 0 ? FIRST_MAX : MAX;
            int cut = findBoundary(limit);
            if (cut < 0) {
                break;
            }
            String sentence = buf.substring(0, cut);
            buf.delete(0, cut);
            if (!sentence.isBlank()) {
                out.add(sentence);
                emitted++;
            }
        }
        return out;
    }

    /**
     * 查找下一个切点：句界符优先；超过上限则强制切。
     *
     * @return 切点（含句界符的长度）；-1 表示暂不可切
     */
    private int findBoundary(int limit) {
        int len = buf.length();
        int scan = Math.min(len, limit);
        for (int i = 0; i < scan; i++) {
            if (BOUNDARY.indexOf(buf.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return len >= limit ? limit : -1;
    }

    /**
     * 流结束时取出尾句；无剩余返回 null。
     */
    public String flush() {
        if (buf.length() == 0 || buf.toString().isBlank()) {
            return null;
        }
        String tail = buf.toString();
        buf.setLength(0);
        return tail;
    }
}
