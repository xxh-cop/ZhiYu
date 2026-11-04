package com.zhiyu.modules.emotion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 情绪对话请求 DTO（T1.3a）。
 *
 * <p>{@code sessionId} 为 null 时新建会话；非 null 时追加到已有会话。
 */
@Data
public class ChatDTO {

    /** 会话 ID，null 时新建会话 */
    private Long sessionId;

    /** 触发情绪（可选，如「焦虑」） */
    private String mood;

    /** 用户消息内容，不能为空 */
    @NotBlank(message = "content 不能为空")
    private String content;

    /**
     * 协议模式：传 {@code "live"} 启用句级真流式（chunk/revoke/done 事件）；
     * 缺省保持旧版整段单事件格式（兼容期一个迭代周期，见 openapi.yaml）。
     */
    private String mode;
}
