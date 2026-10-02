package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推荐主表实体（T1.5）。
 *
 * <p>对应 {@code craft_recommendation} 表（见 sql/schema.sql）。
 *
 * <p><b>关于 reason 加密</b>：schema.sql 中 {@code craft_recommendation.reason} 注释写了
 * "(加密)"，但推荐理由由 LLM 生成，不含用户原始对话内容，不属于 AGENTS.md §5
 * 敏感字段清单（对话内容、心情便签）范畴。本批次 reason 明文存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_recommendation")
public class CraftRecommendation extends BaseEntity {

    /** 推荐目标用户 user.id */
    private Long userId;

    /** 关联情绪会话 emotion_session.id，可空 */
    private Long sessionId;

    /** 情绪 code，取值见 dict_mood.code */
    private String mood;

    /** 整体推荐理由（明文存储，见类注释） */
    private String reason;
}
