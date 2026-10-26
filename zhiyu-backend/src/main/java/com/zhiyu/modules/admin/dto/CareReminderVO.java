package com.zhiyu.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关怀提醒 VO（M2 批 4）。
 *
 * <p>care_reminder.summary 本身已脱敏（不加密），但仅管理员与已领取的负责人可见；
 * 未领取任务对咨询师隐藏摘要与关联标识。
 */
@Data
public class CareReminderVO {

    /** 关怀提醒 ID */
    private Long id;

    /** 触发用户 ID；咨询师仅本人领取的任务可见，未领取时为 null */
    private Long userId;

    /** 关联会话 ID；咨询师仅本人领取的任务可见，未领取时为 null */
    private Long sessionId;

    /** 风险等级：1 一般 / 2 高危 */
    private Integer riskLevel;

    /** 脱敏摘要；咨询师领取后可见，未领取时为 null */
    private String summary;

    /** 处理状态：0 待处理 / 1 已跟进 */
    private Integer status;

    /** 是否已被领取（含被其他咨询师领取） */
    private Boolean claimed;

    /** 是否由当前咨询师领取 */
    private Boolean mine;

    /** 创建时间 */
    private LocalDateTime createTime;
}
