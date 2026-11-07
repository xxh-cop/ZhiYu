package com.zhiyu.modules.work.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品评论实体（T1.8a）。
 *
 * <p>对应 {@code work_comment} 表。评论者实名（无 anonymous 字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_comment")
public class WorkComment extends BaseEntity {

    /** 作品 ID */
    private Long workId;

    /** 评论者 user.id */
    private Long userId;

    /** 评论内容 */
    private String content;

    /** 1展示 0隐藏 */
    private Integer status;

    /** 软档降权截止（2026-11-07 处置链）：NULL=不降权；处置链开关关闭时读侧忽略本列。 */
    private java.time.LocalDateTime softDemoteUntil;
}
