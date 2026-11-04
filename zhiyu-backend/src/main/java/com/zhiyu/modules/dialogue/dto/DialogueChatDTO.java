package com.zhiyu.modules.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 统一对话入口请求（C1，/api/dialogue/chat）。雪花 ID 按字符串传递（AGENTS §5.1）。
 */
public record DialogueChatDTO(
        @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "会话 ID 格式非法")
        String sessionId,
        @NotBlank(message = "内容不能为空")
        @Size(max = 2000, message = "内容长度不能超过 2000 字")
        String text,
        @Pattern(regexp = "^(LISTENING_PAGE|PRACTICE_PAGE|KNOWLEDGE_PAGE)$", message = "入口上下文非法")
        String entryContext,
        @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "练习会话 ID 格式非法")
        String practiceSessionId,
        @Size(max = 50, message = "意图提示过长")
        String intentHint,
        @Size(max = 50, message = "情绪标记过长")
        String mood) {
}
