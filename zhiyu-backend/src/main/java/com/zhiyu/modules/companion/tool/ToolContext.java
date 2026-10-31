package com.zhiyu.modules.companion.tool;

/** 工具调用上下文；practiceSessionId 可为空，但 userId 必须来自已认证请求。 */
public record ToolContext(Long userId, Long practiceSessionId) {
}
