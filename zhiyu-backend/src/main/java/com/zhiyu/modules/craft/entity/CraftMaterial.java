package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 手作材料实体，对应 {@code craft_material} 表。
 *
 * <p>id / createTime / updateTime / deleted 由 {@link BaseEntity} 提供。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_material")
public class CraftMaterial extends BaseEntity {

    /** 所属项目 ID */
    private Long projectId;

    /** 材料名称 */
    private String name;

    /** 用量 */
    private String qty;
}
