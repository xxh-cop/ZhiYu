package com.zhiyu.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户通知实体（2026-11-04 通知系统）。
 *
 * <p>事件源：作品审核、评论审核、预约回复、小组新成员、关怀跟进提醒。
 * 只存脱敏内容（AGENTS.md §5）：关怀类通知正文为固定引导语，
 * 不含学生身份与会话内容；评论类不含评论原文。
 *
 * <p>幂等：{@code userId + type + refType + refId + dedupeKey} 有唯一索引
 * （uk_notification_dedupe），同类同对象同结果只生成一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /** 接收者用户 ID（本人数据隔离）。 */
    private Long userId;

    /** 通知类型：WORK_AUDIT / COMMENT_AUDIT / BOOKING_REPLY / GROUP_JOIN / CARE_REMINDER。 */
    private String type;

    /** 标题（面向用户的短句）。 */
    private String title;

    /** 脱敏正文（可空；关怀类只含引导语）。 */
    private String content;

    /** 关联对象类型：work / comment / booking / group / care_reminder。 */
    private String refType;

    /** 跳转目标 ID（雪花，接口按字符串返回）。COMMENT_AUDIT 为评论所属作品 ID（S2 2026-11-04）。 */
    private Long refId;

    /** 二级关联对象 ID（S2 2026-11-04）：COMMENT_AUDIT 存评论 ID 锚点，其余类型为 NULL。 */
    private Long subRefId;

    /** 幂等去重键。 */
    private String dedupeKey;

    /** 已读标记：0 未读 / 1 已读。 */
    private Integer readFlag;
}
