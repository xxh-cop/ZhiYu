package com.zhiyu.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核队列项 VO（M2 批 4）。
 *
 * <p>对应 craft_project 表中 status=0（待审）的项目。
 */
@Data
public class AuditQueueItemVO {

    /** 项目 ID */
    private Long id;

    /** 项目标题 */
    private String title;

    /** 项目简介 */
    private String intro;

    /** 技艺品类 */
    private String category;

    /** 难度等级：1 入门 / 2 进阶 / 3 高阶 */
    private Integer level;

    /** 预计时长（分钟） */
    private Integer estMinutes;

    /** 疗愈效果标签 */
    private String therapyTags;

    /** 封面图 URL */
    private String coverUrl;
    private String videoUrl;

    /** 上传传承人 ID */
    private Long inheritorId;

    /** 审核状态：0 待审 / 1 上架 / 2 下架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ── 2026-11-04（I2）：work / comment 通用队列字段（project 类型保持既有字段） ──

    /** 队列类型：project / work / comment。 */
    private String type;

    /** 作者（归属者）ID；project 类型同 inheritorId。 */
    private Long authorId;

    /** 关联对象 ID（comment 类型为所属作品 id）。 */
    private Long refId;

    /** 内容摘要（work 为媒体类型说明，comment 为评论正文）。 */
    private String excerpt;

    /**
     * 对象级 AI 审计摘要（脱敏：scene/action/reason/createTime，不含原文）。
     * 关联不上的对象返回空数组，不伪造（见 auditHint）。
     */
    private java.util.List<java.util.Map<String, Object>> aiAudits;

    /** aiAudits 为空时的说明（历史数据未记录 ref 关联）。 */
    private String auditHint;
}
