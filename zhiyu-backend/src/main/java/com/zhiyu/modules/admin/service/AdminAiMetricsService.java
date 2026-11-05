package com.zhiyu.modules.admin.service;

import com.zhiyu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * AI 调用用量与质量指标聚合（BO 批，2026-11-05）。
 *
 * <p>数据源 {@code ai_trace}（只追加、脱敏元数据）：按时间区间 + 资源 key（scene）
 * 聚合并给出调用量、成功率、降级率、P50/P95 延迟与 token 用量。
 *
 * <p>只返回聚合数字，不返回用户 ID、会话 ID、prompt、对话原文或自由文本原因。
 * tokens 为 {@code null} 的历史行不参与求和（如实，不估算）。
 */
@Service
@RequiredArgsConstructor
public class AdminAiMetricsService {

    /** 延迟百分位采样上限（全区间、全场景合计）；超出时取最近样本，返回体注明。 */
    private static final int LATENCY_SAMPLE_LIMIT = 20000;
    /** 时间区间上限（天），防止全表扫描。 */
    private static final int MAX_RANGE_DAYS = 90;

    private final JdbcTemplate db;

    /**
     * @param from 区间起（含），null 时取 {@code to - 7 天}
     * @param to   区间止（含），null 时取当前时间
     */
    public Map<String, Object> metrics(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        if (!start.isBefore(end)) {
            throw new BusinessException(1001, "from 必须早于 to");
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new BusinessException(1001, "时间区间最长 " + MAX_RANGE_DAYS + " 天");
        }

        // ── 按场景 + 状态聚合计数与 token 用量 ──
        Map<String, SceneAgg> aggs = new TreeMap<>();
        for (Map<String, Object> row : db.queryForList("""
                SELECT scene, result_status, COUNT(*) AS c, COUNT(latency_ms) AS lc,
                       COALESCE(SUM(input_tokens),0) AS it, COALESCE(SUM(output_tokens),0) AS ot,
                       AVG(latency_ms) AS avg_l
                FROM ai_trace WHERE create_time>=? AND create_time<=?
                GROUP BY scene, result_status
                """, start, end)) {
            String scene = Objects.toString(row.get("scene"), "unknown");
            SceneAgg agg = aggs.computeIfAbsent(scene, k -> new SceneAgg());
            long count = number(row.get("c"));
            agg.calls += count;
            agg.inputTokens += number(row.get("it"));
            agg.outputTokens += number(row.get("ot"));
            if (row.get("avg_l") != null) {
                // AVG 只覆盖非 null 延迟行，权重必须用 COUNT(latency_ms) 而非 COUNT(*)
                long latencyCount = number(row.get("lc"));
                agg.weightedLatency += ((Number) row.get("avg_l")).doubleValue() * latencyCount;
                agg.latencyRows += latencyCount;
            }
            switch (Objects.toString(row.get("result_status"), "")) {
                case "OK" -> agg.ok += count;
                case "FALLBACK" -> agg.fallback += count;
                case "BLOCKED" -> agg.blocked += count;
                case "ERROR" -> agg.error += count;
                default -> agg.other += count;
            }
        }

        // ── 各场景模型标识（DISTINCT，不含 null） ──
        Map<String, List<String>> models = new LinkedHashMap<>();
        for (Map<String, Object> row : db.queryForList(
                "SELECT DISTINCT scene, model FROM ai_trace WHERE create_time>=? AND create_time<=? AND model IS NOT NULL",
                start, end)) {
            models.computeIfAbsent(Objects.toString(row.get("scene"), "unknown"), k -> new ArrayList<>())
                    .add(Objects.toString(row.get("model"), "unknown"));
        }

        // ── 延迟样本（升序，全局上限采样）计算 P50/P95 ──
        Map<String, List<Integer>> latencies = new LinkedHashMap<>();
        int sampled = 0;
        boolean truncated = false;
        for (Map<String, Object> row : db.queryForList("""
                SELECT scene, latency_ms FROM ai_trace
                WHERE create_time>=? AND create_time<=? AND latency_ms IS NOT NULL
                ORDER BY create_time DESC LIMIT ?
                """, start, end, LATENCY_SAMPLE_LIMIT + 1)) {
            sampled++;
            if (sampled > LATENCY_SAMPLE_LIMIT) {
                truncated = true;
                break;
            }
            latencies.computeIfAbsent(Objects.toString(row.get("scene"), "unknown"), k -> new ArrayList<>())
                    .add(((Number) row.get("latency_ms")).intValue());
        }

        List<Map<String, Object>> scenes = new ArrayList<>();
        for (Map.Entry<String, SceneAgg> entry : aggs.entrySet()) {
            String scene = entry.getKey();
            SceneAgg agg = entry.getValue();
            List<Integer> samples = new ArrayList<>(latencies.getOrDefault(scene, List.of()));
            samples.sort(Comparator.naturalOrder());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scene", scene);
            item.put("calls", agg.calls);
            item.put("okCount", agg.ok);
            item.put("fallbackCount", agg.fallback);
            item.put("errorCount", agg.error);
            item.put("blockedCount", agg.blocked);
            item.put("successRate", rate(agg.ok, agg.calls));
            item.put("fallbackRate", rate(agg.fallback, agg.calls));
            item.put("errorRate", rate(agg.error, agg.calls));
            item.put("avgLatencyMs", agg.latencyRows == 0 ? null
                    : Math.round(agg.weightedLatency / agg.latencyRows));
            item.put("p50LatencyMs", percentile(samples, 50));
            item.put("p95LatencyMs", percentile(samples, 95));
            item.put("inputTokens", agg.inputTokens);
            item.put("outputTokens", agg.outputTokens);
            item.put("models", models.getOrDefault(scene, List.of()));
            scenes.add(item);
        }

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("from", start.toString());
        window.put("to", end.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("window", window);
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("scenes", scenes);
        result.put("note", "只统计 ai_trace 脱敏元数据（场景/模型/延迟/状态/tokens），不含用户标识与对话原文；"
                + "tokens 仅在客户端可获取时记录，未记录的行不参与求和；"
                + "P50/P95 每场景基于区间内延迟样本（全场景合计上限 " + LATENCY_SAMPLE_LIMIT + " 条，取最近）"
                + (truncated ? "，本区间样本已超上限，百分位为最近样本近似" : "")
                + "。scene 为熔断资源 key（llm-chat 等）；编排层 trace 另以业务场景名记录（companion 等）。");
        result.put("latencySampleTruncated", truncated);
        return result;
    }

    /** 百分率（%），保留 1 位小数；总量为 0 时返回 null（不造 0）。 */
    private static Double rate(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    /** 最近点插值百分位；无样本返回 null（与 traceSummary 的 0 口径区分：null 表示无数据）。 */
    static Integer percentile(List<Integer> sorted, int percent) {
        if (sorted == null || sorted.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(sorted.size() * percent / 100.0) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /** 单场景累加器。 */
    private static final class SceneAgg {
        long calls;
        long ok;
        long fallback;
        long error;
        long blocked;
        long other;
        long inputTokens;
        long outputTokens;
        double weightedLatency;
        long latencyRows;
    }
}
