package com.zhiyu.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 审计日志实体（T1.3b）。
 *
 * <p><b>不继承 {@link com.zhiyu.common.entity.BaseEntity}</b>（AGENTS.md §5 例外）：
 * 审计日志只追加、不更新、不删除，因此只保留 {@code create_time}，
 * 没有 {@code update_time} 与 {@code deleted}。
 *
 * <p>字段严格按 {@code sql/schema.sql}：
 * <ul>
 *   <li>{@code rawInput} —— 加密存储（AES-256-GCM，{@link com.zhiyu.common.crypto.AesEncryptor}）</li>
 *   <li>{@code aiOutput} —— 加密存储</li>
 * </ul>
 */
@Data
@TableName("ai_audit_log")
public class AiAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联用户 ID（非空写入；迁移前的历史行可能为 {@code null}）。
     *
     * <p>不是外键：审计日志只追加，用户注销后仍保留归属线索。
     */
    private Long userId;

    /** 场景：chat / recommend / work_message 等。 */
    private String scene;

    /** 原始输入（加密存储），可空。 */
    private String rawInput;

    /** AI 输出（加密存储），可空。 */
    private String aiOutput;

    /** 过滤动作：PASS / REVIEW / REWRITE / BLOCK。 */
    private String action;

    /** 命中原因。 */
    private String reason;

    /**
     * 关联对象类型（2026-11-04 I2）：work / comment 等。
     * 2026-11-04 前的历史数据为 {@code null}——聚合查询对不上必须返回空，不伪造关联。
     */
    private String refType;

    /** 关联对象 ID（雪花）。 */
    private Long refId;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
