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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvalCase(String id, String type, String question, String expectation, boolean safe) {
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
