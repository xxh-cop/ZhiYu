package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 创作会话实体（T1.6）。
 *
 * <p>对应 {@code craft_session} 表（见 sql/schema.sql）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_session")
public class CraftSession extends BaseEntity {

    /** 所属用户 user.id */
    private Long userId;

    /** 项目 ID craft_project.id */
    private Long projectId;

    /** 0待开始 1进行中 2已暂停 3已完成 4已放弃；创建即 1 */
    private Integer status;

    /** 当前步骤序号 */
    private Integer currentStep;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 心流累计时长（秒） */
    private Integer flowSeconds;
}
