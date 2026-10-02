package com.zhiyu.modules.craft.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创作会话 VO（T1.6）。
 *
 * <p>对应 OpenAPI schema {@code CraftSessionVO}。
 */
@Data
public class CraftSessionVO {

    /** 会话 ID */
    private Long sessionId;

    /** 项目 ID */
    private Long projectId;

    /** 项目标题（join craft_project 取） */
    private String projectTitle;

    /** 0待开始 1进行中 2已暂停 3已完成 4已放弃 */
    private Integer status;

    /** 状态中文标签 */
    private String statusLabel;

    /** 当前步骤序号 */
    private Integer currentStep;

    /** 项目总步骤数（count craft_step） */
    private Integer totalSteps;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
