package com.zhiyu.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出审计留痕实体（2026-11-04 N2，见 docs/export-audit-20261104.md）。
 *
 * <p><b>不继承 BaseEntity</b>：审计语义同 ai_audit_log——只追加、不更新、不删除，
 * 故只保留 {@code create_time}，没有 {@code update_time} 与逻辑删除。
 */
@Data
@TableName("admin_export_log")
public class AdminExportLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 导出人（管理员 ID）。 */
    private Long actorId;

    /** 导出时角色（快照）。 */
    private Integer actorRole;

    /** 导出对象：users / works / comments。 */
    private String targetType;

    /** 筛选条件脱敏摘要（不存敏感原文）。 */
    private String filterSummary;

    /** 导出字段（已通过黑名单校验）。 */
    private String fieldList;

    /** 导出条数。 */
    private Integer recordCount;

    /** 导出用途（必填）。 */
    private String purpose;

    /** 导出批次号（文件名/文件头引用，唯一）。 */
    private String batchNo;

    /** 0 正常 / 1 超阈值批量告警。 */
    private Integer alertFlag;

    /** 留痕时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
