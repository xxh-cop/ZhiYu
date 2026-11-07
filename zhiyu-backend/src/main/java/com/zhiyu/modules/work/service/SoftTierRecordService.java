package com.zhiyu.modules.work.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.ai.safety.HighRiskLexicon;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 软档处置链·写池服务（2026-11-07，设计 docs/moderation-soft-tier-disposition-chain-20261107.md）。
 *
 * <p><b>职责</b>：审核员「通过并标记软档」时落台账（moderation_soft_review）+ 同步抽样决定 +
 * 写热路径降权列（work/work_comment.soft_demote_until）+ 降权通知；以及作者申诉入口。
 * 复核队列读取与裁决在 {@code SoftTierPoolService}（admin 模块），刻意分开避免
 * AdminWorkService ↔ 本服务的循环依赖。
 *
 * <p><b>开关</b>：{@code zhiyu.moderation.soft-tier.disposition.enabled} 默认 false——
 * 关闭时 {@link #record} 拒绝写入（1001），读侧（社区流/评论列表）忽略 soft_demote_until 列。
 *
 * <p><b>抽样分层（§1.2，实证逃逸形态全覆盖，不按风险均匀抽）</b>：
 * mandatory_lexicon（服务端词库命中，可信信号）→ mandatory_riskpoint（管理端回显 AI 建议
 * riskPoints 非空；<b>注意</b>：该信号来自审核员客户端回显，服务端无法独立核验——ai_audit_log
 * 原文加密且无读通道；role=3 操作且全程审计留痕，误用最坏后果是分层错位而非放行，接受此边界）
 * → mandatory_author_first（作者当日首条，服务端 DB 计数）→ random（均匀随机，sample-rate）。
 * mandatory 层不受 daily-cap 约束（安全优先）；random/author_first 层溢出转未抽中（overflow_*），
 * 保留降权窗口，次日队列优先。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftTierRecordService {

    private final JdbcTemplate db;
    private final HighRiskLexicon lexicon;
    private final NotificationService notificationService;
    private final com.zhiyu.ai.safety.ContentSafetyFilter contentSafetyFilter;

    @Value("${zhiyu.moderation.soft-tier.disposition.enabled:false}")
    private boolean enabled;
    @Value("${zhiyu.moderation.soft-tier.disposition.unsampled-demote-hours:72}")
    private int unsampledDemoteHours;
    @Value("${zhiyu.moderation.soft-tier.disposition.sla-hours:24}")
    private int slaHours;
    @Value("${zhiyu.moderation.soft-tier.disposition.sample-rate:0.33}")
    private double sampleRate;
    @Value("${zhiyu.moderation.soft-tier.disposition.daily-cap:50}")
    private int dailyCap;

    public boolean enabled() {
        return enabled;
    }

    /** 抽中者降权硬上界（天）：实际远短于此（SLA 升级机制兜底），仅防字段语义分叉。 */
    private static final int SAMPLED_DEMOTE_DAYS_CAP = 30;

    /**
     * 软档标记落台账（幂等：uk_soft_target 兜底，重复调用返回既有台账 id）。
     *
     * @param targetType         work / comment
     * @param targetId           对象 id
     * @param authorId           作者 id
     * @param contentText        词库匹配用文本（作品=标题+寄语，评论=正文）
     * @param narrative          管理端回显的窄门 narrative 值（可空，仅留证）
     * @param riskPointsNonEmpty 管理端回显的 AI 建议 riskPoints 是否非空（抽样信号，见类注释边界说明）
     * @param displayTitle       通知展示用标题（评论传 null）
     * @return 台账 id
     */
    @Transactional
    public long record(String targetType, long targetId, long authorId, String contentText,
                       String narrative, boolean riskPointsNonEmpty, String displayTitle) {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        if (!"work".equals(targetType) && !"comment".equals(targetType)) {
            throw new BusinessException(1001, "不支持的软档对象类型");
        }
        Long existing = queryOne("SELECT id FROM moderation_soft_review WHERE target_type=? AND target_id=? AND deleted=0",
                Long.class, targetType, targetId);
        if (existing != null) {
            log.info("软档台账已存在（幂等返回）: targetType={}, targetId={}, reviewId={}", targetType, targetId, existing);
            return existing;
        }

        // 关联最近一次 moderation 场景的 PASS 审计（建议证据链；原文加密存于 ai_audit_log，不重复存）
        Long auditLogId = queryOne(
                "SELECT id FROM ai_audit_log WHERE scene='moderation' AND ref_type=? AND ref_id=? AND action='PASS' ORDER BY id DESC LIMIT 1",
                Long.class, targetType, targetId);

        String layer = decideLayer(authorId, contentText, riskPointsNonEmpty);
        boolean sampled = layer.startsWith("mandatory") || "random".equals(layer);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime demoteUntil = sampled
                ? now.plusDays(SAMPLED_DEMOTE_DAYS_CAP)
                : now.plusHours(unsampledDemoteHours);
        LocalDateTime slaDueAt = sampled ? now.plusHours(slaHours) : null;

        long id = IdWorker.getId();
        db.update("""
                INSERT INTO moderation_soft_review
                (id,target_type,target_id,author_id,ai_tendency,narrative,audit_log_id,sampled,sample_layer,pool_status,
                 sla_due_at,demote_until,appeal_status,notified,create_time,update_time,deleted)
                VALUES(?,?,?,?,'SOFT_CONCERN',?,?,?,?,?,?,?, 'NONE',0,?,?,0)
                """, id, targetType, targetId, authorId, narrative, auditLogId, sampled ? 1 : 0, layer,
                sampled ? "PENDING" : "NOT_SAMPLED",
                slaDueAt == null ? null : Timestamp.valueOf(slaDueAt), Timestamp.valueOf(demoteUntil),
                Timestamp.valueOf(now), Timestamp.valueOf(now));

        // 热路径降权列（台账为事实源，本列是读优化冗余，同事务写入）
        if ("work".equals(targetType)) {
            db.update("UPDATE work SET soft_demote_until=? WHERE id=? AND deleted=0", Timestamp.valueOf(demoteUntil), targetId);
        } else {
            db.update("UPDATE work_comment SET soft_demote_until=? WHERE id=? AND deleted=0", Timestamp.valueOf(demoteUntil), targetId);
        }

        // 降权通知（失败不阻断审核主流程；notified 列保证可补发核对）
        try {
            // 评论通知的跳转 ref 是所属作品（与 COMMENT_AUDIT 约定一致）
            Long workIdForNotify = "comment".equals(targetType)
                    ? queryOne("SELECT work_id FROM work_comment WHERE id=?", Long.class, targetId) : null;
            boolean pushed = notificationService.notifySoftTierDemote(authorId, targetType, targetId,
                    workIdForNotify, displayTitle, sampled, unsampledDemoteHours);
            if (pushed) {
                db.update("UPDATE moderation_soft_review SET notified=1 WHERE id=?", id);
            }
        } catch (Exception e) {
            log.warn("软档降权通知写入失败（不阻断主流程）: reviewId={}, err={}", id, e.getMessage());
        }
        log.info("软档标记落台账: reviewId={}, targetType={}, targetId={}, layer={}, sampled={}, demoteUntil={}",
                id, targetType, targetId, layer, sampled, demoteUntil);
        return id;
    }

    /**
     * 抽样分层决定（顺序即优先级）。返回值即 sample_layer 落库值。
     */
    private String decideLayer(long authorId, String contentText, boolean riskPointsNonEmpty) {
        if (lexicon.containsRisk(contentText)) {
            return "mandatory_lexicon";
        }
        if (riskPointsNonEmpty) {
            return "mandatory_riskpoint";
        }
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long authorToday = db.queryForObject(
                "SELECT COUNT(*) FROM moderation_soft_review WHERE author_id=? AND deleted=0 AND create_time>=?",
                Long.class, authorId, Timestamp.valueOf(dayStart));
        boolean capped = isCapped(dayStart);
        if (authorToday != null && authorToday == 0) {
            return capped ? "overflow_author_first" : "mandatory_author_first";
        }
        if (ThreadLocalRandom.current().nextDouble() < sampleRate) {
            return capped ? "overflow_random" : "random";
        }
        return "random";
    }

    /** 日入池是否已达软上限（只约束 random / mandatory_author_first 层）。 */
    private boolean isCapped(LocalDateTime dayStart) {
        Long sampledToday = db.queryForObject(
                "SELECT COUNT(*) FROM moderation_soft_review WHERE sampled=1 AND deleted=0 AND create_time>=?",
                Long.class, Timestamp.valueOf(dayStart));
        return sampledToday != null && sampledToday >= dailyCap;
    }

    /**
     * 作者申诉（逃生通道，设计 §2.3）：申诉即升为人工必审（等同抽中），SLA 重新起算。
     * 幂等：已 FILED 直接成功；复核已终裁（CLEAR/UPHOLD/BLOCK）→ 3001。
     *
     * @param reason 申诉理由（必填，≤200 字）：过既有输入侧内容安全过滤（scene=soft-appeal，
     *               命中高危词拒绝并触发关怀流程，过滤本身写 ai_audit_log），落库 appeal_reason。
     */
    @Transactional
    public void appeal(long uid, String targetType, long targetId, String reason) {
        if (!enabled) {
            throw new BusinessException(1001, "软档处置链未开启");
        }
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new BusinessException(1001, "请填写不超过 200 字的申诉理由");
        }
        if (contentSafetyFilter.checkInput(uid, null, "soft-appeal", reason).getAction()
                == com.zhiyu.ai.safety.SafetyAction.BLOCK) {
            throw new BusinessException(1001, "申诉理由暂不适合提交，请调整后重试");
        }
        // 本人数据校验（不泄露存在性之外的归属信息）
        Long owner;
        if ("work".equals(targetType)) {
            owner = queryOne("SELECT user_id FROM work WHERE id=? AND deleted=0", Long.class, targetId);
        } else if ("comment".equals(targetType)) {
            owner = queryOne("SELECT user_id FROM work_comment WHERE id=? AND deleted=0", Long.class, targetId);
        } else {
            throw new BusinessException(1001, "不支持的对象类型");
        }
        if (owner == null) {
            throw new BusinessException(1004, "内容不存在");
        }
        if (owner != uid) {
            throw new BusinessException(1003, "只能对自己的内容申诉");
        }
        var rows = db.queryForList(
                "SELECT id,pool_status,appeal_status,sampled FROM moderation_soft_review WHERE target_type=? AND target_id=? AND deleted=0 FOR UPDATE",
                targetType, targetId);
        if (rows.isEmpty()) {
            throw new BusinessException(1004, "该内容没有进行中的软档处置");
        }
        Map<String, Object> row = rows.get(0);
        long reviewId = ((Number) row.get("id")).longValue();
        String poolStatus = String.valueOf(row.get("pool_status"));
        if ("CLEAR".equals(poolStatus) || "UPHOLD".equals(poolStatus) || "BLOCK".equals(poolStatus)
                || "EXPIRED".equals(poolStatus)) {
            throw new BusinessException(3001, "该内容的复核已结束");
        }
        if ("FILED".equals(String.valueOf(row.get("appeal_status")))) {
            return; // 幂等：重复申诉不重复升级
        }
        LocalDateTime slaDue = LocalDateTime.now().plusHours(slaHours);
        LocalDateTime demoteCap = LocalDateTime.now().plusDays(SAMPLED_DEMOTE_DAYS_CAP);
        db.update("""
                UPDATE moderation_soft_review
                SET appeal_status='FILED', appeal_reason=?, sampled=1, pool_status='ESCALATED', sla_due_at=?, demote_until=?, update_time=CURRENT_TIMESTAMP
                WHERE id=?
                """, reason, Timestamp.valueOf(slaDue), Timestamp.valueOf(demoteCap), reviewId);
        if ("work".equals(targetType)) {
            db.update("UPDATE work SET soft_demote_until=? WHERE id=?", Timestamp.valueOf(demoteCap), targetId);
        } else {
            db.update("UPDATE work_comment SET soft_demote_until=? WHERE id=?", Timestamp.valueOf(demoteCap), targetId);
        }
        notifyAdmins(reviewId, "appeal", "有新的软档申诉待复核",
                "有作者对「展示受限」处置提出申诉，已升级为人工必审，请尽快复核。");
        log.info("软档申诉升级人工必审: reviewId={}, targetType={}, targetId={}, appellant={}", reviewId, targetType, targetId, uid);
    }

    /** 通知全部 role=3 管理员（复用通知中心；dedupe 按事件+日期窗口防刷屏）。供本服务与 SoftTierPoolService 共用。 */
    public void notifyAdmins(long reviewId, String event, String title, String content) {
        try {
            List<Long> admins = db.queryForList(
                    "SELECT id FROM user WHERE role=3 AND deleted=0 LIMIT 50", Long.class);
            String day = java.time.LocalDate.now().toString();
            for (Long adminId : admins) {
                notificationService.notifySoftTierAdmin(adminId, reviewId, title, content,
                        "soft-review:" + reviewId + ":" + event + ":" + day);
            }
        } catch (Exception e) {
            log.warn("软档管理员通知写入失败（不阻断主流程）: reviewId={}, event={}, err={}", reviewId, event, e.getMessage());
        }
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        var list = db.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return list.isEmpty() ? null : list.get(0);
    }
}
