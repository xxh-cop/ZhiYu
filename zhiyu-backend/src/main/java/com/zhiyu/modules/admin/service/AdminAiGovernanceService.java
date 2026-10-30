package com.zhiyu.modules.admin.service;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
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
            "只统计安全审核事件，不据此推算调用量、成功率或延迟；不返回用户 ID、原始输入输出或匹配关键词。配置存在不等于服务连通。";

    private final JdbcTemplate db;
    private final ObjectProvider<VectorStore> vectorStore;
    private final LlmClient llmClient;
    private final AiClient aiClient;

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
        config.put("vectorMode", vectorStore.getIfAvailable() == null ? "UNCONFIGURED" : "LOCAL_FILE");
        config.put("vectorVerified", false);
        config.put("speechConfigured", aiClient.speechAvailable());
        config.put("resilienceStore", aiClient.breakerStoreName());
        config.put("note", "配置存在不等于服务连通；本页只统计安全审核事件，不推算调用量、成功率或延迟。");

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
        result.put("config", config);
        result.put("privacyNote", PRIVACY_NOTE);
        return result;
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
