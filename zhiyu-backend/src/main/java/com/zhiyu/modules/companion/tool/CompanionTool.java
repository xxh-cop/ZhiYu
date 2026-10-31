package com.zhiyu.modules.companion.tool;

/** 小织可调用的受控工具；所有实现必须自己校验用户与输入，不允许任意 SQL/Shell。 */
public interface CompanionTool {

    String name();

    String description();

    ToolResult execute(ToolContext context, java.util.Map<String, Object> input);
}
