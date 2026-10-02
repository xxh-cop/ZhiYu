package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推荐明细实体（T1.5）。
 *
 * <p>对应 {@code craft_recommendation_item} 表（见 sql/schema.sql）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_recommendation_item")
public class CraftRecommendationItem extends BaseEntity {

    /** 所属推荐主表 craft_recommendation.id */
    private Long recommendationId;

    /** 推荐的 craft_project.id */
    private Long projectId;

    /** 规则层打分 */
    private Double score;

    /** 单项推荐理由 */
    private String reason;
}
