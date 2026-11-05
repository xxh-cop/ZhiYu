package com.zhiyu.modules.craft.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 推荐反馈回灌加权（AM2，2026-11-04）。
 *
 * <p><b>接在现有规则之后</b>：规则层打分与排序逻辑一行不动，本服务只在
 * {@code CraftRecommendServiceImpl} 选品排序前提供每个项目的加权增量。
 *
 * <p><b>确定性、可解释、可回滚</b>：不引入向量/模型——按本人反馈事件（CLICK 正 /
 * DISMISS 负）对被点项目的疗愈标签累计加权，再映射回候选项目（按其 therapy_tags）：
 * <pre>
 *   projectBoost(p) = Σ 事件 on p:  sign × weight × decayPerDay ^ 事件年龄(天)
 *   tagScore(t)     = Σ 含标签 t 的被反馈项目的 projectBoost
 *   boost(候选 c)    = min(cap, Σ c.therapy_tags 的 tagScore)
 *   dismissSpreadEnabled=true 时对负反馈净额额外叠加（2026-11-05 缺口修复）：
 *     boost(c) += projectBoost(c)（c 为被 DISMISS 项目本身——无标签项目不再免疫）
 *             +  categoryScore(c.category)（Σ 同品类被 DISMISS 项目的负净额）
 * </pre>
 * 反馈不足 {@code min-events} 条时返回空表（调用方跳过加权，排序与原规则逐条一致）；
 * 开关关闭时同样返回空表。CLICK 语义不变（只经标签映射，不自加权、不入品类分）。
 *
 * <p><b>数据边界</b>：只读本人 craft_recommend_event；不把便签/对话等敏感内容作为特征。
 */
@Slf4j
@Service
public class RecommendFeedbackBoost {

    private final JdbcTemplate jdbc;

    public RecommendFeedbackBoost(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Value("${zhiyu.recommend.feedback.enabled:true}")
    private boolean enabled;
    @Value("${zhiyu.recommend.feedback.window-days:90}")
    private int windowDays;
    @Value("${zhiyu.recommend.feedback.min-events:3}")
    private int minEvents;
    @Value("${zhiyu.recommend.feedback.click-weight:0.15}")
    private double clickWeight;
    @Value("${zhiyu.recommend.feedback.dismiss-weight:0.15}")
    private double dismissWeight;
    @Value("${zhiyu.recommend.feedback.decay-per-day:0.98}")
    private double decayPerDay;
    @Value("${zhiyu.recommend.feedback.cap:0.6}")
    private double cap;
    /**
     * DISMISS 强化开关（2026-11-05 核实缺口后补）：负反馈除标签映射外，
     * 叠加「被点项目自降权」（无 therapy_tags 的项目此前完全免疫，连自身都不降）
     * 与「同 category 降权」（同类横向抑制）。false 时回到 AM2 原始口径（仅标签映射），
     * 与既有行为逐分一致。CLICK 语义不变（仍只经标签映射）。
     */
    @Value("${zhiyu.recommend.feedback.dismiss-spread-enabled:true}")
    private boolean dismissSpreadEnabled;

    public boolean enabled() {
        return enabled;
    }

    /** H2 返回 Timestamp、MySQL Connector/J 8 返回 LocalDateTime——两路兼容（W/AM 批同类坑）。 */
    private static LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return (LocalDateTime) v;
    }

    /**
     * 计算当前用户对每个项目的反馈加权增量。
     *
     * @return projectId → boost（非负=正反馈占优；可为负=负反馈占优）；
     *         开关关闭或反馈不足 min-events 时返回空表（调用方不得改变排序）
     */
    public Map<Long, Double> boosts(Long userId) {
        if (!enabled || userId == null) {
            return Map.of();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);
        var events = jdbc.queryForList(
                "SELECT project_id, event_type, create_time FROM craft_recommend_event"
                        + " WHERE user_id=? AND deleted=0 AND create_time>=?",
                userId, Timestamp.valueOf(since));
        if (events.size() < minEvents) {
            return Map.of();
        }

        // 项目级累计分（含时间衰减）
        Map<Long, Double> projectBoost = new HashMap<>();
        for (var e : events) {
            long pid = ((Number) e.get("project_id")).longValue();
            double sign = "DISMISS".equals(e.get("event_type")) ? -dismissWeight : clickWeight;
            long ageDays = ChronoUnit.DAYS.between(toLocalDateTime(e.get("create_time")), LocalDateTime.now());
            double w = sign * Math.pow(decayPerDay, Math.max(0, ageDays));
            projectBoost.merge(pid, w, Double::sum);
        }

        // 项目标签与品类映射（被反馈项目才需要标签；品类用于 DISMISS 同类降权）
        Map<Long, String> projectTags = new HashMap<>();
        Map<Long, String> projectCategory = new HashMap<>();
        for (var row : jdbc.queryForList(
                "SELECT id, therapy_tags, category FROM craft_project WHERE deleted=0")) {
            long pid = ((Number) row.get("id")).longValue();
            projectTags.put(pid, row.get("therapy_tags") == null ? "" : row.get("therapy_tags").toString());
            projectCategory.put(pid, row.get("category") == null ? "" : row.get("category").toString());
        }

        // 标签分 → 映射回每个候选项目（按标签聚合）
        Map<String, Double> tagScore = new HashMap<>();
        for (var entry : projectBoost.entrySet()) {
            String tags = projectTags.get(entry.getKey());
            if (tags == null || tags.isBlank()) {
                continue;
            }
            for (String tag : tags.split(",")) {
                tagScore.merge(tag.trim(), entry.getValue(), Double::sum);
            }
        }

        // DISMISS 强化（dismissSpreadEnabled）：负反馈净额叠加被点项目自降权 + 同品类降权
        Map<String, Double> categoryScore = new HashMap<>();
        if (dismissSpreadEnabled) {
            for (var entry : projectBoost.entrySet()) {
                if (entry.getValue() >= 0) {
                    continue; // 只强化负反馈；CLICK 语义不变
                }
                String category = projectCategory.get(entry.getKey());
                if (category != null && !category.isBlank()) {
                    categoryScore.merge(category.trim(), entry.getValue(), Double::sum);
                }
            }
        }

        Map<Long, Double> result = new HashMap<>();
        for (var pe : projectTags.entrySet()) {
            double boost = 0;
            for (String tag : pe.getValue().split(",")) {
                boost += tagScore.getOrDefault(tag.trim(), 0.0);
            }
            if (dismissSpreadEnabled) {
                // 自降权：被 DISMISS 的项目自身必降（修复无标签项目免疫的缺口）
                Double direct = projectBoost.get(pe.getKey());
                if (direct != null && direct < 0) {
                    boost += direct;
                }
                // 同品类降权
                String category = projectCategory.get(pe.getKey());
                if (category != null && !category.isBlank()) {
                    boost += categoryScore.getOrDefault(category.trim(), 0.0);
                }
            }
            if (boost != 0) {
                result.put(pe.getKey(), Math.max(-cap, Math.min(cap, boost)));
            }
        }
        return result;
    }
}
