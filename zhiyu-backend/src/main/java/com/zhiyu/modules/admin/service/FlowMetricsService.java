package com.zhiyu.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.calibration.FlowCalibrationService;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.takedown.TakedownProperties;
import com.zhiyu.modules.craft.takedown.TakedownStateResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 心流/专注度量看板聚合（阶段 1-2，2026-11-05）。
 *
 * <p>口径以 {@code docs/flow-metrics-dashboard-literature-20261105.md} §3/§9 为准，不另行发明：
 * <ul>
 *   <li>分母 = 会话各步骤 {@code step_leave.dwell_seconds} 之和（活跃创作时长）；
 *       分子 = {@code flow_seconds}（2026-11-05 起为「阈值后深度沉浸秒数」，存量结构性不可比，§9.7）。</li>
 *   <li>会话级占比 = 分子/分母；看板指标 = 人×周会话占比中位数（按 start_time 归周，
 *       Asia/Shanghai，周一起始），报 p50/p90 与分桶样本数，不给均值大数字。</li>
 *   <li>仅终态会话（status 3 完成 / 4 放弃）进入分布；在途会话、无 step_leave 事件的会话
 *       只在 totals 计数，不参与占比。</li>
 *   <li>剔除（分子分母同口径、会话级；判定优先级 readOnly → projectStatus+takedown_kind →
 *       事件流证据：精确档 step_gate_blocked，兜底档 session_archive）：项目当前只读（合规下架窗口内/已归档/运营下架已到期，
 *       与 {@link TakedownStateResolver} 同口径）且会话最后活动时间不早于只读起点 → 整会话剔除；
 *       待审项目（status=0）会话剔除（多为测试/演示数据，§9.3）。剔除明细在 exclusions 返回。</li>
 *   <li>运营下架未到期会话计入，但按 takedown_kind=operational 单独分面，不与常态并线（§9.2）。</li>
 *   <li>分层：craft_project.level 与活跃时长桶（&lt;5 / 5-15 / 15-30 / &gt;30 分钟）；
 *       单元去重用户数 &lt;10 → sparse=true（前端标灰，§3.3-2）。</li>
 *   <li>事件证据分两档（2026-11-05）：<b>精确档</b> = step_gate_blocked（心跳被下架闸口
 *       拒绝的真实时点，服务端派生、每会话至多一条）；<b>兜底档</b> = session_archive
 *       （归档 Worker 派生，仅会话无 gate 事件时使用，如被拒后未再打开）。起点取 resolver
 *       推导与事件证据的较早者；剔除明细 readOnlyEvidence 如实标注档位。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowMetricsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 区间上限（天），超出 1001 */
    private static final int MAX_RANGE_DAYS = 90;
    /** 分层单元最少去重用户数，不足标 sparse（§3.3-2） */
    private static final int SPARSE_MIN_USERS = 10;
    /** 剔除明细返回上限（总数在 totals.excludedSessions） */
    private static final int EXCLUSION_DETAIL_LIMIT = 50;
    /** 时长桶边界（秒）：<5 / 5-15 / 15-30 / >30 分钟（§3.1，工程折中） */
    private static final int BUCKET_5_MIN = 300;
    private static final int BUCKET_15_MIN = 900;
    private static final int BUCKET_30_MIN = 1800;
    /** 分布分桶数（0~0.1 … 0.9+ 共 10 桶；占比 >1 的脏数据归入末桶） */
    private static final int RATIO_BUCKETS = 10;

    private final JdbcTemplate db;
    private final FlowCalibrationService flowCalibrationService;
    private final TakedownProperties takedownProperties;
    private final AesEncryptor crypto;
    private final ObjectMapper json;

    /**
     * 看板聚合主入口。
     *
     * @param fromRaw 起始日期（含），null → 今天-29 天
     * @param toRaw   截止日期（含），null → 今天
     */
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(String fromRaw, String toRaw) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = parseDate(fromRaw, today.minusDays(29), "from");
        LocalDate to = parseDate(toRaw, today, "to");
        if (from.isAfter(to)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "from 不得晚于 to");
        }
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "查询跨度不得超过 " + MAX_RANGE_DAYS + " 天");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // ── 1. 区间内会话（按 start_time 归属） ──
        List<Map<String, Object>> sessionRows = db.queryForList(
                "SELECT id,user_id,project_id,status,start_time,end_time,flow_seconds"
                        + " FROM craft_session WHERE deleted=0 AND start_time IS NOT NULL"
                        + " AND start_time>=? AND start_time<?",
                start, endExclusive);

        // ── 2. 会话事件聚合（活跃时长/最后活动/归档事件起点） ──
        Map<Long, EventAgg> eventAgg = new HashMap<>();
        if (!sessionRows.isEmpty()) {
            String in = joinIds(sessionRows);
            db.queryForList(
                    "SELECT session_id,"
                            + " SUM(CASE WHEN event_type='step_leave' THEN COALESCE(dwell_seconds,0) ELSE 0 END) AS active_seconds,"
                            + " MAX(occurred_at) AS last_event_at,"
                            + " MIN(CASE WHEN event_type='session_archive' THEN occurred_at END) AS first_archive_at,"
                            + " MIN(CASE WHEN event_type='step_gate_blocked' THEN occurred_at END) AS first_gate_blocked_at"
                            + " FROM craft_session_event WHERE deleted=0 AND session_id IN (" + in + ")"
                            + " GROUP BY session_id")
                    .forEach(row -> {
                        EventAgg agg = new EventAgg();
                        Object active = row.get("active_seconds");
                        agg.activeSeconds = active == null ? 0 : ((Number) active).longValue();
                        agg.lastEventAt = toLdt(row.get("last_event_at"));
                        agg.firstArchiveAt = toLdt(row.get("first_archive_at"));
                        agg.firstGateBlockedAt = toLdt(row.get("first_gate_blocked_at"));
                        eventAgg.put(((Number) row.get("session_id")).longValue(), agg);
                    });
        }

        // ── 3. 项目批量装载（剔除判定与分层维度） ──
        Map<Long, CraftProject> projects = loadProjects(sessionRows);

        // ── 4. 会话分类：在途 / 剔除 / 纳入 ──
        int inProgress = 0;
        int excludedReadOnly = 0;
        int excludedPending = 0;
        int excludedNoEvents = 0;
        List<Map<String, Object>> exclusionDetails = new ArrayList<>();
        // 纳入会话：会话级占比记录
        List<SessionRatio> included = new ArrayList<>();
        for (Map<String, Object> row : sessionRows) {
            long sessionId = ((Number) row.get("id")).longValue();
            long userId = ((Number) row.get("user_id")).longValue();
            Long projectId = row.get("project_id") == null ? null : ((Number) row.get("project_id")).longValue();
            int status = ((Number) row.get("status")).intValue();
            if (status == 1 || status == 2) {
                inProgress++;
                continue;
            }
            if (status != 3 && status != 4) {
                // 0 待开始：从未启动，不算创作行为
                inProgress++;
                continue;
            }
            CraftProject project = projectId == null ? null : projects.get(projectId);
            EventAgg agg = eventAgg.get(sessionId);
            LocalDateTime startTime = toLdt(row.get("start_time"));
            LocalDateTime endTime = toLdt(row.get("end_time"));

            // 剔除判定（优先级：readOnly 标志 → projectStatus+kind → 事件流证据）。
            // 事件证据分档（2026-11-05）：精确档 = step_gate_blocked（心跳被闸口拒绝的真实时点），
            // 兜底档 = session_archive（归档 Worker 派生，仅无 gate 事件的会话使用）。
            if (project != null && Integer.valueOf(0).equals(project.getStatus())) {
                excludedPending++;
                addExclusion(exclusionDetails, sessionId, projectId, "pending_project", null, null);
                continue;
            }
            LocalDateTime readOnlySince = readOnlySince(project, now);
            String readOnlyEvidence = readOnlySince != null ? "project_state" : null;
            if (agg != null) {
                LocalDateTime eventEvidence = agg.firstGateBlockedAt != null
                        ? agg.firstGateBlockedAt : agg.firstArchiveAt;
                String eventKind = agg.firstGateBlockedAt != null ? "step_gate_blocked"
                        : (agg.firstArchiveAt != null ? "session_archive" : null);
                if (eventEvidence != null && (readOnlySince == null || eventEvidence.isBefore(readOnlySince))) {
                    readOnlySince = eventEvidence;
                    readOnlyEvidence = eventKind;
                }
            }
            if (readOnlySince != null) {
                LocalDateTime lastActivity = lastActivity(startTime, endTime, agg);
                if (lastActivity == null || !lastActivity.isBefore(readOnlySince)) {
                    excludedReadOnly++;
                    addExclusion(exclusionDetails, sessionId, projectId, "read_only", readOnlySince, readOnlyEvidence);
                    continue;
                }
            }
            if (agg == null || agg.activeSeconds <= 0) {
                excludedNoEvents++;
                continue;
            }
            long flow = row.get("flow_seconds") == null ? 0L : ((Number) row.get("flow_seconds")).longValue();
            SessionRatio sr = new SessionRatio();
            sr.sessionId = sessionId;
            sr.userId = userId;
            sr.projectId = projectId;
            sr.activeSeconds = agg.activeSeconds;
            sr.flowSeconds = flow;
            sr.ratio = (double) flow / (double) agg.activeSeconds;
            sr.weekStart = startTime.atZone(ZONE).toLocalDate().with(DayOfWeek.MONDAY);
            sr.level = project == null ? null : project.getLevel();
            sr.durationBucket = durationBucket(agg.activeSeconds);
            sr.takedownKind = takedownFacet(project);
            included.add(sr);
        }

        // ── 5. 人×周聚合：每组会话占比中位数 ──
        Map<String, List<SessionRatio>> personWeekGroups = new LinkedHashMap<>();
        for (SessionRatio sr : included) {
            personWeekGroups.computeIfAbsent(sr.userId + "@" + sr.weekStart, k -> new ArrayList<>()).add(sr);
        }
        List<PersonWeek> personWeeks = new ArrayList<>();
        for (List<SessionRatio> group : personWeekGroups.values()) {
            PersonWeek pw = new PersonWeek();
            pw.userId = group.get(0).userId;
            pw.weekStart = group.get(0).weekStart;
            pw.sessionCount = group.size();
            pw.activeSeconds = group.stream().mapToLong(s -> s.activeSeconds).sum();
            pw.flowSeconds = group.stream().mapToLong(s -> s.flowSeconds).sum();
            List<Double> ratios = group.stream().map(s -> s.ratio).sorted().toList();
            pw.medianRatio = percentile(ratios, 0.5);
            personWeeks.add(pw);
        }

        // ── 6. 分布：人×周中位占比的 p50/p90 与分桶 ──
        List<Double> pwRatios = personWeeks.stream().map(pw -> pw.medianRatio).sorted().toList();
        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("personWeeks", personWeeks.size());
        distribution.put("p50", percentile(pwRatios, 0.5));
        distribution.put("p90", percentile(pwRatios, 0.9));
        distribution.put("buckets", ratioBuckets(pwRatios));

        // ── 7. 周趋势（人×周值再取中位 + IQR + 活跃人数） ──
        Map<LocalDate, List<PersonWeek>> byWeek = new TreeMap<>();
        for (PersonWeek pw : personWeeks) {
            byWeek.computeIfAbsent(pw.weekStart, k -> new ArrayList<>()).add(pw);
        }
        List<Map<String, Object>> weekly = new ArrayList<>();
        for (Map.Entry<LocalDate, List<PersonWeek>> entry : byWeek.entrySet()) {
            List<PersonWeek> group = entry.getValue();
            List<Double> medians = group.stream().map(pw -> pw.medianRatio).sorted().toList();
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("weekStart", entry.getKey().toString());
            w.put("activeUsers", group.stream().map(pw -> pw.userId).distinct().count());
            w.put("sessionCount", group.stream().mapToInt(pw -> pw.sessionCount).sum());
            w.put("medianRatio", percentile(medians, 0.5));
            w.put("p25", percentile(medians, 0.25));
            w.put("p75", percentile(medians, 0.75));
            w.put("activeSeconds", group.stream().mapToLong(pw -> pw.activeSeconds).sum());
            w.put("flowSeconds", group.stream().mapToLong(pw -> pw.flowSeconds).sum());
            weekly.add(w);
        }

        // ── 8. 分层（会话级占比 p50/p90；单元用户数 <10 标 sparse） ──
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("byLevel", facet(included, s -> s.level == null ? "unknown" : String.valueOf(s.level)));
        facets.put("byDurationBucket", facet(included, s -> s.durationBucket));
        facets.put("byTakedownKind", facet(included, s -> s.takedownKind));

        // ── 9. 行为信号事件计数（区间内全量，含在途会话） ──
        Map<String, Integer> events = new LinkedHashMap<>();
        for (String key : List.of("step_leave", "dwell_stagnation", "step_backtrack",
                "breathing_prompt", "breathing_responded", "suspected_away", "export", "session_archive",
                "step_gate_blocked")) {
            events.put(key, 0);
        }
        db.queryForList(
                        "SELECT event_type, COUNT(*) AS n FROM craft_session_event"
                                + " WHERE deleted=0 AND occurred_at>=? AND occurred_at<? GROUP BY event_type",
                        start, endExclusive)
                .forEach(row -> events.put(String.valueOf(row.get("event_type")),
                        ((Number) row.get("n")).intValue()));

        // ── 10. 自评校准（复用阶段 1-1 口径）与响应率 ──
        Map<String, Object> calibration = flowCalibrationService.adminStats();
        Map<String, Object> selfReport = selfReportStats(start, endExclusive);

        // ── 11. totals / unavailable / 口径变更 ──
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("sessionsInRange", sessionRows.size());
        totals.put("inProgressSessions", inProgress);
        totals.put("excludedReadOnly", excludedReadOnly);
        totals.put("excludedPendingProject", excludedPending);
        totals.put("excludedNoEvents", excludedNoEvents);
        totals.put("includedSessions", included.size());
        totals.put("activeSeconds", included.stream().mapToLong(s -> s.activeSeconds).sum());
        totals.put("flowSeconds", included.stream().mapToLong(s -> s.flowSeconds).sum());
        totals.put("activeUsers", included.stream().map(s -> s.userId).distinct().count());

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("from", from.toString());
        range.put("to", to.toString());
        range.put("days", (int) (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() + 1));
        out.put("range", range);
        out.put("generatedAt", ZonedDateTime.now(ZONE).toOffsetDateTime().toString());
        out.put("metricLabel", String.valueOf(calibration.get("metricLabel")));
        out.put("calibration", calibration);
        out.put("selfReport", selfReport);
        out.put("totals", totals);
        out.put("exclusions", exclusionDetails);
        out.put("distribution", distribution);
        out.put("weekly", weekly);
        out.put("facets", facets);
        out.put("events", events);
        out.put("definitionChanges", definitionChanges());
        out.put("unavailable", unavailable(calibration));
        return out;
    }

    // ──────────────────────────────────────────────
    //  剔除判定
    // ──────────────────────────────────────────────

    /**
     * 项目当前只读时推导只读起点；非只读返回 null。
     * 合规下架窗口内 → takedown_at；已归档 → archive_at ?? export_until ?? takedown_at；
     * 运营下架到期（resolver 判 readOnly）→ 其 deadline（archive_at 或推导值）。
     * 历史 NULL kind 终态下架与合规同口径（resolver 已含该语义）。
     */
    private LocalDateTime readOnlySince(CraftProject project, LocalDateTime now) {
        if (project == null) {
            return null;
        }
        var view = TakedownStateResolver.resolve(project, takedownProperties, now);
        if (!view.readOnly()) {
            return null;
        }
        int status = project.getStatus() == null ? 0 : project.getStatus();
        if (status == 0) {
            // 待审在调用方单独剔除（pending_project），不走只读口径
            return null;
        }
        if (view.effectiveStatus() == 3) {
            LocalDateTime since = project.getArchiveAt() != null ? project.getArchiveAt()
                    : project.getExportUntil() != null ? project.getExportUntil()
                    : project.getTakedownAt();
            // 归档时间缺失（脏数据）时保守起全剔：起点退化为「最早可能」即会话开始后的一切
            return since != null ? since : LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        // status=2：合规窗口内（含历史 NULL kind）→ 下架即只读；运营到期 → resolver 的 deadline
        if (TakedownStateResolver.KIND_OPERATIONAL.equals(project.getTakedownKind())) {
            return view.deadline() != null ? view.deadline() : project.getTakedownAt();
        }
        return project.getTakedownAt() != null ? project.getTakedownAt() : LocalDateTime.of(1970, 1, 1, 0, 0);
    }

    private LocalDateTime lastActivity(LocalDateTime startTime, LocalDateTime endTime, EventAgg agg) {
        LocalDateTime last = endTime != null ? endTime : startTime;
        if (agg != null && agg.lastEventAt != null && (last == null || agg.lastEventAt.isAfter(last))) {
            last = agg.lastEventAt;
        }
        return last;
    }

    private void addExclusion(List<Map<String, Object>> details, long sessionId, Long projectId,
                              String reason, LocalDateTime readOnlySince, String readOnlyEvidence) {
        if (details.size() >= EXCLUSION_DETAIL_LIMIT) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        // 雪花 ID 按字符串下发，前端不得转 Number
        item.put("sessionId", String.valueOf(sessionId));
        item.put("projectId", projectId == null ? null : String.valueOf(projectId));
        item.put("reason", reason);
        item.put("readOnlySince", readOnlySince == null ? null : readOnlySince.toString());
        // 只读起点证据档：project_state（项目当前状态推导）/ step_gate_blocked（精确档，心跳被拒时刻）
        // / session_archive（兜底档，归档 Worker 派生事件）；非只读剔除为 null
        item.put("readOnlyEvidence", readOnlyEvidence);
        details.add(item);
    }

    // ──────────────────────────────────────────────
    //  分层与分布
    // ──────────────────────────────────────────────

    private interface FacetKey {
        String of(SessionRatio s);
    }

    /** 分层聚合：会话级占比 p50/p90 + 会话/人数；单元用户数 <10 标 sparse。 */
    private List<Map<String, Object>> facet(List<SessionRatio> sessions, FacetKey keyFn) {
        Map<String, List<SessionRatio>> groups = new LinkedHashMap<>();
        for (SessionRatio s : sessions) {
            groups.computeIfAbsent(keyFn.of(s), k -> new ArrayList<>()).add(s);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<SessionRatio>> entry : groups.entrySet()) {
            List<SessionRatio> group = entry.getValue();
            List<Double> ratios = group.stream().map(s -> s.ratio).sorted().toList();
            long users = group.stream().map(s -> s.userId).distinct().count();
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("key", entry.getKey());
            cell.put("sessions", group.size());
            cell.put("users", users);
            cell.put("p50", percentile(ratios, 0.5));
            cell.put("p90", percentile(ratios, 0.9));
            cell.put("activeSeconds", group.stream().mapToLong(s -> s.activeSeconds).sum());
            cell.put("flowSeconds", group.stream().mapToLong(s -> s.flowSeconds).sum());
            cell.put("sparse", users < SPARSE_MIN_USERS);
            out.add(cell);
        }
        return out;
    }

    private String durationBucket(long activeSeconds) {
        if (activeSeconds < BUCKET_5_MIN) {
            return "lt5";
        }
        if (activeSeconds < BUCKET_15_MIN) {
            return "m5to15";
        }
        if (activeSeconds < BUCKET_30_MIN) {
            return "m15to30";
        }
        return "gt30";
    }

    /** 下架分面：无下架 / 运营下架 / 合规下架（历史 NULL kind 的终态下架归合规，与 resolver 一致）。 */
    private String takedownFacet(CraftProject project) {
        if (project == null) {
            return "none";
        }
        if (TakedownStateResolver.KIND_OPERATIONAL.equals(project.getTakedownKind())) {
            return "operational";
        }
        if (TakedownStateResolver.KIND_COMPLIANCE.equals(project.getTakedownKind())) {
            return "compliance";
        }
        int status = project.getStatus() == null ? 0 : project.getStatus();
        if (status >= 2) {
            return "compliance";
        }
        return "none";
    }

    /** 人×周中位占比的 10 桶分布（占比 >1 归末桶，桶 max=null 表示开区间）。 */
    private List<Map<String, Object>> ratioBuckets(List<Double> sortedRatios) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        int[] counts = new int[RATIO_BUCKETS];
        for (double r : sortedRatios) {
            int idx = (int) Math.floor(r * RATIO_BUCKETS);
            if (idx < 0) {
                idx = 0;
            }
            if (idx >= RATIO_BUCKETS) {
                idx = RATIO_BUCKETS - 1;
            }
            counts[idx]++;
        }
        for (int i = 0; i < RATIO_BUCKETS; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("index", i);
            b.put("min", i / (double) RATIO_BUCKETS);
            b.put("max", i == RATIO_BUCKETS - 1 ? null : (i + 1) / (double) RATIO_BUCKETS);
            b.put("count", counts[i]);
            buckets.add(b);
        }
        return buckets;
    }

    /** 线性插值分位数；空列表返回 null（不冒充有数据）。 */
    private static Double percentile(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) {
            return null;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double idx = p * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted.get(lo);
        }
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (idx - lo);
    }

    // ──────────────────────────────────────────────
    //  自评响应率（区分「没看到」与「看到不答」）
    // ──────────────────────────────────────────────

    private Map<String, Object> selfReportStats(LocalDateTime start, LocalDateTime endExclusive) {
        int sampled = 0;
        int answered = 0;
        int skipped = 0;
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT payload FROM wellbeing_record WHERE kind=? AND deleted=0"
                        + " AND create_time>=? AND create_time<?",
                FlowCalibrationService.KIND, start, endExclusive);
        for (Map<String, Object> row : rows) {
            Map<String, Object> payload = decode(String.valueOf(row.get("payload")));
            if (payload == null || !Boolean.TRUE.equals(payload.get("sampled"))) {
                continue;
            }
            sampled++;
            if (Boolean.TRUE.equals(payload.get("answered"))) {
                answered++;
            } else if (Boolean.TRUE.equals(payload.get("skipped"))) {
                skipped++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sampled", sampled);
        out.put("answered", answered);
        out.put("skipped", skipped);
        // 「没看到」= 抽中但未答也未跳过；「看到不答」= skipped
        out.put("noResponse", sampled - answered - skipped);
        out.put("responseRate", sampled > 0 ? (double) answered / (double) sampled : null);
        return out;
    }

    // ──────────────────────────────────────────────
    //  口径变更与不可得清单
    // ──────────────────────────────────────────────

    /** §9.6 三处口径变更（2026-11-05），变更点前后数据不得跨期比较。 */
    private List<Map<String, Object>> definitionChanges() {
        List<Map<String, Object>> changes = new ArrayList<>();
        changes.add(change("2026-11-05", "archived-status",
                "新增归档态（status=3）与 takedown_kind 分层；变更前后的项目状态分布不得直接并线"));
        changes.add(change("2026-11-05", "read-only-exclusion",
                "只读会话进入数据面并同步剔除（§9.1）；变更点之前无「只读」概念，前后占比不得跨期比较"));
        changes.add(change("2026-11-05", "flow-seconds-semantics",
                "flow_seconds 改为「阈值后深度沉浸秒数」（剔除 300s 爬坡段）；存量每达标片段约多记 300s，前后结构性不可比，跨期对比作废重算"));
        return changes;
    }

    private Map<String, Object> change(String date, String key, String note) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("date", date);
        c.put("key", key);
        c.put("note", note);
        return c;
    }

    /**
     * 不可得清单：如实列出，不用 0 填充冒充。
     * 自评相关系数在样本不足时动态加入；其余为当前结构性不可得项（§2.3/§9.5）。
     */
    private List<Map<String, Object>> unavailable(Map<String, Object> calibration) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!"ok".equals(calibration.get("status"))) {
            list.add(unavailableItem("self_report_rho", "自评校准相关系数 ρ",
                    "作答样本 n=" + calibration.get("sampleSize") + "，未达最小样本量 "
                            + calibration.get("minSamples") + "；当前指标名为行为描述，不代表已验证的心流体验"));
        }
        list.add(unavailableItem("export_success_rate", "导出成功率",
                "只有成功导出事件计数，无失败/中断记录，成功率分子分母均无数据源"));
        list.add(unavailableItem("notification_receipt", "下架通知触达回执与回流",
                "通知无触达回执，回流行为无数据源"));
        list.add(unavailableItem("audio_passive_exclusion", "音频被动收听段剔除",
                "音频播放起止标记未落地，分子暂未剔除自动播放段（文献 §7 P2）"));
        list.add(unavailableItem("practice_focus", "正念/短练习专注时长",
                "practice_session 无心跳与事件机制，不在本看板口径内"));
        return list;
    }

    private Map<String, Object> unavailableItem(String key, String label, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("reason", reason);
        return item;
    }

    // ──────────────────────────────────────────────
    //  数据装载与基础工具
    // ──────────────────────────────────────────────

    private String joinIds(List<Map<String, Object>> sessionRows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sessionRows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(((Number) sessionRows.get(i).get("id")).longValue());
        }
        return sb.toString();
    }

    private Map<Long, CraftProject> loadProjects(List<Map<String, Object>> sessionRows) {
        Map<Long, CraftProject> projects = new HashMap<>();
        List<Long> ids = sessionRows.stream()
                .map(r -> r.get("project_id"))
                .filter(java.util.Objects::nonNull)
                .map(v -> ((Number) v).longValue())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return projects;
        }
        String in = ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        // 含逻辑删除行：删除项目的会话历史仍参与判定（按项目最后状态）
        db.queryForList(
                "SELECT id,status,takedown_kind,takedown_at,archive_at,export_until,level"
                        + " FROM craft_project WHERE id IN (" + in + ")")
                .forEach(row -> {
                    CraftProject p = new CraftProject();
                    p.setStatus(row.get("status") == null ? null : ((Number) row.get("status")).intValue());
                    p.setTakedownKind((String) row.get("takedown_kind"));
                    p.setTakedownAt(toLdt(row.get("takedown_at")));
                    p.setArchiveAt(toLdt(row.get("archive_at")));
                    p.setExportUntil(toLdt(row.get("export_until")));
                    p.setLevel(row.get("level") == null ? null : ((Number) row.get("level")).intValue());
                    projects.put(((Number) row.get("id")).longValue(), p);
                });
        return projects;
    }

    private LocalDate parseDate(String raw, LocalDate fallback, String name) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), name + " 日期格式非法（需 YYYY-MM-DD）: " + raw);
        }
    }

    /** H2（Timestamp）与 MySQL（LocalDateTime，Connector/J 8.0.23+）时间类型兼容。 */
    private static LocalDateTime toLdt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(String value) {
        try {
            return json.readValue(crypto.decrypt(value), Map.class);
        } catch (Exception e) {
            log.warn("自评记录解密失败（已跳过）: err={}", e.getMessage());
            return null;
        }
    }

    /** 会话事件聚合中间态。 */
    private static final class EventAgg {
        private long activeSeconds;
        private LocalDateTime lastEventAt;
        private LocalDateTime firstArchiveAt;
        private LocalDateTime firstGateBlockedAt;
    }

    /** 纳入分布的会话级占比记录。 */
    private static final class SessionRatio {
        private long sessionId;
        private long userId;
        private Long projectId;
        private long activeSeconds;
        private long flowSeconds;
        private double ratio;
        private LocalDate weekStart;
        private Integer level;
        private String durationBucket;
        private String takedownKind;
    }

    /** 人×周聚合单元。 */
    private static final class PersonWeek {
        private long userId;
        private LocalDate weekStart;
        private int sessionCount;
        private long activeSeconds;
        private long flowSeconds;
        private double medianRatio;
    }
}
