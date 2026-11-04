package com.zhiyu.modules.notification.vo;

import lombok.Data;

/**
 * 通知列表条目（接口出参）。
 *
 * <p>雪花 ID 全部按字符串返回（AGENTS.md §5.1：禁止转成 JavaScript Number 后再提交）。
 * 正文为脱敏内容，不含他人隐私。
 */
@Data
public class NotificationVO {

    /** 通知 ID（字符串）。 */
    private String id;

    /** 通知类型。 */
    private String type;

    /** 标题。 */
    private String title;

    /** 脱敏正文（可空）。 */
    private String content;

    /** 关联对象类型。 */
    private String refType;

    /** 关联对象 ID（字符串，可空）。COMMENT_AUDIT 为评论所属作品 ID（跳转目标）。 */
    private String refId;

    /** 二级关联对象 ID（字符串，可空；S2 2026-11-04）。COMMENT_AUDIT 为评论 ID 锚点，学生端可直达定位。 */
    private String subRefId;

    /** 是否已读。 */
    private Boolean read;

    /** 创建时间。 */
    private String createTime;
}
