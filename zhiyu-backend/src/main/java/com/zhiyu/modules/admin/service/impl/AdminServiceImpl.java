package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.dto.*;
import com.zhiyu.modules.admin.service.AdminService;
import com.zhiyu.modules.audit.entity.CareReminder;
import com.zhiyu.modules.audit.mapper.CareReminderMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.emotion.entity.EmotionSession;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端服务实现（M2 批 4）。
 *
 * <p>复用现有 Mapper（CraftProjectMapper / CareReminderMapper / UserMapper /
 * WorkMapper / EmotionSessionMapper），不新建 Mapper。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final int PAGE_SIZE = 10;

    private final CraftProjectMapper craftProjectMapper;
    private final CareReminderMapper careReminderMapper;
    private final UserMapper userMapper;
    private final WorkMapper workMapper;
    private final EmotionSessionMapper emotionSessionMapper;
    private final JdbcTemplate jdbc;
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;
    private final com.zhiyu.modules.care.OperationLog operationLog;
    private final com.zhiyu.modules.file.MediaService mediaService;
    private final com.zhiyu.modules.craft.takedown.TakedownProperties takedownProperties;

    @Override
    public PageVO<AuditQueueItemVO> getAuditQueue(String type, Integer page) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        // 2026-11-04（I2）：work / comment 队列（此前只对 project 返回数据，其余返回空）
        if ("work".equals(type)) {
            return workQueue(pageNum);
        }
        if ("comment".equals(type)) {
            return commentQueue(pageNum);
        }
        if (!"project".equals(type)) {
            return new PageVO<>(0, 1, PAGE_SIZE, Collections.emptyList());
        }

        Page<CraftProject> pageParam = new Page<>(pageNum, PAGE_SIZE);
        LambdaQueryWrapper<CraftProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftProject::getStatus, 0)
                .orderByDesc(CraftProject::getCreateTime);

        IPage<CraftProject> result = craftProjectMapper.selectPage(pageParam, wrapper);

        List<AuditQueueItemVO> records = result.getRecords().stream()
                .map(this::toAuditQueueItemVO)
                .collect(Collectors.toList());
        return new PageVO<>(result.getTotal(), pageNum, PAGE_SIZE, records);
    }

    /**
     * 待审作品队列（work.status=0）。每项附对象级 AI 审计摘要（脱敏）。
     */
    private PageVO<AuditQueueItemVO> workQueue(int pageNum) {
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work WHERE status=0 AND deleted=0", Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,title,user_id,media_type,create_time FROM work "
                        + "WHERE status=0 AND deleted=0 ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",
                PAGE_SIZE, (pageNum - 1) * PAGE_SIZE);
        List<AuditQueueItemVO> records = rows.stream().map(row -> {
            AuditQueueItemVO vo = new AuditQueueItemVO();
            vo.setType("work");
            vo.setId(((Number) row.get("id")).longValue());
            vo.setTitle((String) row.get("title"));
            vo.setAuthorId(((Number) row.get("user_id")).longValue());
            vo.setRefId(((Number) row.get("id")).longValue());
            vo.setExcerpt("媒体类型：" + row.get("media_type"));
            vo.setCreateTime(toLdt(row.get("create_time")));
            fillAiAudits(vo, "work", vo.getRefId());
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(total, pageNum, PAGE_SIZE, records);
    }

    /**
     * 待审评论队列（work_comment.status=0）。每项附对象级 AI 审计摘要（脱敏）。
     */
    private PageVO<AuditQueueItemVO> commentQueue(int pageNum) {
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_comment WHERE status=0 AND deleted=0", Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.id,c.content,c.user_id,c.work_id,c.create_time,w.title workTitle "
                        + "FROM work_comment c LEFT JOIN work w ON w.id=c.work_id "
                        + "WHERE c.deleted=0 AND c.status=0 ORDER BY c.create_time DESC,c.id DESC LIMIT ? OFFSET ?",
                PAGE_SIZE, (pageNum - 1) * PAGE_SIZE);
        List<AuditQueueItemVO> records = rows.stream().map(row -> {
            AuditQueueItemVO vo = new AuditQueueItemVO();
            vo.setType("comment");
            vo.setId(((Number) row.get("id")).longValue());
            vo.setTitle("评论·" + row.get("workTitle"));
            vo.setAuthorId(((Number) row.get("user_id")).longValue());
            vo.setRefId(((Number) row.get("work_id")).longValue());
            vo.setExcerpt((String) row.get("content"));
            vo.setCreateTime(toLdt(row.get("create_time")));
            fillAiAudits(vo, "comment", vo.getId());
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(total, pageNum, PAGE_SIZE, records);
    }

    /**
     * 对象级 AI 审计摘要聚合（I2，2026-11-04）。
     *
     * <p><b>关联口径（不伪造）</b>：只按 {@code ref_type + ref_id} 精确匹配
     * ai_audit_log；2026-11-04 前的历史审计没有 ref 列（一律为 NULL），
     * 因此当前真实数据下返回空数组并写 {@code auditHint} 说明，
     * <b>不做</b>「按 user_id + 时间窗」的猜测式关联（会把别人的审计张冠李戴）。
     * 摘要只返回脱敏字段（scene/action/reason/createTime），不返回加密的原文列。
     */
    private void fillAiAudits(AuditQueueItemVO vo, String refType, Long refId) {
        List<Map<String, Object>> audits = jdbc.queryForList(
                "SELECT scene,action,reason,create_time FROM ai_audit_log "
                        + "WHERE ref_type=? AND ref_id=? ORDER BY create_time DESC,id DESC LIMIT 5",
                refType, refId);
        vo.setAiAudits(audits);
        if (audits.isEmpty()) {
            vo.setAuditHint("该对象暂无对象级 AI 审计记录（2026-11-04 前的历史审计未记录对象关联，"
                    + "不代表未做安全过滤）；自该日期起的新审计将携带对象关联。");
        }
    }

    private static LocalDateTime toLdt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt;
        }
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }

    @Override
    public AuditQueueItemVO auditProject(Long projectId, AuditRequest req) {
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (project.getStatus() != null && project.getStatus() != 0) {
            throw new BusinessException(ResultCode.BAD_STATE);
        }

        String action = req.getAction();
        if ("approve".equals(action)) {
            project.setStatus(1);
        } else if ("reject".equals(action)) {
            if (req.getReason() == null || req.getReason().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "驳回必须填写理由");
            }
            project.setStatus(2);
            // AY1：驳回理由落库（与下架/删除同列；此前校验非空后丢弃）
            project.setAuditReason(req.getReason().trim());
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "action 只能为 approve 或 reject");
        }

        craftProjectMapper.updateById(project);
        log.info("项目审核完成: projectId={}, action={}, operator={}",
                projectId, action, UserContext.getUserId());
        // BC1（2026-11-05）：审核动作落操作日志，使「评审驳回 vs 事后下架」可由日志推导（问责闭环）
        operationLog.record("project." + ("approve".equals(action) ? "approve" : "reject"), projectId);
        // BE（2026-11-05）：审核结果触达传承人（通过/驳回都通知，驳回带理由）；失败不阻断审核主流程
        try {
            notificationService.notifyProjectAudit(project.getInheritorId(), projectId, project.getTitle(),
                    "approve".equals(action), project.getAuditReason());
        } catch (Exception e) {
            log.warn("项目审核结果通知写入失败（不阻断审核）: projectId={}, err={}", projectId, e.getMessage());
        }

        return toAuditQueueItemVO(project);
    }

    /**
     * BC1（2026-11-05）：项目问责详情。auditReason 原样下发（管理端可见，学生端不下发）；
     * 动作来源推导自 admin_operation_log 该项目最近一条 project.* 记录——
     * 下架能力 2026-11-05 才存在且必落日志，故无日志的历史 status=2 必为评审驳回，推导无猜测。
     */
    @Override
    public Map<String, Object> projectDetail(Long projectId) {
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        var logs = jdbc.queryForList(
                "SELECT action,actor_id,create_time FROM admin_operation_log"
                        + " WHERE target_id=? AND action IN ('project.approve','project.reject','project.takedown','project.delete')"
                        + " ORDER BY create_time DESC,id DESC LIMIT 1", projectId);
        String lastAction = null;
        Object lastActionTime = null;
        Object lastActorId = null;
        if (!logs.isEmpty()) {
            String a = String.valueOf(logs.get(0).get("action"));
            lastAction = a.substring("project.".length()).toUpperCase(Locale.ROOT);
            lastActionTime = String.valueOf(logs.get(0).get("create_time"));
            lastActorId = String.valueOf(logs.get(0).get("actor_id"));
        }
        String statusOrigin = null;
        if (project.getStatus() != null && project.getStatus() == 2) {
            statusOrigin = "TAKEDOWN".equals(lastAction) ? "TAKEDOWN" : "REJECT";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(project.getId()));
        out.put("title", project.getTitle());
        out.put("status", project.getStatus());
        out.put("inheritorId", String.valueOf(project.getInheritorId()));
        out.put("category", project.getCategory());
        out.put("auditReason", project.getAuditReason());
        out.put("lastAction", lastAction);
        out.put("lastActionTime", lastActionTime);
        out.put("lastActorId", lastActorId);
        out.put("statusOrigin", statusOrigin);
        // BT（2026-11-05）：下架三字段——键恒在，无值如实 null（不省略、不空串），
        // 供管理端展示「合规下架至 X 归档」
        out.put("takedownKind", project.getTakedownKind());
        out.put("exportUntil", project.getExportUntil() == null ? null : project.getExportUntil().toString());
        out.put("archiveAt", project.getArchiveAt() == null ? null : project.getArchiveAt().toString());
        out.put("createTime", project.getCreateTime() == null ? null : project.getCreateTime().toString());
        out.put("updateTime", project.getUpdateTime() == null ? null : project.getUpdateTime().toString());
        return out;
    }

    // ── AY（2026-11-05）：下架与删除 ──

    /** 理由必填校验；超列宽拒绝（不静默截断，与 ProjectTakedownDTO @Size 口径一致）。 */
    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "必须填写理由");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 300) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "理由不超过 300 字");
        }
        return trimmed;
    }

    /** 通知失败不阻断管理动作（与 GrowthService.reviewBooking 的通知口径一致）。 */
    private void notifyProjectSafely(Long inheritorId, Long projectId, String title, String actionName, String reason) {
        try {
            notificationService.notifyProjectStatus(inheritorId, projectId, title, actionName, reason);
        } catch (Exception e) {
            log.warn("项目{}通知写入失败（不阻断主流程）: projectId={}, err={}", actionName, projectId, e.getMessage());
        }
    }

    /** 运营下架截止时间的产品红线：距现在至少 7 天（与配置校验 @Min(7) 同口径）。 */
    private static final int MIN_OPERATIONAL_DEADLINE_DAYS = 7;

    @Override
    @Transactional
    public void takedownProject(Long projectId, String reason, String kind, LocalDateTime archiveAt) {
        String trimmed = requireReason(reason);
        // 分层下架（2026-11-05）：类型必填，两类差异化行为见 TakedownStateResolver
        String normalizedKind = normalizeKind(kind);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime exportUntil = null;
        String noticeKey;
        if (com.zhiyu.modules.craft.takedown.TakedownStateResolver.KIND_COMPLIANCE.equals(normalizedKind)) {
            if (archiveAt != null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "合规下架不支持指定归档时间（导出窗口由系统配置）");
            }
            exportUntil = now.plusHours(takedownProperties.getCompliance().getExportWindowHours());
            noticeKey = com.zhiyu.modules.craft.takedown.TakedownStateResolver.NOTICE_COMPLIANCE;
        } else {
            if (archiveAt == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "运营下架必须填写截止时间");
            }
            if (archiveAt.isBefore(now.plusDays(MIN_OPERATIONAL_DEADLINE_DAYS))) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "截止时间距现在至少 " + MIN_OPERATIONAL_DEADLINE_DAYS + " 天");
            }
            noticeKey = com.zhiyu.modules.craft.takedown.TakedownStateResolver.NOTICE_OPERATIONAL;
        }
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 真实状态机：仅上架（1）→ 下架（2）；0 待审 / 2 已下架 / 3 已归档一律 3001，不可再审核复活
        if (project.getStatus() == null || project.getStatus() != 1) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "仅上架中的项目可下架");
        }
        project.setStatus(2);
        project.setAuditReason(trimmed);
        project.setTakedownKind(normalizedKind);
        project.setTakedownAt(now);
        project.setArchiveAt(archiveAt);
        project.setExportUntil(exportUntil);
        project.setNoticeKey(noticeKey);
        craftProjectMapper.updateById(project);
        operationLog.record("project.takedown", projectId);
        notifyProjectSafely(project.getInheritorId(), projectId, project.getTitle(), "takedown", trimmed);
        log.info("项目已下架: projectId={}, kind={}, operator={}", projectId, normalizedKind, UserContext.getUserId());
    }

    /** 下架类型必填且只能 compliance / operational。 */
    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "必须选择下架类型（compliance 或 operational）");
        }
        String k = kind.trim().toLowerCase(java.util.Locale.ROOT);
        if (!com.zhiyu.modules.craft.takedown.TakedownStateResolver.KIND_COMPLIANCE.equals(k)
                && !com.zhiyu.modules.craft.takedown.TakedownStateResolver.KIND_OPERATIONAL.equals(k)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "下架类型只能为 compliance 或 operational");
        }
        return k;
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId, String reason) {
        String trimmed = requireReason(reason);
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 先收媒体引用（步骤音频 + 项目视频/封面），再级联逻辑删除，最后按引用计数退役
        List<String> audios = jdbc.queryForList(
                "SELECT audio_url FROM craft_step WHERE project_id=? AND deleted=0 AND audio_url IS NOT NULL",
                String.class, projectId);
        String video = project.getVideoUrl();
        String cover = project.getCoverUrl();
        jdbc.update("UPDATE craft_step SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE project_id=? AND deleted=0", projectId);
        jdbc.update("UPDATE craft_material SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE project_id=? AND deleted=0", projectId);
        // 注意：@TableLogic 字段不参与 updateById 的 SET——先落理由，再走逻辑删除
        project.setAuditReason(trimmed);
        craftProjectMapper.updateById(project);
        craftProjectMapper.deleteById(projectId);
        for (String audio : audios) {
            mediaService.retireOrphanedMedia(audio);
        }
        mediaService.retireOrphanedMedia(video);
        mediaService.retireOrphanedMedia(cover);
        operationLog.record("project.delete", projectId);
        notifyProjectSafely(project.getInheritorId(), projectId, project.getTitle(), "delete", trimmed);
        log.info("项目已逻辑删除: projectId={}, operator={}", projectId, UserContext.getUserId());
    }

    @Override
    public PageVO<CareReminderVO> getCareReminders(Integer status, Integer page) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        Long actor = UserContext.getUserId();
        boolean admin = Integer.valueOf(3).equals(UserContext.getRole());

        StringBuilder where = new StringBuilder(" WHERE r.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        if (!admin) {
            // 咨询师只能看到未领取任务（供领取）与本人已领取任务，
            // 不能看到其他咨询师领取的个案。
            where.append(" AND (c.owner_id IS NULL OR c.owner_id = ?)");
            args.add(actor);
        }

        String from = " FROM care_reminder r"
                + " LEFT JOIN care_case c ON c.reminder_id = r.id AND c.deleted = 0"
                + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(PAGE_SIZE);
        pageArgs.add((pageNum - 1) * PAGE_SIZE);
        String sql = "SELECT r.id, r.user_id, r.session_id, r.risk_level, r.summary,"
                + " r.status, r.create_time, c.owner_id" + from
                + " ORDER BY r.create_time DESC LIMIT ? OFFSET ?";

        List<CareReminderVO> records = jdbc.query(sql, (rs, rowNum) -> {
            CareReminderVO vo = new CareReminderVO();
            vo.setId(rs.getLong("id"));
            vo.setRiskLevel(rs.getInt("risk_level"));
            vo.setStatus(rs.getInt("status"));
            vo.setCreateTime(rs.getTimestamp("create_time") == null
                    ? null : rs.getTimestamp("create_time").toLocalDateTime());
            Long ownerId = rs.getObject("owner_id", Long.class);
            boolean claimed = ownerId != null;
            boolean mine = claimed && ownerId.equals(actor);
            vo.setClaimed(claimed);
            vo.setMine(mine);
            if (admin || mine) {
                vo.setUserId(rs.getObject("user_id", Long.class));
                vo.setSessionId(rs.getObject("session_id", Long.class));
                vo.setSummary(rs.getString("summary"));
            } else {
                // 未领取：咨询师只看到风险等级与时间，领取后才展示摘要与关联标识。
                vo.setUserId(null);
                vo.setSessionId(null);
                vo.setSummary(null);
            }
            return vo;
        }, pageArgs.toArray());
        return new PageVO<>(total == null ? 0 : total, pageNum, PAGE_SIZE, records);
    }

    @Override
    public DashboardStatsVO getDashboardStats() {
        DashboardStatsVO vo = new DashboardStatsVO();

        // totalUsers
        long totalUsers = userMapper.selectCount(null);
        vo.setTotalUsers(totalUsers);

        // totalWorks
        long totalWorks = workMapper.selectCount(null);
        vo.setTotalWorks(totalWorks);

        // pendingProjects
        LambdaQueryWrapper<CraftProject> projectWrapper = new LambdaQueryWrapper<>();
        projectWrapper.eq(CraftProject::getStatus, 0);
        long pendingProjects = craftProjectMapper.selectCount(projectWrapper);
        vo.setPendingProjects(pendingProjects);

        // pendingCareReminders
        LambdaQueryWrapper<CareReminder> reminderWrapper = new LambdaQueryWrapper<>();
        reminderWrapper.eq(CareReminder::getStatus, 0);
        long pendingCareReminders = careReminderMapper.selectCount(reminderWrapper);
        vo.setPendingCareReminders(pendingCareReminders);

        // moodDistribution
        vo.setMoodDistribution(getMoodDistribution());

        // userTrend7d
        vo.setUserTrend7d(getUserTrend7d());

        return vo;
    }

    // ── 私有方法 ──

    private AuditQueueItemVO toAuditQueueItemVO(CraftProject project) {
        AuditQueueItemVO vo = new AuditQueueItemVO();
        vo.setId(project.getId());
        vo.setTitle(project.getTitle());
        vo.setIntro(project.getIntro());
        vo.setCategory(project.getCategory());
        vo.setLevel(project.getLevel());
        vo.setEstMinutes(project.getEstMinutes());
        vo.setTherapyTags(project.getTherapyTags());
        vo.setCoverUrl(project.getCoverUrl());
        vo.setVideoUrl(project.getVideoUrl());
        vo.setInheritorId(project.getInheritorId());
        vo.setStatus(project.getStatus());
        vo.setCreateTime(project.getCreateTime());
        return vo;
    }

    private List<DashboardStatsVO.MoodItem> getMoodDistribution() {
        List<EmotionSession> sessions = emotionSessionMapper.selectList(null);
        Map<String, Long> moodCount = sessions.stream()
                .filter(s -> s.getMood() != null && !s.getMood().isBlank())
                .collect(Collectors.groupingBy(EmotionSession::getMood, Collectors.counting()));

        return moodCount.entrySet().stream()
                .map(e -> {
                    DashboardStatsVO.MoodItem item = new DashboardStatsVO.MoodItem();
                    item.setMood(e.getKey());
                    item.setCount(e.getValue().intValue());
                    return item;
                })
                .sorted(Comparator.comparing(DashboardStatsVO.MoodItem::getCount).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardStatsVO.TrendItem> getUserTrend7d() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(User::getCreateTime, sevenDaysAgo);
        List<User> recentUsers = userMapper.selectList(wrapper);

        Map<LocalDate, Long> dailyCount = recentUsers.stream()
                .filter(u -> u.getCreateTime() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getCreateTime().toLocalDate(),
                        Collectors.counting()));

        List<DashboardStatsVO.TrendItem> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DashboardStatsVO.TrendItem item = new DashboardStatsVO.TrendItem();
            item.setDate(date.toString());
            item.setCount(dailyCount.getOrDefault(date, 0L).intValue());
            trend.add(item);
        }
        return trend;
    }
}
