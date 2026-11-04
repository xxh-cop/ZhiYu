package com.zhiyu.modules.emotion.service;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 情绪基线偏离提醒（AM1，2026-11-04）。
 *
 * <p><b>只做提醒，不做判断</b>：本服务不产出任何诊断性结论，只产出「是否提示 +
 * 提示等级 + 建议动作类型」三要素，文案与免责声明由前端按等级渲染。
 *
 * <p><b>计算口径（全部配置化，zhiyu.emotion.baseline.*）</b>：
 * 以「低落类情绪（heavy-moods，默认 depressed）记录占比的近期-基线位移」为信号：
 * <ul>
 *   <li>recentShare = 近 {@code short-window-days} 天低落类占比（便签 ∪ 情绪会话，仅本人）</li>
 *   <li>baseShare = 之前 {@code long-window-days} 天（不含近期窗口）低落类占比</li>
 *   <li>提示条件：recentShare − baseShare ≥ {@code share-uplift}
 *       且近期记录 ≥ {@code min-recent-records}（防一两条就报）
 *       且历史总记录 ≥ {@code min-history-records}（冷启动不报）</li>
 * </ul>
 * 默认值依据：share-uplift=0.30 对应「低落占比上移约三分之一」的可感知变化；
 * min-history=8 / min-recent=3 是防稀疏误报的最小样本（远低于此任何占比都不可信）；
 * cooldown=72h 保证同档位提醒不密集刷屏。阈值属产品级决策，调参走配置评审。
 *
 * <p><b>冷却</b>：提示返回时写 user.baseline_nudge_last_at/last_level；冷却期内同档位不再提示。
 * <p><b>可关闭</b>：user.baseline_nudge_enabled=0 时恒返回 nudge=false。
 */
@Slf4j
@Service
public class EmotionBaselineService {

    private final JdbcTemplate jdbc;
    private final BusinessAccess access;

    @Value("${zhiyu.emotion.baseline.enabled:true}")
    private boolean enabled;
    @Value("${zhiyu.emotion.baseline.short-window-days:3}")
    private int shortWindowDays;
    @Value("${zhiyu.emotion.baseline.long-window-days:28}")
    private int longWindowDays;
    @Value("${zhiyu.emotion.baseline.min-history-records:8}")
    private int minHistoryRecords;
    @Value("${zhiyu.emotion.baseline.min-recent-records:3}")
    private int minRecentRecords;
    @Value("${zhiyu.emotion.baseline.share-uplift:0.30}")
    private double shareUplift;
    @Value("${zhiyu.emotion.baseline.cooldown-hours:72}")
    private int cooldownHours;
    @Value("${zhiyu.emotion.baseline.heavy-moods:depressed}")
    private String heavyMoods;

    public EmotionBaselineService(JdbcTemplate jdbc, BusinessAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /** 查询当前用户是否应提示（情绪数据敏感 → 挂隐私同意门）。 */
    public Map<String, Object> nudge() {
        long uid = access.consentedUser();
        if (!enabled) {
            return result(false, false);
        }
        var rows = jdbc.queryForList(
                "SELECT baseline_nudge_enabled, baseline_nudge_last_at, baseline_nudge_last_level"
                        + " FROM user WHERE id=? AND deleted=0 AND status=1", uid);
        if (rows.isEmpty()) {
            throw new BusinessException(1002, "登录已失效");
        }
        var row = rows.get(0);
        // 用户已关闭：enabled=false 且恒不提示
        if (!Integer.valueOf(1).equals(((Number) row.get("baseline_nudge_enabled")).intValue())) {
            return result(false, false);
        }
        // 冷却期（同档位不重复）
        Object lastAt = row.get("baseline_nudge_last_at");
        if (lastAt != null && "gentle".equals(row.get("baseline_nudge_last_level"))) {
            // H2 返回 Timestamp、MySQL Connector/J 8 返回 LocalDateTime——两路兼容（W 批同类坑）
            LocalDateTime last = lastAt instanceof Timestamp t
                    ? t.toLocalDateTime() : (LocalDateTime) lastAt;
            if (last.isAfter(LocalDateTime.now().minusHours(cooldownHours))) {
                return result(true, false);
            }
        }

        List<String> heavy = List.of(heavyMoods.split(","));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentStart = now.minusDays(shortWindowDays);
        LocalDateTime baseStart = now.minusDays(longWindowDays);

        long recentTotal = count(uid, recentStart, now, heavy, false);
        long recentHeavy = count(uid, recentStart, now, heavy, true);
        long historyTotal = count(uid, baseStart, recentStart, heavy, false);
        long historyHeavy = count(uid, baseStart, recentStart, heavy, true);

        // 冷启动/稀疏：样本不足一律不提示（不误报）
        if (recentTotal < minRecentRecords || recentTotal + historyTotal < minHistoryRecords) {
            return result(true, false);
        }
        double recentShare = (double) recentHeavy / recentTotal;
        double baseShare = historyTotal == 0 ? 0.0 : (double) historyHeavy / historyTotal;
        if (recentShare - baseShare < shareUplift) {
            return result(true, false);
        }

        // 命中：记冷却（同档位）
        jdbc.update("UPDATE user SET baseline_nudge_last_at=CURRENT_TIMESTAMP,"
                + " baseline_nudge_last_level='gentle', update_time=CURRENT_TIMESTAMP WHERE id=?", uid);
        log.info("基线偏离提醒触发: userId={}, recentShare={}, baseShare={}", uid,
                String.format("%.2f", recentShare), String.format("%.2f", baseShare));
        return result(true, true);
    }

    /** 开关设置（用户偏好；登录即可，非隐私撤回路径）。 */
    public void setEnabled(boolean on) {
        long uid = access.user();
        jdbc.update("UPDATE user SET baseline_nudge_enabled=?, update_time=CURRENT_TIMESTAMP WHERE id=?",
                on ? 1 : 0, uid);
        log.info("基线提醒开关变更: userId={}, enabled={}", uid, on);
    }

    private Map<String, Object> result(boolean userEnabled, boolean nudge) {
        return Map.of("enabled", userEnabled,
                "nudge", nudge,
                "level", nudge ? "gentle" : "",
                "actionType", nudge ? "craft" : "");
    }

    /** 统计窗口内（[from,to)）便签 ∪ 情绪会话的记录数（仅本人、未删除、mood 非空）。 */
    private long count(long uid, LocalDateTime from, LocalDateTime to, List<String> heavy, boolean heavyOnly) {
        String in = String.join(",", heavy.stream().map(m -> "'" + m.trim() + "'").toList());
        String heavyClause = heavyOnly ? " AND mood IN (" + in + ")" : "";
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + " SELECT mood, create_time FROM emotion_session WHERE user_id=? AND deleted=0"
                        + " AND mood IS NOT NULL AND create_time>=? AND create_time<?" + heavyClause
                        + " UNION ALL"
                        + " SELECT mood, create_time FROM emotion_note WHERE user_id=? AND deleted=0"
                        + " AND mood IS NOT NULL AND create_time>=? AND create_time<?" + heavyClause
                        + ") t",
                Long.class, uid, Timestamp.valueOf(from), Timestamp.valueOf(to),
                uid, Timestamp.valueOf(from), Timestamp.valueOf(to));
        return n == null ? 0 : n;
    }
}
