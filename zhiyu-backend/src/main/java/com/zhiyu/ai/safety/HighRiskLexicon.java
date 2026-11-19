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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高危词库（T1.3b，2026-11-19 补齐终局告别 / 求助形态）。
 *
 * <p>从 {@code classpath:lexicon/high-risk.txt} 加载关键词集合，
 * 供 {@link KeywordContentSafetyFilter} 做输入侧子串匹配；
 * 同时从 {@code classpath:lexicon/high-risk-benign.txt} 加载良性复合词，
 * 在匹配前做中性化（掩码），避免「求救」等弱信号词在正常语境误触发。
 *
 * <p>加载策略：
 * <ul>
 *   <li>UTF-8 显式读取（AGENTS.md §10：不依赖 {@code file.encoding}）</li>
 *   <li>{@code #} 开头为注释，空行忽略；两份资源缺失/不可读 → fail-fast</li>
 *   <li>匹配时去空白、转小写（大小写不敏感）</li>
 * </ul>
 *
 * <p>匹配口径：
 * <ul>
 *   <li>先对归一化文本做良性复合词掩码（替换为占位符，不做删除，
 *       避免前后文拼接出新的命中）；其中「求救信号」为<b>语境门控</b>：
 *       仅当同句出现演练/科普/影视/设备等良性语境词时才掩码，
 *       真实求助（「这是我的求救信号」）照常命中</li>
 *   <li>同句命中多个关键词时取<b>最长者</b>（更具体的表达优先），
 *       同长度取更高风险档——保证定级不受集合遍历顺序影响</li>
 * </ul>
 */
@Slf4j
@Component
public class HighRiskLexicon {

    @Value("classpath:lexicon/high-risk.txt")
    private Resource lexiconResource;

    @Value("classpath:lexicon/high-risk-benign.txt")
    private Resource benignResource;

    /** 良性复合词替换后的占位符：分隔前后文，避免拼接出新的误命中。 */
    private static final String MASK = "\u0000";

    private static final Pattern LEVEL_MARKER = Pattern.compile("@level:(\\d)");

    /** 良性复合词表内的语境门控段标记。 */
    private static final String CONTEXT_MARKER = "# @context";

    private Set<String> keywords = Collections.emptySet();

    /** AQ2（2026-11-04）：关键词 → 危机等级（来自词表 {@code # @level:N} 组标记）。 */
    private Map<String, Integer> levels = Collections.emptyMap();

    /** 良性复合词（无条件中性化）；加载失败即启动失败，不静默降级。 */
    private Set<String> benignPhrases = Collections.emptySet();

    /**
     * 语境门控中性化：弱信号 → 良性语境词集合。
     * 仅当文本同句出现任一语境词时才中性化，避免把真实求助（「这是我的求救信号」）一起掩掉。
     */
    private Map<String, Set<String>> contextGatedMasks = Collections.emptyMap();

    @PostConstruct
    public void init() {
        Set<String> loaded = new HashSet<>();
        Map<String, Integer> lv = new HashMap<>();
        int currentLevel = 2; // 标记前的默认档（高危，与历史 care_reminder 口径一致）
        try (BufferedReader reader = open(lexiconResource)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                // AQ2：组级标记行 `# @level:N`（写在分组注释上方，语义沿用词表既有分组，不自造）
                if (trimmed.startsWith("#")) {
                    Matcher m = LEVEL_MARKER.matcher(trimmed);
                    if (m.find()) {
                        currentLevel = Integer.parseInt(m.group(1));
                    }
                    continue;
                }
                String kw = normalize(trimmed);
                loaded.add(kw);
                lv.put(kw, currentLevel);
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 lexicon/high-risk.txt 失败", e);
        }

        Set<String> benign = new HashSet<>();
        Map<String, Set<String>> gated = new HashMap<>();
        boolean contextMode = false;
        try (BufferedReader reader = open(benignResource)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    if (trimmed.startsWith(CONTEXT_MARKER)) {
                        contextMode = true;
                    }
                    continue;
                }
                if (contextMode) {
                    // 语境门控格式：弱信号|良性语境词1,良性语境词2,...
                    String[] parts = trimmed.split("\\|", 2);
                    if (parts.length != 2) {
                        throw new IllegalStateException("良性复合词语境门控格式应为「弱信号|语境词,...」: " + trimmed);
                    }
                    String phrase = normalize(parts[0].trim());
                    Set<String> contexts = new HashSet<>();
                    for (String ctx : parts[1].split(",")) {
                        if (!ctx.isBlank()) {
                            contexts.add(normalize(ctx.trim()));
                        }
                    }
                    if (phrase.isEmpty() || contexts.isEmpty()) {
                        throw new IllegalStateException("良性复合词语境门控缺少弱信号或语境词: " + trimmed);
                    }
                    if (!containsOnlyWeakKeywords(phrase, loaded, lv)) {
                        throw new IllegalStateException(
                                "语境门控项必须包含 1 档弱信号且不得含 2/3 档关键词: " + phrase);
                    }
                    gated.put(phrase, Collections.unmodifiableSet(contexts));
                } else {
                    String phrase = normalize(trimmed);
                    if (!containsOnlyWeakKeywords(phrase, loaded, lv)) {
                        throw new IllegalStateException(
                                "良性复合词必须包含 1 档弱信号且不得含 2/3 档关键词: " + phrase);
                    }
                    benign.add(phrase);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 lexicon/high-risk-benign.txt 失败", e);
        }

        this.keywords = Collections.unmodifiableSet(loaded);
        this.levels = Collections.unmodifiableMap(lv);
        this.benignPhrases = Collections.unmodifiableSet(benign);
        this.contextGatedMasks = Collections.unmodifiableMap(gated);
        log.info("high-risk.txt 加载完成：{} 条关键词，{} 条良性复合词，{} 条语境门控",
                keywords.size(), benignPhrases.size(), contextGatedMasks.size());
    }

    /**
     * 关键词的危机等级（AQ2）：1 关注 / 2 高危 / 3 紧急（语义沿词表分组注释）。
     * 词表外/未知返回 {@code null}——调用方按「无法确定时保守取高档」处理。
     */
    public Integer levelOf(String keyword) {
        return keyword == null ? null : levels.get(keyword);
    }

    /**
     * 文本中是否包含任一高危关键词（子串匹配，大小写不敏感、忽略空白；
     * 良性复合词先行中性化）。
     */
    public boolean containsRisk(String text) {
        return firstMatch(text) != null;
    }

    /**
     * 命中的关键词（用于脱敏 summary），未命中返回 null。
     *
     * <p>多词命中时取最长者（更具体的表达优先），同长度取更高风险档，
     * 避免 HashSet 遍历顺序导致定级漂移。
     */
    public String firstMatch(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return null;
        }
        String masked = maskBenign(normalized);
        String best = null;
        for (String kw : keywords) {
            if (!masked.contains(kw)) {
                continue;
            }
            if (best == null
                    || kw.length() > best.length()
                    || (kw.length() == best.length() && levelValue(kw) > levelValue(best))) {
                best = kw;
            }
        }
        return best;
    }

    public int size() {
        return keywords.size();
    }

    /** 词表关键词（包内可见，供安全不变量测试核对掩码表）。 */
    Set<String> keywords() {
        return keywords;
    }

    /** 良性复合词（包内可见，供安全不变量测试核对掩码表）。 */
    Set<String> benignPhrases() {
        return benignPhrases;
    }

    /** 语境门控中性化项（包内可见，供安全不变量测试核对掩码表）。 */
    Map<String, Set<String>> contextGatedMasks() {
        return contextGatedMasks;
    }

    /**
     * 良性复合词中性化：把「正常语义但含弱信号词」的固定复合词替换为占位符。
     * 只做字面替换（非删除），确保不会把前后文拼成新的关键词。
     *
     * <p>无条件项先掩码；语境门控项仅在原文出现任一良性语境词时掩码，
     * 从而保留「这是我的求救信号」这类真实求助的命中。
     */
    private String maskBenign(String normalized) {
        String masked = normalized;
        for (String phrase : benignPhrases) {
            if (masked.contains(phrase)) {
                masked = masked.replace(phrase, MASK);
            }
        }
        for (Map.Entry<String, Set<String>> entry : contextGatedMasks.entrySet()) {
            if (!masked.contains(entry.getKey())) {
                continue;
            }
            for (String ctx : entry.getValue()) {
                if (normalized.contains(ctx)) {
                    masked = masked.replace(entry.getKey(), MASK);
                    break;
                }
            }
        }
        return masked;
    }

    private int levelValue(String keyword) {
        Integer lv = levels.get(keyword);
        return lv == null ? 3 : lv; // 未知保守取最高档
    }

    /**
     * 中性化项安全校验（fail-fast）：必须至少包含一个关键词，且所含关键词全部为 1 档。
     * 防止词表维护时误把 2/3 档信号（如「遗书」）写进中性化/门控表。
     */
    private boolean containsOnlyWeakKeywords(String phrase, Set<String> keywords, Map<String, Integer> levels) {
        boolean containsKeyword = false;
        for (String kw : keywords) {
            if (!phrase.contains(kw)) {
                continue;
            }
            containsKeyword = true;
            Integer lv = levels.get(kw);
            if (lv == null || lv != 1) {
                return false;
            }
        }
        return containsKeyword;
    }

    private BufferedReader open(Resource resource) throws java.io.IOException {
        // AGENTS.md §10：显式 UTF-8，不依赖 file.encoding
        return new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
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
