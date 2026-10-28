package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推荐点击事件（20261028）。
 *
 * <p>对应 {@code craft_recommend_event} 表；v1 只写 CLICK，
 * 唯一键保证同一用户/推荐/项目/事件只记一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_recommend_event")
public class CraftRecommendEvent extends BaseEntity {

    private Long userId;

    private Long recommendationId;

    private Long projectId;

    private String eventType;

    private String source;
}
