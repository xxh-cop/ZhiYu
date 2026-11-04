package com.zhiyu.modules.companion.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 离线评测集：黄金问题、安全红队与端到端任务。
 * 只读、不含用户数据；真实模型未配置时只能验证降级路径。
 */
@Service
public class CompanionEvalCatalog {

    public static final int MIN_GOLDEN = 20;
    public static final int MIN_SAFETY = 10;
    public static final int MIN_E2E = 8;

    /**
     * 评测用例。2026-11-03 扩展机器可断言字段（全部可选，缺省为 null）：
     * <ul>
     *   <li>{@code layer} —— 分层：L1 红线安全 / L2 检索质量 / L3 对话质量 / L4 降级路径 / L5 注入防御 /
     *       L6 摘要忠实性（2026-11-04 Z1）/ P2 调度层（SKIPPED 登记）/ E2E</li>
     *   <li>{@code mustContain} / {@code mustContainAny} / {@code mustNotContain} —— 文本断言（任一命中 / 全部命中 / 全部不命中）</li>
     *   <li>{@code expectSources} —— RAG 路径必须命中的来源条目 id</li>
     *   <li>{@code mustBlock} —— true 时输入侧必须被安全过滤拦截（不调用 LLM）</li>
     *   <li>{@code maxLength} —— 回复长度上限</li>
     *   <li>{@code history} —— 多轮上下文（user/assistant 交替）</li>
     *   <li>{@code userText} —— 生成类用例的素材文本（如倾听小结的发言 transcript）</li>
     *   <li>{@code check} —— 内置探针名（empathy_first / refusal / L4 降级探针）</li>
     *   <li>{@code status} —— {@code SKIPPED(<原因>)} 时登记但不执行、不计入通过率（2026-11-04 Z2，P2 调度层用例）</li>
     *   <li>{@code previousSummary} / {@code startTurn} / {@code endTurn} / {@code turns} / {@code outOfWindow}
     *       —— L6 摘要忠实性输入：旧摘要、覆盖区间、待压缩轮次、窗口外不得出现的实体（2026-11-04 Z1）</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvalCase(String id, String type, String question, String expectation, boolean safe,
                           String layer,
                           List<String> mustContain, List<String> mustContainAny,
                           List<String> mustNotContain, List<String> expectSources,
                           Boolean mustBlock, Integer maxLength,
                           String userText, List<String> history, String check,
                           String status,
                           String previousSummary, Integer startTurn, Integer endTurn,
                           List<Map<String, String>> turns, List<String> outOfWindow) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvalDataset(String version, String notes, List<EvalCase> cases,
                              List<EvalCase> safetyRedTeam, List<EvalCase> e2eTasks) {
    }

    private final EvalDataset dataset;

    public CompanionEvalCatalog(ObjectMapper json,
                                @Value("classpath:eval/companion-eval.json") Resource resource) {
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            this.dataset = json.readValue(reader, EvalDataset.class);
        } catch (IOException e) {
            throw new IllegalStateException("评测集读取失败", e);
        }
        validate();
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", dataset.version());
        result.put("notes", dataset.notes());
        result.put("total", size(dataset.cases()));
        result.put("safetyTotal", size(dataset.safetyRedTeam()));
        result.put("e2eTotal", size(dataset.e2eTasks()));
        result.put("byType", counts(dataset.cases()));
        result.put("safetyByType", counts(dataset.safetyRedTeam()));
        result.put("e2eByType", counts(dataset.e2eTasks()));
        result.put("cases", dataset.cases());
        result.put("safetyRedTeam", dataset.safetyRedTeam());
        result.put("e2eTasks", dataset.e2eTasks());
        return result;
    }

    private void validate() {
        if (size(dataset.cases()) < MIN_GOLDEN) {
            throw new IllegalStateException("评测集至少需要 " + MIN_GOLDEN + " 条黄金问题");
        }
        if (size(dataset.safetyRedTeam()) < MIN_SAFETY) {
            throw new IllegalStateException("评测集至少需要 " + MIN_SAFETY + " 条安全红队");
        }
        if (size(dataset.e2eTasks()) < MIN_E2E) {
            throw new IllegalStateException("评测集至少需要 " + MIN_E2E + " 条端到端任务");
        }
        Set<String> ids = new HashSet<>();
        for (List<EvalCase> group : List.of(dataset.cases(), dataset.safetyRedTeam(), dataset.e2eTasks())) {
            for (EvalCase item : group) {
                if (item.id() == null || item.id().isBlank() || item.question() == null || item.question().isBlank()) {
                    throw new IllegalStateException("评测用例缺少 id 或 question");
                }
                if (!ids.add(item.id())) {
                    throw new IllegalStateException("评测用例 id 重复: " + item.id());
                }
            }
        }
    }

    private static List<Map<String, Object>> counts(List<EvalCase> cases) {
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (EvalCase item : cases) {
            grouped.merge(item.type() == null ? "UNKNOWN" : item.type(), 1L, Long::sum);
        }
        return grouped.entrySet().stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            return item;
        }).toList();
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
