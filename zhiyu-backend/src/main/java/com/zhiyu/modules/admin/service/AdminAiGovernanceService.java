package com.zhiyu.modules.admin.service;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
import com.zhiyu.modules.companion.eval.CompanionEvalCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 治理首版聚合：只统计安全审核事件与能力配置状态。
 *
 * <p>不返回 userId、rawInput、aiOutput、自由文本 reason 或匹配关键词，
 * 不推算调用量、成功率或延迟。
 */
@Service
@RequiredArgsConstructor
public class AdminAiGovernanceService {

    private static final String PRIVACY_NOTE =
            "安全事件与陪伴 trace 都只做脱敏聚合；不返回用户 ID、会话 ID、原始输入输出、思维链、工具参数或匹配关键词。配置存在不等于服务连通，评测集不代表真实用户效果或疗效。";

    private final JdbcTemplate db;
    private final ObjectProvider<VectorStore> vectorStore;
    private final LlmClient llmClient;
    private final AiClient aiClient;
    private final CompanionEvalCatalog evalCatalog;
    private final RuntimeSettings settings;

    @Value("${zhiyu.ai.cost.input-per-million:0}")
    private double inputCostPerMillion;
    @Value("${zhiyu.ai.cost.output-per-million:0}")
    private double outputCostPerMillion;
    @Value("${zhiyu.ai.cost.currency:CNY}")
    private String costCurrency;

    public Map<String, Object> overview(int days) {
        int windowDays = days <= 0 ? 30 : Math.min(days, 90);
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE create_time>=?", Long.class, since);

        Map<String, Long> actions = new LinkedHashMap<>();
        for (String action : List.of("PASS", "REVIEW", "REWRITE", "BLOCK")) actions.put(action, 0L);
        for (Map<String, Object> row : db.queryForList(
                "SELECT action,COUNT(*) AS count FROM ai_audit_log WHERE create_time>=? GROUP BY action",
                since)) {
            actions.put(Objects.toString(row.get("action"), "UNKNOWN"), number(row.get("count")));
        }

        Map<String, Long> scenes = new LinkedHashMap<>();
        for (Map<String, Object> row : db.queryForList(
                "SELECT scene,COUNT(*) AS count FROM ai_audit_log WHERE create_time>=? GROUP BY scene ORDER BY count DESC",
                since)) {
            scenes.put(Objects.toString(row.get("scene"), "unknown"), number(row.get("count")));
        }

        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Map<String, Object> row : db.queryForList(
                "SELECT reason,COUNT(*) AS count FROM ai_audit_log WHERE create_time>=? GROUP BY reason",
                since)) {
            String category = normalizeReason(Objects.toString(row.get("reason"), ""));
            reasons.merge(category, number(row.get("count")), Long::sum);
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (Map<String, Object> row : db.queryForList(
                "SELECT id,scene,action,reason,create_time FROM ai_audit_log WHERE create_time>=? ORDER BY create_time DESC LIMIT 8",
                since)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Objects.toString(row.get("id"), ""));
            item.put("scene", Objects.toString(row.get("scene"), "unknown"));
            item.put("sceneLabel", sceneLabel(Objects.toString(row.get("scene"), "unknown")));
            item.put("action", Objects.toString(row.get("action"), "UNKNOWN"));
            item.put("reasonCategory", normalizeReason(Objects.toString(row.get("reason"), "")));
            item.put("createTime", row.get("create_time"));
            recent.add(item);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        boolean mock = llmClient instanceof MockLlmClient;
        config.put("chatMode", mock ? "MOCK" : "EXTERNAL");
        config.put("chatVerified", false);
        config.put("modelName", Objects.toString(aiClient.modelName(), "unknown"));
        Map<String, Object> modelConfig = settings.values("model");
        if (modelConfig == null) modelConfig = Map.of();
        config.put("temperature", modelConfig.getOrDefault("temperature", 0.7));
        config.put("maxTokens", modelConfig.getOrDefault("maxTokens", 800));
        config.put("modelConfigRevision", revision("model"));
        config.put("promptConfigRevision", revision("prompts"));
        config.put("vectorMode", vectorStore.getIfAvailable() == null ? "UNCONFIGURED" : "LOCAL_FILE");
        config.put("vectorVerified", false);
        config.put("speechConfigured", aiClient.speechAvailable());
        config.put("resilienceStore", aiClient.breakerStoreName());
        config.put("note", "配置存在不等于服务连通；本页只统计脱敏事件、trace 与 token 用量，不推算疗效。模型名称、地址和密钥只由服务端环境变量管理。");

        Map<String, Object> trace = traceSummary(since);
        config.put("promptVersions", trace.get("promptVersions"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowDays", windowDays);
        result.put("generatedAt", LocalDateTime.now());
        result.put("totalEvents", total == null ? 0 : total);
        result.put("actionCounts", items(actions));
        result.put("sceneCounts", scenes.entrySet().stream()
                .map(entry -> item(sceneLabel(entry.getKey()), entry.getValue())).toList());
        result.put("reasonCounts", reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> item(entry.getKey(), entry.getValue())).toList());
        result.put("recentEvents", recent);
        result.put("trace", trace);
        result.put("eval", evalCatalog.summary());
        result.put("config", config);
        result.put("privacyNote", PRIVACY_NOTE);
        return result;
    }

    private int revision(String key) {
        List<Integer> rows = db.queryForList(
                "SELECT revision FROM runtime_config WHERE config_key=? AND deleted=0",
                Integer.class, key);
        return rows == null || rows.isEmpty() || rows.get(0) == null ? 0 : rows.get(0);
    }

    /** 陪伴 trace 脱敏聚合：只返回状态、工具名、延迟与归一化原因，不含用户/会话 ID。 */
    private Map<String, Object> traceSummary(LocalDateTime since) {
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM ai_trace WHERE create_time>=?", Long.class, since);

        Map<String, Long> statuses = new LinkedHashMap<>();
        for (String status : List.of("OK", "FALLBACK", "BLOCKED", "ERROR")) statuses.put(status, 0L);
        for (Map<String, Object> row : db.queryForList(
                "SELECT result_status,COUNT(*) AS count FROM ai_trace WHERE create_time>=? GROUP BY result_status",
                since)) {
            statuses.merge(Objects.toString(row.get("result_status"), "UNKNOWN"),
                    number(row.get("count")), Long::sum);
        }

        Long toolCalls = db.queryForObject(
                "SELECT COUNT(*) FROM ai_trace WHERE create_time>=? AND tool_name IS NOT NULL",
                Long.class, since);
        Long toolOk = db.queryForObject(
                "SELECT COUNT(*) FROM ai_trace WHERE create_time>=? AND tool_name IS NOT NULL AND result_status='OK'",
                Long.class, since);
        Number average = db.queryForObject(
                "SELECT AVG(latency_ms) FROM ai_trace WHERE create_time>=? AND latency_ms IS NOT NULL",
                Number.class, since);
        List<Integer> latencies = db.queryForList(
                "SELECT latency_ms FROM ai_trace WHERE create_time>=? AND latency_ms IS NOT NULL ORDER BY latency_ms LIMIT 5000",
                Integer.class, since);
        int p95 = percentile(latencies, 95);

        List<Map<String, Object>> failures = new ArrayList<>();
        for (Map<String, Object> row : db.queryForList("""
                SELECT scene,tool_name,result_status,fallback_reason,latency_ms,create_time
                FROM ai_trace
                WHERE create_time>=? AND result_status IN ('FALLBACK','BLOCKED','ERROR')
                ORDER BY create_time DESC LIMIT 8
                """, since)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", Objects.toString(row.get("scene"), "unknown"));
            item.put("sceneLabel", sceneLabel(Objects.toString(row.get("scene"), "unknown")));
            item.put("toolName", row.get("tool_name"));
            item.put("resultStatus", Objects.toString(row.get("result_status"), "UNKNOWN"));
            item.put("fallbackReason", normalizeTraceReason(Objects.toString(row.get("fallback_reason"), "")));
            item.put("latencyMs", row.get("latency_ms"));
            item.put("createTime", row.get("create_time"));
            failures.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("statusCounts", items(statuses));
        result.put("toolCalls", toolCalls == null ? 0 : toolCalls);
        result.put("toolSuccessRate", toolCalls != null && toolCalls > 0
                ? Math.round((toolOk == null ? 0 : toolOk) * 1000.0 / toolCalls) / 10.0 : 0.0);
        result.put("avgLatencyMs", average == null ? 0 : Math.round(average.doubleValue()));
        result.put("p95LatencyMs", p95);
        result.put("recentFailures", failures);

        Long inputTokens = db.queryForObject(
                "SELECT COALESCE(SUM(input_tokens),0) FROM ai_trace WHERE create_time>=?",
                Long.class, since);
        Long outputTokens = db.queryForObject(
                "SELECT COALESCE(SUM(output_tokens),0) FROM ai_trace WHERE create_time>=?",
                Long.class, since);
        long inputTotal = inputTokens == null ? 0 : inputTokens;
        long outputTotal = outputTokens == null ? 0 : outputTokens;
        boolean costConfigured = inputCostPerMillion > 0 || outputCostPerMillion > 0;
        double estimatedCost = inputTotal / 1_000_000.0 * inputCostPerMillion
                + outputTotal / 1_000_000.0 * outputCostPerMillion;
        result.put("inputTokens", inputTotal);
        result.put("outputTokens", outputTotal);
        result.put("estimatedCost", Math.round(estimatedCost * 10000.0) / 10000.0);
        result.put("currency", costCurrency == null || costCurrency.isBlank() ? "CNY" : costCurrency);
        result.put("costConfigured", costConfigured);
        result.put("costNote", costConfigured
                ? "按服务端配置的每百万 token 费率估算，实际以供应商账单为准。"
                : "费率未配置，仅显示 token 用量；在服务端设置每百万 token 费率后可估算成本。");
        result.put("modelVersions", versionCounts("model", since));
        result.put("promptVersions", versionCounts("prompt_version", since));
        result.put("note", "只统计 ai_trace 元数据；P95 取最近最多 5000 条延迟样本，不包含用户原文、用户 ID、会话 ID 或工具参数。");
        return result;
    }

    private List<Map<String, Object>> versionCounts(String column, LocalDateTime since) {
        String sql = "SELECT " + column + " AS name, COUNT(*) AS count FROM ai_trace "
                + "WHERE create_time>=? AND " + column + " IS NOT NULL "
                + "GROUP BY " + column + " ORDER BY count DESC";
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : db.queryForList(sql, since)) {
            String name = Objects.toString(row.get("name"), "unknown");
            // 历史 trace 的 model 字段曾记录 MOCK/EXTERNAL 模式，不作为模型版本展示。
            if ("model".equals(column) && ("MOCK".equals(name) || "EXTERNAL".equals(name))) continue;
            result.add(item(name, number(row.get("count"))));
        }
        return result;
    }

    static String normalizeTraceReason(String reason) {
        if (reason == null || reason.isBlank()) return "未标注";
        return switch (reason.trim()) {
            case "INPUT_BLOCK", "OUTPUT_BLOCK", "INVALID_PLAN", "MODEL_FALLBACK",
                 "OUTPUT_REWRITE", "TOOL_FAILED" -> reason.trim();
            default -> "其他运行事件";
        };
    }

    private static int percentile(List<Integer> sorted, int percent) {
        if (sorted == null || sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(sorted.size() * percent / 100.0) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "未标注";
        String text = reason.trim();
        int colon = indexOfColon(text);
        if (colon > 0) text = text.substring(0, colon).trim();
        return switch (text) {
            case "输入检查通过", "输出检查通过", "关键词命中", "输出越界词命中" -> text;
            default -> "其他安全事件";
        };
    }

    private static int indexOfColon(String text) {
        int english = text.indexOf(':');
        int chinese = text.indexOf('：');
        if (english < 0) return chinese;
        if (chinese < 0) return english;
        return Math.min(english, chinese);
    }

    private static List<Map<String, Object>> items(Map<String, Long> counts) {
        return counts.entrySet().stream().map(entry -> item(entry.getKey(), entry.getValue())).toList();
    }

    private static Map<String, Object> item(String name, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private static String sceneLabel(String scene) {
        return switch (scene) {
            case "chat" -> "对话";
            case "recommend" -> "推荐";
            case "work_message", "message" -> "作品寄语";
            case "reflection" -> "创作回顾";
            case "listening" -> "倾听小结";
            case "rag" -> "非遗问答";
            case "lineart" -> "线稿共创";
            case "heritage" -> "非遗变体";
            case "companion" -> "练习陪伴";
            case "comment" -> "评论";
            case "group" -> "小组";
            case "guidance" -> "活动引导";
            case "work" -> "作品";
            default -> scene;
        };
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
