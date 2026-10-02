package com.zhiyu.modules.work.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品点赞/收藏实体（T1.8a）。
 *
 * <p>对应 {@code work_like} 表。唯一键 (work_id, user_id, type)。
 * toggle 语义：重复请求即取消。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_like")
public class WorkLike extends BaseEntity {

    /** 作品 ID */
    private Long workId;

    /** 用户 ID */
    private Long userId;

    /** 1点赞 0收藏 */
    private Integer type;
}
