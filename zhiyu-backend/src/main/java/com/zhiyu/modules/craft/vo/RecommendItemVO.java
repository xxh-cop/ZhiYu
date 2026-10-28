package com.zhiyu.modules.craft.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 推荐明细 VO（T1.5）。
 *
 * <p>对应 OpenAPI schema {@code RecommendItemVO}。
 */
@Data
public class RecommendItemVO {

    /** 项目 ID；按字符串返回，避免 JS 精度丢失。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;

    /** 项目标题 */
    private String title;

    /** 项目简介 */
    private String intro;

    /** 文化背景 */
    private String cultureBg;

    /** 1 入门 / 2 进阶 / 3 高阶 */
    private Integer level;

    /** 预计时长（分钟） */
    private Integer estMinutes;

    /** 疗愈效果标签（逗号分隔字符串） */
    private String therapyTags;

    /** 封面图 URL */
    private String coverUrl;

    /** 规则层打分 */
    private Double score;

    /** 单项推荐理由 */
    private String reason;
}
