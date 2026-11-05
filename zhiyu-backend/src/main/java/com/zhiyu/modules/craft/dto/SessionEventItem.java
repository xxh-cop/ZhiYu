package com.zhiyu.modules.craft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Map;

/**
 * 会话行为事件条目（2026-11-05 心流度量配套）。
 *
 * <p>对应 OpenAPI schema {@code SessionEventItem}。
 * {@code context} 只允许标量键值（数字/布尔/短字符串），由服务端再做脱敏校验，
 * 禁止上报对话或自由文本。
 */
@Data
public class SessionEventItem {

    /** 事件类型（仅允许客户端白名单类型，见 CraftSessionEventType.CLIENT_ALLOWED） */
    @NotBlank(message = "事件类型不能为空")
    private String eventType;

    /** 相关步骤序号（会话级事件可空） */
    private Integer stepNo;

    /**
     * 事件发生时刻（RFC3339 / ISO-8601 字符串，如 2026-11-05T10:20:30.123Z
     * 或 2026-11-05T10:20:30；服务端宽容解析后按服务时区落库，允许 5 分钟时钟偏移）
     */
    @NotBlank(message = "发生时刻不能为空")
    private String occurredAt;

    /** 客户端序号（每会话单调递增），幂等去重键 */
    @NotNull(message = "客户端序号不能为空")
    @Positive(message = "客户端序号必须为正数")
    private Long clientSeq;

    /** 脱敏上下文（标量键值，可空） */
    private Map<String, Object> context;
}
