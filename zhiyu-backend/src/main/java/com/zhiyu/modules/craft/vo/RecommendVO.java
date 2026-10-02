package com.zhiyu.modules.craft.vo;

import lombok.Data;

import java.util.List;

/**
 * 推荐结果 VO（T1.5）。
 *
 * <p>对应 OpenAPI schema {@code RecommendVO}。
 */
@Data
public class RecommendVO {

    /** 推荐记录 ID（craft_recommendation.id） */
    private Long recommendationId;

    /** 情绪 code */
    private String mood;

    /** 整体推荐理由 */
    private String reason;

    /** 推荐明细列表 */
    private List<RecommendItemVO> items;
}
