package com.zhiyu.ai.safety;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 高危词库（T1.3b）。
 *
 * <p>从 {@code classpath:lexicon/high-risk.txt} 加载关键词集合，
 * 供 {@link KeywordContentSafetyFilter} 做输入侧子串匹配。
 *
 * <p>加载策略：
 * <ul>
 *   <li>UTF-8 显式读取（AGENTS.md §10：不依赖 {@code file.encoding}）</li>
 *   <li>{@code #} 开头为注释，空行忽略</li>
 *   <li>匹配时去空白、转小写（大小写不敏感）</li>
 *   <li>加载失败 → fail-fast，启动报错（不静默失败）</li>
 * </ul>
 */
@Slf4j
@Component
public class HighRiskLexicon {

    @Value("classpath:lexicon/high-risk.txt")
    private Resource lexiconResource;

    private Set<String> keywords = Collections.emptySet();

    @PostConstruct
    public void init() {
        Set<String> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(lexiconResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                // 归一化：去全部空白 + 小写（中文无大小写问题，但保留以兼容英文 / 混排）
                loaded.add(normalize(trimmed));
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 lexicon/high-risk.txt 失败", e);
        }
        this.keywords = Collections.unmodifiableSet(loaded);
        log.info("high-risk.txt 加载完成：{} 条关键词", keywords.size());
    }

    /**
     * 文本中是否包含任一高危关键词（子串匹配，大小写不敏感、忽略空白）。
     */
    public boolean containsRisk(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = normalize(text);
        for (String kw : keywords) {
            if (normalized.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 命中的第一个关键词（用于脱敏 summary），未命中返回 null。
     */
    public String firstMatch(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = normalize(text);
        for (String kw : keywords) {
            if (normalized.contains(kw)) {
                return kw;
            }
        }
        return null;
    }

    public int size() {
        return keywords.size();
    }

    /**
     * 归一化：去除全部空白字符并转小写。
     */
    private String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
