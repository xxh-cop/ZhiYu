package com.zhiyu.modules.companion.tool;

/** 工具结果；失败只返回归一化错误码，不把底层异常或用户原文透给模型/前端。 */
public record ToolResult(boolean success, Object data, String code, String message) {

    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, null);
    }

    public static ToolResult failure(String code, String message) {
        return new ToolResult(false, null, code, message);
    }
}
