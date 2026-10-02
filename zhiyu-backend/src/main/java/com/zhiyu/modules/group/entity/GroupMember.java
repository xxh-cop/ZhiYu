package com.zhiyu.modules.group.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小组成员实体（T1.8b）。
 *
 * <p>对应 {@code group_member} 表。唯一键 (group_id, user_id)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("group_member")
public class GroupMember extends BaseEntity {

    /** 小组 ID */
    private Long groupId;

    /** 用户 ID */
    private Long userId;

    /** 角色：成员 / 组长 */
    private String role;
}
