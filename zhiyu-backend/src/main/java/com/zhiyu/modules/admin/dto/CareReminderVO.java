package com.zhiyu.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关怀提醒 VO（M2 批 4）。
 *
 * <p>care_reminder.summary 本身已脱敏（不加密），直接返回。
 */
@Data
public class CareReminderVO {

    /** 关怀提醒 ID */
    private Long id;

    /** 触发用户 ID（不做二次脱敏，仅返回 ID 供管理端展示） */
    private Long userId;

    /** 关联会话 ID（可空） */
    private Long sessionId;

    /** 风险等级：1 一般 / 2 高危 */
    private Integer riskLevel;

    /** 脱敏摘要（不加密，供授权角色查看） */
    private String summary;

    /** 处理状态：0 待处理 / 1 已跟进 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
