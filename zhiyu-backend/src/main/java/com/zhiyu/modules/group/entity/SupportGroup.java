package com.zhiyu.modules.group.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 互助小组实体（T1.8b）。
 *
 * <p>对应 {@code support_group} 表（见 sql/schema.sql）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("support_group")
public class SupportGroup extends BaseEntity {

    /** 小组名称 */
    private String name;

    /** 小组描述 */
    private String description;

    /** 人数上限 */
    private Integer maxCount;

    /** 当前成员数 */
    private Integer memberCount;

    /** 0关闭 1开放 */
    private Integer status;
}
