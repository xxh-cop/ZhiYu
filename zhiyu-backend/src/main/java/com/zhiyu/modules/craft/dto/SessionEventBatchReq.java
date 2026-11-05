package com.zhiyu.modules.craft.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 会话行为事件批量上报请求（2026-11-05 心流度量配套）。
 *
 * <p>对应 OpenAPI schema {@code SessionEventBatchReq}。
 * 批量上限 100 条，超出整体 1001。
 */
@Data
public class SessionEventBatchReq {

    @NotNull(message = "事件列表不能为空")
    @Size(min = 1, max = 100, message = "事件列表须为 1-100 条")
    @Valid
    private List<SessionEventItem> events;
}
