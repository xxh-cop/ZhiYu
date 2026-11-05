package com.zhiyu.modules.craft.takedown;

import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 项目归档 Worker（2026-11-05 分层下架）：到期把下架项目 2→3 转归档。
 *
 * <ul>
 *   <li>合规下架：导出窗口（export_until）结束 → 归档，archive_at 回填为 export_until。</li>
 *   <li>运营下架：archive_at 到期 → 归档；存量缺 archive_at 的行按
 *       takedown_at + default-deadline-days 推导（与 {@link TakedownStateResolver} 同口径）。</li>
 * </ul>
 *
 * <p>归档后提示语换 craft-archived；读路径（listSteps）对「已过截止但 Worker 未扫到」
 * 的行按同一口径即时推导，不依赖本 Worker 的扫描周期。幂等，可重复执行。
 *
 * <p>2026-11-05 心流度量配套：项目归档使存量进行中/暂停会话转只读，
 * 对每个受影响会话写 {@code session_archive} 结束事件（自评校准挂接点之一）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraftProjectArchiveWorker {

    private final JdbcTemplate jdbc;
    private final TakedownProperties properties;
    private final CraftSessionEventService craftSessionEventService;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void sweep() {
        archiveDueProjects();
    }

    /**
     * 执行一轮到期归档。
     *
     * @return 本轮归档的项目数
     */
    public int archiveDueProjects() {
        LocalDateTime now = LocalDateTime.now();
        // 合规下架（含历史 NULL kind）：窗口结束 → 归档；先取候选 ID 供结束事件定位
        List<Long> complianceIds = jdbc.queryForList(
                "SELECT id FROM craft_project WHERE status=2 AND deleted=0"
                        + " AND (takedown_kind IS NULL OR takedown_kind='compliance')"
                        + " AND export_until IS NOT NULL AND export_until < ?",
                Long.class, now);
        int compliance = jdbc.update(
                "UPDATE craft_project SET status=3, archive_at=export_until, notice_key=?, update_time=?"
                        + " WHERE status=2 AND deleted=0"
                        + " AND (takedown_kind IS NULL OR takedown_kind='compliance')"
                        + " AND export_until IS NOT NULL AND export_until < ?",
                TakedownStateResolver.NOTICE_ARCHIVED, now, now);
        // 运营下架：archive_at 到期 → 归档
        List<Long> operationalIds = jdbc.queryForList(
                "SELECT id FROM craft_project WHERE status=2 AND deleted=0 AND takedown_kind='operational'"
                        + " AND archive_at IS NOT NULL AND archive_at < ?",
                Long.class, now);
        int operational = jdbc.update(
                "UPDATE craft_project SET status=3, notice_key=?, update_time=?"
                        + " WHERE status=2 AND deleted=0 AND takedown_kind='operational'"
                        + " AND archive_at IS NOT NULL AND archive_at < ?",
                TakedownStateResolver.NOTICE_ARCHIVED, now, now);
        // 运营下架存量缺 archive_at：takedown_at + default-deadline-days 推导
        List<Long> legacyIds = archiveLegacyOperational(now);
        int total = compliance + operational + legacyIds.size();
        if (total > 0) {
            log.info("项目归档完成: compliance={}, operational={}, legacyOperational={}",
                    compliance, operational, legacyIds.size());
            List<Long> archived = new ArrayList<>(complianceIds.size() + operationalIds.size() + legacyIds.size());
            if (compliance > 0) {
                archived.addAll(complianceIds);
            }
            if (operational > 0) {
                archived.addAll(operationalIds);
            }
            archived.addAll(legacyIds);
            recordArchiveEvents(archived);
        }
        return total;
    }

    /**
     * 运营下架存量行（archive_at 为空）按推导截止时间归档。
     *
     * @return 本轮实际归档的项目 ID 列表
     */
    private List<Long> archiveLegacyOperational(LocalDateTime now) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, takedown_at FROM craft_project"
                        + " WHERE status=2 AND deleted=0 AND takedown_kind='operational' AND archive_at IS NULL");
        List<Long> archived = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get("takedown_at");
            LocalDateTime base = raw instanceof Timestamp ts ? ts.toLocalDateTime() : null;
            LocalDateTime deadline = (base != null ? base : now)
                    .plusDays(properties.getOperational().getDefaultDeadlineDays());
            if (deadline.isBefore(now)) {
                int updated = jdbc.update(
                        "UPDATE craft_project SET status=3, archive_at=?, notice_key=?, update_time=?"
                                + " WHERE id=? AND status=2 AND deleted=0",
                        deadline, TakedownStateResolver.NOTICE_ARCHIVED, now, row.get("id"));
                if (updated > 0) {
                    archived.add(((Number) row.get("id")).longValue());
                }
            }
        }
        return archived;
    }

    /**
     * 归档转只读的结束事件钩子：对受影响项目的进行中/暂停会话写 session_archive 事件。
     * 观测数据，失败不影响归档主流程。
     */
    private void recordArchiveEvents(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return;
        }
        try {
            String placeholders = String.join(",", Collections.nCopies(projectIds.size(), "?"));
            List<Map<String, Object>> sessions = jdbc.queryForList(
                    "SELECT id, user_id, project_id, status FROM craft_session"
                            + " WHERE deleted=0 AND status IN (1,2) AND project_id IN (" + placeholders + ")",
                    projectIds.toArray());
            for (Map<String, Object> row : sessions) {
                Long sessionId = ((Number) row.get("id")).longValue();
                Long userId = ((Number) row.get("user_id")).longValue();
                Long projectId = ((Number) row.get("project_id")).longValue();
                int sessionStatus = ((Number) row.get("status")).intValue();
                craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                        CraftSessionEventType.SESSION_ARCHIVE, null,
                        Map.of("sessionStatus", sessionStatus, "reason", "project_archived"), null);
            }
        } catch (Exception e) {
            log.warn("归档结束事件写入失败（已忽略）: err={}", e.getMessage());
        }
    }
}
