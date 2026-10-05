package com.zhiyu.modules.craft.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手作项目列表项 VO（M2 批 6A）。
 *
 * <p>学生端传承人主页的项目列表展示。
 */
@Data
public class CraftProjectItemVO {

    /** 项目 ID */
    private Long id;

    /** 项目标题 */
    private String title;

    /** 项目简介 */
    private String intro;

    /** 封面图 URL */
    private String coverUrl;

    /** 技艺品类 */
    private String category;

    /** 1 入门 / 2 进阶 / 3 高阶 */
    private Integer level;

    /** 预计时长（分钟） */
    private Integer estMinutes;

    /** 疗愈效果标签，逗号分隔 */
    private String therapyTags;

    /** 创建时间 */
    private LocalDateTime createTime;
}
