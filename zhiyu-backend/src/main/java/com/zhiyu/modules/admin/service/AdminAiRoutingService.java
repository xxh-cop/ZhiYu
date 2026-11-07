package com.zhiyu.modules.admin.service;

import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.LlmClientRegistry;
import com.zhiyu.ai.routing.ModelRouter;
import com.zhiyu.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 模型路由只读总览（2026-11-08，方案 A：单端点聚合配置 + 统计）。
 *
 * <p>回答三个问题：① 现在谁在干活（capabilities：每个能力的档位/云模型/
 * cloud-only/兜底生效值与记账场景，来自 {@link ModelRouter} 只读出口与
 * {@link LlmClientRegistry} 装配状态，是<b>配置呈现</b>不是统计）；
 * ② 最近实际走了谁（distribution：ai_trace 按 scene+provider+result_status
 * 聚合的纯计数，并按事故锚点分 pre/post 两桶）；
 * ③ 异常由前端用 ①+② 推导，其中「cloud-only 是否被本地接管」的权威数字由
 * {@code cloudOnlyLocalCheck} 直接给出（区分修复前历史事故与修复后现行）。
 *
 * <p>只读、纯计数：不返回用户 ID、会话 ID、prompt、对话原文或自由文本原因。
 * 不改 {@code /api/admin/ai-metrics}（在用量产端点，不往里加 provider 维度）。
 */
@Service
@RequiredArgsConstructor
public class AdminAiRoutingService {

    /** 时间区间上限（天），与 ai-metrics 同口径，防止全表扫描。 */
    private static final int MAX_RANGE_DAYS = 90;

    private final JdbcTemplate db;
    private final ModelRouter modelRouter;
    private final LlmClientRegistry registry;

    /**
     * 历史事故锚点（application.yml：zhiyu.admin.ai-routing.incident-fixed-at）。
     * 默认值 = 修复提交 ffbcd7c 时间；锚点前 cloud-only 的 ollama 记录为
     * 已修复事故（LlmClientRegistry 父类型注入歧义）的证据，不计入现行红线。
     */
    @Value("${zhiyu.admin.ai-routing.incident-fixed-at:2026-11-07T22:12:48}")
    private String incidentFixedAtConfig;

    private LocalDateTime incidentFixedAt;

    @PostConstruct
    void parseIncidentFixedAt() {
        try {
            incidentFixedAt = LocalDateTime.parse(incidentFixedAtConfig.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "zhiyu.admin.ai-routing.incident-fixed-at 不是合法 ISO 本地日期时间: "
                            + incidentFixedAtConfig, e);
        }
    }

    /**
     * @param from           区间起（含），null 时取 {@code to - 7 天}
     * @param to             区间止（含），null 时取当前时间
     * @param anchorOverride 事故锚点临时覆盖（答辩/排查现场用）；null 时用配置值
     */
    public Map<String, Object> overview(LocalDateTime from, LocalDateTime to,
                                        LocalDateTime anchorOverride) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        if (!start.isBefore(end)) {
            throw new BusinessException(1001, "from 必须早于 to");
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new BusinessException(1001, "时间区间最长 " + MAX_RANGE_DAYS + " 天");
        }
        LocalDateTime anchor = anchorOverride != null ? anchorOverride : incidentFixedAt;

        List<Map<String, Object>> capabilities = capabilities();

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("from", start.toString());
        window.put("to", end.toString());
        result.put("window", window);
        result.put("generatedAt", LocalDateTime.now().toString());
        Map<String, Object> anchorInfo = new LinkedHashMap<>();
        anchorInfo.put("at", anchor.toString());
        anchorInfo.put("source", anchorOverride != null ? "override" : "config");
        anchorInfo.put("note", "历史事故锚点（声明的运维事实，非自动计算）：锚点前 cloud-only 的 ollama 记录为"
                + "已修复事故（LlmClientRegistry 父类型注入歧义，ffbcd7c 起云槽精确排除子类）的证据，"
                + "不计入现行红线；默认取配置 zhiyu.admin.ai-routing.incident-fixed-at，"
                + "可用 anchor 查询参数临时覆盖。");
        result.put("incidentAnchor", anchorInfo);
        result.put("registry", registryInfo());
        result.put("capabilities", capabilities);
        result.put("distribution", distribution(start, end, anchor));
        result.put("cloudOnlyLocalCheck", cloudOnlyLocalCheck(capabilities, start, end, anchor));
        result.put("note", "capabilities 为路由配置呈现（ModelRouter/LlmClientRegistry 生效状态），"
                + "distribution 为 ai_trace 按 scene+provider+result_status 聚合的纯计数（trace 粒度，不含内容），"
                + "period 按事故锚点分 preFix/postFix 两桶；"
                + "capabilities[].sceneKeys 是该能力的记账场景清单：一个能力可对应多个 scene"
                + "（如 listening_summary=llm-strong 主路径 + llm-message 回落路径），"
                + "一个 scene 也可被多个能力共用（llm-message 由 message/stuck_guide/listening_summary 回落共用，"
                + "按能力汇总时该 scene 计数会在各能力间重叠，归属以 scene 为准不重复拆分）；"
                + "provider=null 为编排层 trace（CompanionTraceService 不记 provider）、语音或加列前存量；"
                + "moderation_shadow 判定走 ai_audit_log 与影子台账，不在 ai_trace。"
                + "cloudOnlyLocalCheck 只统计「独占 scene」（归属能力全部 cloud-only）的 ollama 记录："
                + "postFixLocalCalls 为修复后窗口的现行值（应为 0），preFixLocalCalls 为锚点前历史事故证据。");
        return result;
    }

    /** 装配状态：云/本地/Mock 是否就位与演示开关生效值（不含任何密钥）。 */
    private Map<String, Object> registryInfo() {
        LlmClient cloud = registry.cloudOrNull();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cloudAvailable", cloud != null);
        info.put("cloudProvider", cloud == null ? null : cloud.providerName());
        info.put("localAvailable", registry.localOrNull() != null);
        info.put("localModel", modelRouter.localModel());
        info.put("mockAvailable", registry.mockOrNull() != null);
        info.put("localDemoMode", modelRouter.localDemoMode());
        info.put("shadowLocalEffective", modelRouter.shadowLocalEffective());
        return info;
    }

    /** 路由配置呈现：每个能力的档位、云模型、cloud-only、兜底生效值与记账场景。 */
    private List<Map<String, Object>> capabilities() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String task : modelRouter.knownTasks()) {
            ModelRouter.Route route = modelRouter.resolve(task);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("task", task);
            item.put("tier", route.tier());
            item.put("cloudModel", route.model());
            item.put("llmAllowed", route.llmAllowed());
            item.put("cloudOnly", modelRouter.cloudOnly(task));
            item.put("fallbackAllowed", modelRouter.fallbackAllowed(task));
            item.put("sceneKeys", modelRouter.sceneKeys(task));
            list.add(item);
        }
        return list;
    }

    /**
     * ai_trace 按 scene+provider+period 透视的纯计数。
     * period：create_time 早于事故锚点为 preFix，否则（含等于）为 postFix；
     * 行序 scene、provider、period 升序，provider NULL 在前。
     */
    private List<Map<String, Object>> distribution(LocalDateTime start, LocalDateTime end,
                                                   LocalDateTime anchor) {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> row : db.queryForList("""
                SELECT scene, provider, result_status,
                       CASE WHEN create_time < ? THEN 'preFix' ELSE 'postFix' END AS period,
                       COUNT(*) AS c
                FROM ai_trace WHERE create_time>=? AND create_time<=?
                GROUP BY scene, provider, result_status, period
                ORDER BY scene, provider, period
                """, anchor, start, end)) {
            String scene = Objects.toString(row.get("scene"), "unknown");
            String provider = (String) row.get("provider");
            String period = Objects.toString(row.get("period"), "postFix");
            Map<String, Object> item = rows.computeIfAbsent(
                    scene + "|" + Objects.toString(provider, "") + "|" + period,
                    k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("scene", scene);
                        m.put("provider", provider);
                        m.put("period", period);
                        m.put("calls", 0L);
                        m.put("okCount", 0L);
                        m.put("fallbackCount", 0L);
                        m.put("errorCount", 0L);
                        m.put("blockedCount", 0L);
                        m.put("otherCount", 0L);
                        return m;
                    });
            long count = row.get("c") instanceof Number n ? n.longValue() : 0L;
            item.put("calls", (long) item.get("calls") + count);
            switch (Objects.toString(row.get("result_status"), "")) {
                case "OK" -> item.put("okCount", (long) item.get("okCount") + count);
                case "FALLBACK" -> item.put("fallbackCount", (long) item.get("fallbackCount") + count);
                case "ERROR" -> item.put("errorCount", (long) item.get("errorCount") + count);
                case "BLOCKED" -> item.put("blockedCount", (long) item.get("blockedCount") + count);
                default -> item.put("otherCount", (long) item.get("otherCount") + count);
            }
        }
        return new ArrayList<>(rows.values());
    }

    /**
     * 「cloud-only 是否被本地接管」的权威数字：只统计<b>独占 scene</b>
     * （归属能力全部 cloud-only，无共享歧义——llm-message 与可兜底能力共用，不计）的
     * ollama 记录，并按锚点拆 pre/post。postFixLocalCalls 应为 0；
     * preFixLocalCalls 为已修复事故的历史证据（只追加保留）。
     */
    private Map<String, Object> cloudOnlyLocalCheck(List<Map<String, Object>> capabilities,
                                                    LocalDateTime start, LocalDateTime end,
                                                    LocalDateTime anchor) {
        // scene → 归属能力的 cloudOnly 集合；全部 cloudOnly 才是独占 scene
        Map<String, Boolean> exclusive = new LinkedHashMap<>();
        for (Map<String, Object> cap : capabilities) {
            boolean cloudOnly = Boolean.TRUE.equals(cap.get("cloudOnly"));
            @SuppressWarnings("unchecked")
            List<String> sceneKeys = (List<String>) cap.get("sceneKeys");
            for (String scene : sceneKeys) {
                exclusive.merge(scene, cloudOnly, (a, b) -> a && b);
            }
        }
        List<String> exclusiveScenes = exclusive.entrySet().stream()
                .filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().toList();

        long pre = 0;
        long post = 0;
        if (!exclusiveScenes.isEmpty()) {
            String placeholders = String.join(",", exclusiveScenes.stream().map(s -> "?").toList());
            List<Object> args = new ArrayList<>();
            args.add(anchor);
            args.add(start);
            args.add(end);
            args.addAll(exclusiveScenes);
            for (Map<String, Object> row : db.queryForList("""
                    SELECT CASE WHEN create_time < ? THEN 'pre' ELSE 'post' END AS p, COUNT(*) AS c
                    FROM ai_trace
                    WHERE create_time>=? AND create_time<=? AND provider='ollama'
                      AND scene IN (""" + placeholders + ") GROUP BY p", args.toArray())) {
                long count = row.get("c") instanceof Number n ? n.longValue() : 0L;
                if ("pre".equals(Objects.toString(row.get("p"), ""))) {
                    pre += count;
                } else {
                    post += count;
                }
            }
        }

        Map<String, Object> check = new LinkedHashMap<>();
        check.put("anchor", anchor.toString());
        check.put("exclusiveScenes", exclusiveScenes);
        check.put("preFixLocalCalls", pre);
        check.put("postFixLocalCalls", post);
        check.put("verdict", post == 0 ? "CLEAN" : "VIOLATION");
        return check;
    }
}
