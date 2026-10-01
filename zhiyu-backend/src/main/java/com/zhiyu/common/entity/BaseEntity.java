package com.zhiyu.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务表实体基类，复用 {@code id / create_time / update_time / deleted} 四个公共字段。
 *
 * <p>所有业务表实体都继承此类，不要重复声明这四个字段。
 *
 * <p><b>例外</b>：审计日志表 {@code ai_audit_log} <b>不继承</b>此类
 * —— 该表只追加、不更新、不删除（AGENTS.md §5），因此没有 {@code updateTime}
 * 与逻辑删除语义，请单独定义实体。
 */
@Data
public class BaseEntity {

    /**
     * 主键。由应用侧雪花算法生成（AGENTS.md §5：DDL 不设 AUTO_INCREMENT）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间，插入时由 {@code MetaObjectFillHandler} 自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时由 {@code MetaObjectFillHandler} 自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0 未删除 / 1 已删除。
     *
     * <p>{@code select = false} 表示查询时不返回该列，避免把内部标记透出到接口。
     * 逻辑删除的全局取值由 {@code application.yml} 的
     * {@code mybatis-plus.global-config.db-config.logic-delete-field} 等配置决定。
     */
    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
