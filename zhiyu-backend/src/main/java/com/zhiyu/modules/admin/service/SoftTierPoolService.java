package com.zhiyu.modules.admin.service;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.notification.service.NotificationService;
import com.zhiyu.modules.work.service.CommentModerationService;
import com.zhiyu.modules.work.service.SoftTierRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 软档处置链·抽检池服务（2026-11-07，设计 docs/moderation-soft-tier-disposition-chain-20261107.md）。
 *
 * <p><b>职责</b>：复核队列读取（PENDING/ESCALATED 优先、sla_due_at 升序）、裁决（CLEAR/UPHOLD/BLOCK）、
 * SLA 逾期扫描（24h 升级 ESCALATED 通知管理员；48h 起每日提醒，保守默认：维持降权不自动恢复）。
 * 写池（台账+抽样）在 {@link SoftTierRecordService}，刻意分开避免与 AdminWorkService 循环依赖。
 *
 * <p><b>裁决改状态复用既有入口</b>：BLOCK 走 {@link AdminWorkService#updateWorkStatus} /
 * {@link CommentModerationService#review}（状态机校验、理由必填、审核结果通知全部继承，
 * 处置链不造第二条写状态路径）；CLEAR/UPHOLD 只清软档标记与降权列，不动内容状态。
 *
 * <p>权限：仅 role=3（控制器所在 /api/admin/** 兜底 + 本类不读咨询师脱敏口径——池内含原文，role=2 不进池）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftTierPoolService {

    private static final Set<String> ACTIONS = Set.of("CLEAR", "UPHOLD", "BLOCK");

    private final JdbcTemplate db;
    private final NotificationService notificationService;
    private final AdminWorkService adminWorkService;
    private final CommentModerationService commentModerationService;
    private final SoftTierRecordService recordService;
    private final com.zhiyu.modules.file.MediaService mediaService;

    @Value("${zhiyu.moderation.soft-tier.disposition.enabled:false}")
    private boolean enabled;
    @Value("${zhiyu.moderation.soft-tier.disposition.sla-hours:24}")
    private int slaHours;
    @Value("${zhiyu.moderation.soft-tier.disposition.unsampled-demote-hours:72}")
    private int unsampledDemoteHours;

    /** 复核队列分页（默认只看待办：PENDING + ESCALATED，ESCALATED 在前、按 sla_due_at 升序）。 */
    public Map<String, Object> list(int page, String status) {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        if (page < 1 || page > 100000) {
            throw new BusinessException(1001, "分页参数无效");
        }
        String where;
        if (status == null || status.isBlank() || "OPEN".equals(status)) {
            where = "r.pool_status IN ('PENDING','ESCALATED')";
        } else if ("APPEAL".equals(status)) {
            // 按申诉过滤（与 /api/admin/soft-tier-appeals 同口径）
            where = "r.appeal_status = 'FILED'";
        } else if (Set.of("PENDING", "ESCALATED", "CLEAR", "UPHOLD", "BLOCK", "NOT_SAMPLED").contains(status)) {
            where = "r.pool_status = '" + status + "'";
        } else {
            throw new BusinessException(1001, "状态筛选无效");
        }
        int size = 20;
        // 雪花字段在 Java 侧转字符串（不 CAST AS CHAR——H2 会截断为 CHAR(1)，MySQL/H2 行为必须一致）
        var rows = db.queryForList("""
                SELECT r.id, r.target_type targetType, r.target_id targetId, r.author_id authorId,
                       r.sample_layer sampleLayer, r.pool_status poolStatus, r.appeal_status appealStatus,
                       r.sla_due_at slaDueAt, r.demote_until demoteUntil, r.create_time createTime
                FROM moderation_soft_review r
                WHERE""" + " " + where + " AND r.deleted=0 ORDER BY CASE r.pool_status WHEN 'ESCALATED' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END, CASE WHEN r.sla_due_at IS NULL THEN 1 ELSE 0 END, r.sla_due_at ASC, r.id ASC LIMIT" + " " + size + " OFFSET " + (page - 1) * size);
        stringifyIds(rows, "id", "targetId", "authorId");
        Long total = db.queryForObject("SELECT COUNT(*) FROM moderation_soft_review r WHERE " + where + " AND r.deleted=0", Long.class);
        Map<String, Object> out = new HashMap<>();
        out.put("records", rows);
        out.put("total", total == null ? 0 : total.intValue());
        out.put("page", page);
        out.put("size", size);
        return out;
    }

    /**
     * 申诉单分页列表（管理端单独拉取，不在大队列里捞）：appeal_status=FILED，按更新时间倒序。
     * 含 appealReason 与作者昵称（脱敏口径同既有管理端评论详情）。
     */
    public Map<String, Object> appeals(int page) {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        if (page < 1 || page > 100000) {
            throw new BusinessException(1001, "分页参数无效");
        }
        int size = 20;
        var rows = db.queryForList("""
                SELECT r.id, r.target_type targetType, r.target_id targetId, r.author_id authorId,
                       COALESCE(u.nickname,'已注销用户') authorNickname,
                       r.appeal_reason appealReason, r.sample_layer sampleLayer, r.pool_status poolStatus,
                       r.sla_due_at slaDueAt, r.demote_until demoteUntil, r.create_time createTime, r.update_time appealFiledAt
                FROM moderation_soft_review r LEFT JOIN user u ON u.id = r.author_id AND u.deleted = 0
                WHERE r.appeal_status = 'FILED' AND r.deleted = 0
                ORDER BY r.update_time DESC, r.id DESC LIMIT""" + " " + size + " OFFSET " + (page - 1) * size);
        stringifyIds(rows, "id", "targetId", "authorId");
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM moderation_soft_review WHERE appeal_status='FILED' AND deleted=0", Long.class);
        Map<String, Object> out = new HashMap<>();
        out.put("records", rows);
        out.put("total", total == null ? 0 : total.intValue());
        out.put("page", page);
        out.put("size", size);
        return out;
    }

    /**
     * 看板聚合（一次返回，不给前端留「—」和估算）：
     * todaySampled 当日入池量 / todayRecorded 当日落档量 / pending+escalated 待复核量 /
     * overdue 超时数（未终裁且 sla_due_at 已过）/ appeals 申诉数（FILED）/ verdicts 裁决分布（全量累计）。
     */
    public Map<String, Object> stats() {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        Timestamp dayStart = Timestamp.valueOf(LocalDateTime.now().toLocalDate().atStartOfDay());
        Map<String, Object> out = new HashMap<>();
        out.put("todaySampled", count("SELECT COUNT(*) FROM moderation_soft_review WHERE sampled=1 AND deleted=0 AND create_time>=?", dayStart));
        out.put("todayRecorded", count("SELECT COUNT(*) FROM moderation_soft_review WHERE deleted=0 AND create_time>=?", dayStart));
        out.put("pending", count("SELECT COUNT(*) FROM moderation_soft_review WHERE pool_status='PENDING' AND deleted=0"));
        out.put("escalated", count("SELECT COUNT(*) FROM moderation_soft_review WHERE pool_status='ESCALATED' AND deleted=0"));
        out.put("overdue", count("SELECT COUNT(*) FROM moderation_soft_review WHERE pool_status IN ('PENDING','ESCALATED') AND sla_due_at IS NOT NULL AND sla_due_at < CURRENT_TIMESTAMP AND deleted=0"));
        out.put("appeals", count("SELECT COUNT(*) FROM moderation_soft_review WHERE appeal_status='FILED' AND deleted=0"));
        Map<String, Object> verdicts = new HashMap<>();
        for (String action : ACTIONS) {
            verdicts.put(action, count("SELECT COUNT(*) FROM moderation_soft_review WHERE pool_status='" + action + "' AND deleted=0"));
        }
        out.put("verdicts", verdicts);
        return out;
    }

    private int count(String sql, Object... args) {
        Long n = db.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n.intValue();
    }

    private static void stringifyIds(java.util.List<Map<String, Object>> rows, String... keys) {
        for (var row : rows) {
            for (String key : keys) {
                Object v = row.get(key);
                if (v != null) {
                    row.put(key, String.valueOf(v));
                }
            }
        }
    }

    /** 复核详情（含内容原文节选与 AI 审计关联；池内含原文，仅 role=3）。 */
    public Map<String, Object> detail(long id, Long viewerUid) {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        var rows = db.queryForList(
                "SELECT r.* FROM moderation_soft_review r WHERE r.id=? AND r.deleted=0", id);
        if (rows.isEmpty()) {
            throw new BusinessException(1004, "复核单不存在");
        }
        // 直接沿用 JdbcTemplate 返回的 LinkedCaseInsensitiveMap（H2 列标签大小写不定，
        // 拷成 HashMap 会让 coverUrl 这类混合大小写别名取空——教训，勿复制）
        Map<String, Object> row = rows.get(0);
        String targetType = String.valueOf(row.get("target_type"));
        long targetId = ((Number) row.get("target_id")).longValue();
        long authorId = ((Number) row.get("author_id")).longValue();
        // 雪花字段统一按字符串出参（不 CAST AS CHAR——H2 截断为 CHAR(1)，两库行为必须一致）
        for (String key : new String[]{"id", "target_id", "author_id", "audit_log_id"}) {
            if (row.get(key) != null) {
                row.put(key, String.valueOf(row.get(key)));
            }
        }
        // 作者昵称（脱敏口径与既有管理端评论详情一致：昵称或「已注销用户」），免前端二次请求；
        // 作者行缺失（如已物理清理）时降级「已注销用户」，详情接口不应因此 500
        var nick = db.queryForList(
                "SELECT COALESCE(nickname,'已注销用户') FROM user WHERE id=? AND deleted=0", String.class, authorId);
        row.put("authorNickname", nick.isEmpty() ? "已注销用户" : nick.get(0));
        if ("work".equals(targetType)) {
            var w = db.queryForList("SELECT title, message, status, media_type mediaType, cover_url coverUrl, video_url videoUrl, soft_demote_until FROM work WHERE id=? AND deleted=0", targetId);
            if (!w.isEmpty()) {
                Map<String, Object> work = w.get(0);
                // 封面经既有私有媒体授权读取路径：1h 签名临时地址 + 每次请求重新鉴权（role=3 在授权范围）；
                // 失效引用降级 null（绝不下发可长期访问的私有媒体 URL，也不新造绕过鉴权的读通道）
                work.put("coverUrl", mediaService.playbackIfMediaRef(viewerUid, (String) work.get("coverUrl")));
                row.put("work", work);
            } else {
                row.put("work", null);
            }
        } else {
            var c = db.queryForList("SELECT content, status, work_id workId, soft_demote_until FROM work_comment WHERE id=? AND deleted=0", targetId);
            row.put("comment", c.isEmpty() ? null : c.get(0));
        }
        return row;
    }

    /**
     * 裁决。BLOCK 理由必填（复用既有驳回约束）；终态幂等（已终裁 → 3001）。
     * 申诉单裁决后 appeal_status=RESOLVED。
     */
    @Transactional
    public void verdict(long id, long reviewerId, String action, String reason) {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        if (!ACTIONS.contains(action)) {
            throw new BusinessException(1001, "无效的裁决动作");
        }
        if ("BLOCK".equals(action) && (reason == null || reason.isBlank())) {
            throw new BusinessException(1001, "改判拦截必须填写理由");
        }
        var rows = db.queryForList(
                "SELECT * FROM moderation_soft_review WHERE id=? AND deleted=0 FOR UPDATE", id);
        if (rows.isEmpty()) {
            throw new BusinessException(1004, "复核单不存在");
        }
        Map<String, Object> row = rows.get(0);
        String poolStatus = String.valueOf(row.get("pool_status"));
        if (!"PENDING".equals(poolStatus) && !"ESCALATED".equals(poolStatus)) {
            throw new BusinessException(3001, "该复核单已终裁，请刷新后查看");
        }
        String targetType = String.valueOf(row.get("target_type"));
        long targetId = ((Number) row.get("target_id")).longValue();
        long authorId = ((Number) row.get("author_id")).longValue();

        if ("BLOCK".equals(action)) {
            // 复用既有状态机入口（含审核结果通知），失败（如状态已变化）以 3001 透出
            if ("work".equals(targetType)) {
                adminWorkService.updateWorkStatus(targetId, 2, reason);
            } else {
                commentModerationService.review(targetId, 2, 1, reason);
            }
        }

        LocalDateTime demoteUntil = switch (action) {
            case "CLEAR", "BLOCK" -> null;               // CLEAR 恢复曝光；BLOCK 转下架，降权列失去意义
            default -> {                                  // UPHOLD：维持至原 72h 窗口结束
                // 时间列统一走 JdbcTimes（MySQL 返回 LocalDateTime、H2 返回 Timestamp，裸强转在 MySQL 下 CCE——联调 F1-b）
                LocalDateTime created = com.zhiyu.common.util.JdbcTimes.toLocalDateTime(row.get("create_time"));
                yield created.plusHours(unsampledDemoteHours);
            }
        };
        db.update("""
                UPDATE moderation_soft_review
                SET pool_status=?, reviewer_id=?, reason=?, reviewed_at=CURRENT_TIMESTAMP, demote_until=?,
                    appeal_status=CASE WHEN appeal_status='FILED' THEN 'RESOLVED' ELSE appeal_status END, update_time=CURRENT_TIMESTAMP
                WHERE id=?
                """, action, reviewerId, reason, demoteUntil == null ? null : Timestamp.valueOf(demoteUntil), id);
        String hotTable = "work".equals(targetType) ? "work" : "work_comment";
        db.update("UPDATE " + hotTable + " SET soft_demote_until=? WHERE id=?",
                demoteUntil == null ? null : Timestamp.valueOf(demoteUntil), targetId);

        if ("CLEAR".equals(action)) {
            try {
                Long workIdForNotify = "comment".equals(targetType)
                        ? db.queryForObject("SELECT work_id FROM work_comment WHERE id=?", Long.class, targetId) : null;
                notificationService.notifySoftTierRestore(authorId, targetType, targetId, workIdForNotify);
            } catch (Exception e) {
                log.warn("软档恢复通知写入失败（不阻断裁决）: reviewId={}, err={}", id, e.getMessage());
            }
        }
        log.info("软档复核裁决: reviewId={}, action={}, targetType={}, targetId={}, reviewer={}",
                id, action, targetType, targetId, reviewerId);
    }

    /**
     * SLA 逾期扫描（每分钟，FileCleanupWorker 已全局 EnableScheduling）。
     * 保守默认行为：超时只升级与通知，<b>不自动恢复、不自动下架</b>（设计 §3 选边）。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void scanSlaBreach() {
        if (!enabled) {
            return;
        }
        try {
            // 24h 逾期：逐条升级并立即通知管理员（量小，逐条换精确事件）
            var due = db.queryForList("""
                    SELECT id FROM moderation_soft_review
                    WHERE pool_status='PENDING' AND sla_due_at IS NOT NULL AND sla_due_at < CURRENT_TIMESTAMP AND deleted=0 LIMIT 100
                    """);
            for (var row : due) {
                long reviewId = ((Number) row.get("id")).longValue();
                int changed = db.update("""
                        UPDATE moderation_soft_review
                        SET pool_status='ESCALATED', update_time=CURRENT_TIMESTAMP
                        WHERE id=? AND pool_status='PENDING'
                        """, reviewId);
                if (changed == 1) {
                    log.warn("软档复核 SLA 逾期升级: reviewId={} PENDING → ESCALATED", reviewId);
                    recordService.notifyAdmins(reviewId, "sla-escalate", "软档复核已超时升级",
                            "有软档复核单超过 24 小时未裁决，已升级为必审置顶；默认维持展示受限，请尽快人工裁决。");
                }
            }
            // 逾期 2×sla-hours（默认 48h）仍未裁决：每日提醒一次（通知 dedupe 按日窗口幂等）
            var overdue = db.queryForList("""
                    SELECT id FROM moderation_soft_review
                    WHERE pool_status='ESCALATED' AND sla_due_at < ? AND deleted=0 LIMIT 100
                    """, Timestamp.valueOf(LocalDateTime.now().minusHours(2L * slaHours)));
            for (var row : overdue) {
                long reviewId = ((Number) row.get("id")).longValue();
                recordService.notifyAdmins(reviewId, "sla-breach", "软档复核已逾期待处理",
                        "有软档复核单已超过处置时限，默认维持展示受限，请尽快人工裁决。");
            }
        } catch (Exception e) {
            log.error("软档 SLA 扫描失败（下个周期重试）: {}", e.getMessage());
        }
    }
}
