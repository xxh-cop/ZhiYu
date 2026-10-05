package com.zhiyu.modules.inheritor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 传承人资料实体，对应 {@code sql/schema.sql} 的 {@code inheritor_profile} 表。
 *
 * <p>id / createTime / updateTime / deleted 由 {@link BaseEntity} 提供。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inheritor_profile")
public class InheritorProfile extends BaseEntity {

    /** 关联用户 ID（user.id） */
    private Long userId;

    /** 擅长技艺 */
    private String skill;

    /** 简介 */
    private String intro;

    /** 荣誉头衔 */
    private String honor;

    /** 0 未认证 / 1 已认证 */
    private Integer verified;
}
