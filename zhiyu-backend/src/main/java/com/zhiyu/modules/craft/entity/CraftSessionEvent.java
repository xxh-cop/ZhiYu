package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 创作会话行为事件实体（2026-11-05 心流度量配套）。
 *
 * <p>对应 {@code craft_session_event} 表（见 sql/schema.sql）。
 * 写入幂等：{@code (session_id, client_seq)} 唯一键去重；
 * 服务端派生事件 {@code clientSeq} 为 NULL，不参与去重。
 *
 * <p>{@code context} 为脱敏 JSON 快照（标量键值），禁止写入对话或自由文本；
 * {@code dwellSeconds} 独立成列，供「同类步骤停留 P95」统计避免 JSON_EXTRACT 跨库差异。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_session_event")
public class CraftSessionEvent extends BaseEntity {

    /** 所属会话 craft_session.id */
    private Long sessionId;

    /** 归属用户（服务端按会话填充，不信任客户端） */
    private Long userId;

    /** 所属项目（服务端按会话填充） */
    private Long craftProjectId;

    /** 事件类型，见 {@link com.zhiyu.modules.craft.enums.CraftSessionEventType} */
    private String eventType;

    /** 相关步骤序号（会话级事件为 NULL） */
    private Integer stepNo;

    /** 事件发生时刻（客户端上报或服务端接收） */
    private LocalDateTime occurredAt;

    /** 客户端序号（每会话单调），幂等去重；服务端派生事件为 NULL */
    private Long clientSeq;

    /** 脱敏上下文 JSON（标量键值） */
    private String context;

    /** 步骤停留秒数（step_leave 等填写，P95 统计用） */
    private Integer dwellSeconds;
}
