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
}
