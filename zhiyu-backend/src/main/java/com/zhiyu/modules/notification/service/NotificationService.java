package com.zhiyu.modules.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.notification.entity.Notification;
import com.zhiyu.modules.notification.mapper.NotificationMapper;
import com.zhiyu.modules.notification.vo.NotificationVO;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知系统服务（2026-11-04）。
 *
 * <p>两类职责：
 * <ol>
 *   <li><b>查询侧</b>：未读数（5 秒短 TTL 进程内缓存，支撑首页轮询）、
 *       分页列表（按类型筛选）、标记已读 / 全部已读（幂等）。
 *       一切查询只按 {@link UserContext} 的本人 ID 过滤，越权标记返回 1003。</li>
 *   <li><b>事件侧</b>：供作品审核、评论审核、预约回复、小组加入、关怀提醒
 *       五个触发点调用的 {@code notify*} 方法；正文模板在此集中，
 *       保证脱敏口径一致（关怀类不含用户身份，评论类不含评论原文）。</li>
 * </ol>
 *
 * <p>幂等去重：{@code userId + type + refType + refId + dedupeKey} 先查后插，
 * 数据库唯一索引 uk_notification_dedupe 兜底；同类同对象同结果不重复刷屏。
 *
 * <p>日志纪律：只记 id / type / refId，<b>不打印通知正文</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 合法通知类型（非法值列表接口返回 1001）。 */
    public static final Set<String> TYPES = Set.of(
            "WORK_AUDIT", "COMMENT_AUDIT", "BOOKING_REPLY", "GROUP_JOIN", "CARE_REMINDER",
            "CAMPUS_AUDIT");

    /** 未读数缓存 TTL（毫秒）：首页轮询高频，5 秒内复用计数。 */
    private static final long UNREAD_CACHE_TTL_MS = 5_000L;

    private final NotificationMapper notificationMapper;

    /** 未读数短 TTL 缓存：userId -> [count, expireAt]。 */
    private final ConcurrentHashMap<Long, long[]> unreadCache = new ConcurrentHashMap<>();

    // ── 查询侧 ──

    /** 当前用户未读数（短 TTL 缓存）。 */
    public long unreadCount() {
        long uid = requireUser();
        long[] hit = unreadCache.get(uid);
        long now = System.currentTimeMillis();
        if (hit != null && now < hit[1]) {
            return hit[0];
        }
        Long count = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, uid)
                .eq(Notification::getReadFlag, 0));
        long value = count == null ? 0L : count;
        unreadCache.put(uid, new long[]{value, now + UNREAD_CACHE_TTL_MS});
        return value;
    }

    /** 分页列表（可选按类型筛选），只返回本人通知。 */
    public Map<String, Object> list(int page, int size, String type) {
        long uid = requireUser();
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "分页参数无效");
        }
        if (type != null && !type.isBlank() && !TYPES.contains(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "通知类型无效");
        }
        var query = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, uid)
                .eq(type != null && !type.isBlank(), Notification::getType, type)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId);
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size), query);
        List<NotificationVO> records = result.getRecords().stream().map(this::toVO).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        // total 是计数语义，用 Integer 返回 JSON number（同 unread-count 的口径说明）
        data.put("total", (int) result.getTotal());
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 标记单条已读（幂等；不存在 1004；他人 1003）。 */
    public void markRead(long id) {
        long uid = requireUser();
        Notification target = notificationMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (uid != target.getUserId()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (target.getReadFlag() != null && target.getReadFlag() == 1) {
            return; // 幂等：已读不重复写
        }
        target.setReadFlag(1);
        notificationMapper.updateById(target);
        unreadCache.remove(uid);
    }

    /** 全部标记已读（幂等，返回实际更新条数）。 */
    public int markAllRead() {
        long uid = requireUser();
        Notification update = new Notification();
        update.setReadFlag(1);
        int changed = notificationMapper.update(update, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, uid)
                .eq(Notification::getReadFlag, 0));
        unreadCache.remove(uid);
        return changed;
    }

    // ── AQ4（2026-11-04）：通知删除（仅本人，逻辑删除） ──

    /** 删除单条本人通知。不存在 1004；他人 1003（不泄露存在性之外的归属信息）。 */
    public void deleteOne(long id) {
        long uid = requireUser();
        Notification target = notificationMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (uid != target.getUserId()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        notificationMapper.deleteById(id); // 逻辑删除（全局 logic-delete 配置）
        unreadCache.remove(uid);
        log.info("通知已删除: id={}, owner={}", id, uid);
    }

    /** 批量清空本人通知（可按类型过滤；type 为空=全部）。返回实际删除条数。 */
    public int clearAll(String type) {
        long uid = requireUser();
        if (type != null && !type.isBlank() && !TYPES.contains(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "通知类型无效");
        }
        int changed = notificationMapper.delete(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, uid)
                .eq(type != null && !type.isBlank(), Notification::getType, type));
        unreadCache.remove(uid);
        log.info("通知批量清空: owner={}, type={}, deleted={}", uid, type == null ? "ALL" : type, changed);
        return changed;
    }

    // ── 事件侧（供其他模块触发点调用） ──

    /** 作品审核结果：status 1=已发布 / 2=已下架。接收者=作品归属者。 */
    public boolean notifyWorkAudit(Long ownerUserId, Long workId, String workTitle, int status) {
        return notifyWorkAudit(ownerUserId, workId, workTitle, status, null);
    }

    /**
     * 作品审核结果（含理由）。驳回/下架时正文附「原因：xxx」（I1，2026-11-04）。
     * reason 来自管理员输入（非作品内容），不含额外隐私；通知内截断到 120 字。
     */
    public boolean notifyWorkAudit(Long ownerUserId, Long workId, String workTitle, int status, String reason) {
        if (ownerUserId == null || workId == null) {
            return false;
        }
        String title = status == 1 ? "作品审核已通过，现已公开" : "作品已被下架";
        String content = "你的作品《" + abbreviate(workTitle, 60) + "》"
                + (status == 1 ? "已通过审核并发布。" : "已被管理员下架，如有疑问请联系管理员。");
        if (status == 2 && reason != null && !reason.isBlank()) {
            content += "原因：" + abbreviate(reason, 120);
        }
        return push(ownerUserId, "WORK_AUDIT", title, content, "work", workId, "work:" + workId + ":" + status);
    }

    /**
     * 评论审核结果（含理由）：status 1=公开 / 2=隐藏。接收者=评论作者；不含评论原文。
     * 隐藏时正文附「原因：xxx」（I1，2026-11-04）。
     * S2（2026-11-04）：ref_id 指向评论所属作品（跳转目标），sub_ref_id 存评论锚点；
     * dedupe_key 仍按评论 id 生成，ref 语义变化不产生重复通知。
     */
    public boolean notifyCommentAudit(Long commentAuthorId, Long commentId, Long workId, int status, String reason) {
        if (commentAuthorId == null || commentId == null || workId == null) {
            return false;
        }
        String title = status == 1 ? "你的评论已通过审核" : "你的评论未通过审核";
        String content = status == 1
                ? "你的评论已通过审核并公开展示。"
                : "你的评论未通过审核，已被隐藏。发布内容请遵守社区约定。";
        if (status == 2 && reason != null && !reason.isBlank()) {
            content += "原因：" + abbreviate(reason, 120);
        }
        return push(commentAuthorId, "COMMENT_AUDIT", title, content, "comment", workId, commentId,
                "comment:" + commentId + ":" + status);
    }

    /** 预约回复：confirmed=true 已确认 / false 未通过。接收者=预约用户。 */
    public boolean notifyBookingReply(Long bookingUserId, Long bookingId, String programTitle, boolean confirmed) {
        if (bookingUserId == null || bookingId == null) {
            return false;
        }
        String title = confirmed ? "你的一对一预约已确认" : "你的一对一预约未通过";
        String content = "你预约的「" + abbreviate(programTitle, 40) + "」"
                + (confirmed ? "已确认，请按时参加。" : "未通过，可选择其他时段或联系主办人。");
        return push(bookingUserId, "BOOKING_REPLY", title, content, "booking", bookingId,
                "booking:" + bookingId + ":" + (confirmed ? "CONFIRM" : "REJECT"));
    }

    /**
     * 小组新成员动态。接收者=小组组长（group_member role='组长'，不含新成员本人）。
     * 正文不含新成员昵称（他人隐私）。
     * 去重键含新成员 id：每位新成员一条；同一人退出后重复加入不重复刷屏。
     */
    public boolean notifyGroupJoin(Long leaderUserId, Long groupId, String groupName, Long joinedUserId) {
        if (leaderUserId == null || groupId == null || joinedUserId == null) {
            return false;
        }
        return push(leaderUserId, "GROUP_JOIN", "小组来了新成员",
                "有 1 位新成员加入了小组《" + abbreviate(groupName, 40) + "》。",
                "group", groupId, "group:" + groupId + ":user:" + joinedUserId);
    }

    /**
     * 校园身份核验结果（AH3，2026-11-04）。接收者=申请人本人。
     * approved=true 已通过 / false 未通过；未通过时正文附「原因：xxx」
     * （reason 为管理员审核输入，与作品/评论驳回文案同风格，截断 120 字）。
     * refType=campus_verification，refId=核验记录 id（无二级锚点，subRefId=null）。
     */
    public boolean notifyCampusAudit(Long applicantUserId, Long verificationId,
                                     boolean approved, String reason) {
        if (applicantUserId == null || verificationId == null) {
            return false;
        }
        String title = approved ? "校园身份核验已通过" : "校园身份核验未通过";
        String content = approved
                ? "你的校园身份核验已通过审核。"
                : "你的校园身份核验未通过，可修改信息后重新提交。";
        if (!approved && reason != null && !reason.isBlank()) {
            content += "原因：" + abbreviate(reason, 120);
        }
        return push(applicantUserId, "CAMPUS_AUDIT", title, content,
                "campus_verification", verificationId,
                "campus:" + verificationId + ":" + (approved ? "APPROVED" : "REJECTED"));
    }

    /**
     * 项目下架/删除通知（AY1/AY2，2026-11-05）。接收者=项目传承人。
     * 复用 WORK_AUDIT 类型（与作品审核同一「内容处置」语义，前端已有渲染分支），
     * refType=project 供区分跳转；action 仅允许 takedown/delete。理由必填（管理动作问责）。
     */
    public boolean notifyProjectStatus(Long inheritorUserId, Long projectId, String projectTitle,
                                       String action, String reason) {
        if (inheritorUserId == null || projectId == null) {
            return false;
        }
        boolean takedown = "takedown".equals(action);
        if (!takedown && !"delete".equals(action)) {
            return false;
        }
        String title = takedown ? "你的项目已被下架" : "你的项目已被删除";
        String content = "你的项目《" + abbreviate(projectTitle, 60) + "》已被管理员"
                + (takedown ? "下架，不再对学生展示。" : "删除。")
                + "如有疑问请联系管理员。原因：" + abbreviate(reason, 120);
        return push(inheritorUserId, "WORK_AUDIT", title, content, "project", projectId,
                "project:" + projectId + ":" + action);
    }

    /**
     * 项目审核结果通知（BE，2026-11-05）。接收者=项目传承人；通过与驳回都触达。
     * 复用 WORK_AUDIT + refType=project（与 AY 下架/删除同一「内容处置」语义族，
     * 学生端现有渲染分支零依赖可用）。驳回附理由（截断 120 字，与作品/评论同风格）。
     * dedupe：project:{id}:audit:{APPROVE|REJECT}——审核状态机只受理 status=0，
     * 同一项目同一结果天然不重复，dedupe 键兜底未来可能的重审流程。
     */
    public boolean notifyProjectAudit(Long inheritorUserId, Long projectId, String projectTitle,
                                      boolean approved, String reason) {
        if (inheritorUserId == null || projectId == null) {
            return false;
        }
        String title = approved ? "你的项目已通过审核" : "你的项目未通过审核";
        String content = "你的项目《" + abbreviate(projectTitle, 60) + "》"
                + (approved ? "已通过审核并上架，学生现在可以看到它了。"
                : "未通过审核，暂不对外展示。可修正后创建新项目重新提交审核。");
        if (!approved && reason != null && !reason.isBlank()) {
            content += "原因：" + abbreviate(reason, 120);
        }
        return push(inheritorUserId, "WORK_AUDIT", title, content, "project", projectId,
                "project:" + projectId + ":audit:" + (approved ? "APPROVE" : "REJECT"));
    }

    /**
     * 关怀跟进提醒。接收者=授权角色（咨询师/管理员），
     * 正文为固定引导语，<b>不含学生身份与会话内容</b>（AGENTS.md §5 脱敏口径）。
     */
    public boolean notifyCareReminder(Long recipientUserId, Long reminderId) {
        if (recipientUserId == null || reminderId == null) {
            return false;
        }
        return push(recipientUserId, "CARE_REMINDER", "有新的关怀跟进提醒待处理",
                "关怀工作台有新的待跟进事项，请及时查看。",
                "care_reminder", reminderId, "care:" + reminderId);
    }

    /**
     * BG1（2026-11-05）：传承人入驻核验结果。接收者=传承人本人（inheritor_profile.user_id）。
     * 类型复用 CAMPUS_AUDIT（同为「身份核验结果」语义族，两端已有标签「身份核验」）；
     * refType=inheritor_profile 区分学生校园核验（campus_verification）。
     * 现状说明：verifyInheritor 的 DTO/表/服务三层都没有理由字段——按 BG 批既定口径
     * 只通知状态、不带理由，不为了通知硬加字段（如需驳回理由属产品决策，另派）。
     * dedupe：核验记录 + 结果；同结果重复触发不重复推送，撤销↔通过属不同结果各自触达。
     */
    public boolean notifyInheritorVerify(Long inheritorUserId, Long profileId, boolean approved) {
        if (inheritorUserId == null || profileId == null) {
            return false;
        }
        String title = approved ? "传承人入驻核验已通过" : "传承人入驻核验未通过";
        String content = approved
                ? "你的传承人入驻核验已通过，现在可以维护非遗项目与课程了。"
                : "你的传承人入驻核验未通过，当前保持非认证状态。如有疑问请联系管理员。";
        return push(inheritorUserId, "CAMPUS_AUDIT", title, content, "inheritor_profile", profileId,
                "inheritor:" + profileId + ":verify:" + (approved ? "APPROVED" : "REVOKED"));
    }

    /**
     * BG2（2026-11-05）：小组停用/恢复。接收者=组长（group_member role='组长'），非全体成员。
     * 类型复用 GROUP_JOIN（小组域唯一既有类型，两端标签「小组动态」可承载状态动态）；
     * refType=group 与既有一致。
     * 现状说明：updateGroupStatus 无理由字段，停用只通知状态（如实上报，不硬加字段）。
     * dedupe：小组 + 状态 + 小时窗口——同小时重复切到同一状态只推一次，避免刷屏。
     */
    public boolean notifyGroupStatus(Long leaderUserId, Long groupId, String groupName, boolean enabled) {
        if (leaderUserId == null || groupId == null) {
            return false;
        }
        String title = enabled ? "你的小组已恢复使用" : "你的小组已被停用";
        String content = "你带领的小组《" + abbreviate(groupName, 40) + "》"
                + (enabled ? "已恢复开放，成员可以继续使用。"
                : "已被管理员停用，成员暂时无法使用。如有疑问请联系管理员。");
        String window = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"));
        return push(leaderUserId, "GROUP_JOIN", title, content, "group", groupId,
                "group:" + groupId + ":status:" + (enabled ? "OPEN" : "CLOSED") + ":" + window);
    }

    // ── 内部 ──

    /**
     * 落库一条通知（先查后插幂等去重，唯一索引兜底）。
     *
     * @return true=新建；false=重复事件被去重跳过
     */
    public boolean push(Long userId, String type, String title, String content,
                        String refType, Long refId, String dedupeKey) {
        return push(userId, type, title, content, refType, refId, null, dedupeKey);
    }

    /**
     * 落库一条通知（先查后插幂等去重，唯一索引兜底）。
     *
     * @param subRefId 二级关联对象 ID（S2 2026-11-04；COMMENT_AUDIT 存评论锚点，其余场景为 null）
     * @return true=新建；false=重复事件被去重跳过
     */
    public boolean push(Long userId, String type, String title, String content,
                        String refType, Long refId, Long subRefId, String dedupeKey) {
        Long existing = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, type)
                .eq(refType != null, Notification::getRefType, refType)
                .eq(refId != null, Notification::getRefId, refId)
                .eq(Notification::getDedupeKey, dedupeKey));
        if (existing != null && existing > 0) {
            log.info("通知去重跳过: type={}, refId={}, dedupeKey={}", type, refId, dedupeKey);
            return false;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setSubRefId(subRefId);
        n.setDedupeKey(dedupeKey);
        n.setReadFlag(0);
        try {
            notificationMapper.insert(n);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下先查后插的兜底：唯一索引命中即视为去重
            log.info("通知并发去重（唯一索引兜底）: type={}, refId={}", type, refId);
            return false;
        }
        unreadCache.remove(userId);
        log.info("通知已创建: id={}, type={}, refId={}, recipient={}", n.getId(), type, refId, userId);
        return true;
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(String.valueOf(n.getId()));
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setRefType(n.getRefType());
        vo.setRefId(n.getRefId() == null ? null : String.valueOf(n.getRefId()));
        vo.setSubRefId(n.getSubRefId() == null ? null : String.valueOf(n.getSubRefId()));
        vo.setRead(n.getReadFlag() != null && n.getReadFlag() == 1);
        vo.setCreateTime(n.getCreateTime() == null ? null : n.getCreateTime().toString());
        return vo;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static long requireUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return uid;
    }
}
