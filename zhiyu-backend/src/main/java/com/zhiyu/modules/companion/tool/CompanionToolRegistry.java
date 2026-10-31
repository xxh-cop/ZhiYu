package com.zhiyu.modules.companion.tool;

import com.zhiyu.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 工具白名单；只允许注册过的工具被调用，未知工具直接返回失败。 */
@Slf4j
@Component
public class CompanionToolRegistry {

    private final Map<String, CompanionTool> tools;

    public CompanionToolRegistry(List<CompanionTool> toolList) {
        Map<String, CompanionTool> registered = new LinkedHashMap<>();
        for (CompanionTool tool : toolList) {
            String name = tool == null ? null : tool.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("CompanionTool 名称不能为空");
            }
            if (registered.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("CompanionTool 重复注册: " + name);
            }
        }
        this.tools = Map.copyOf(registered);
    }

    public Set<String> names() {
        return tools.keySet();
    }

    public String description(String name) {
        CompanionTool tool = tools.get(name);
        return tool == null ? "" : tool.description();
    }

    public ToolResult execute(ToolContext context, String name, Map<String, Object> input) {
        if (context == null || context.userId() == null) {
            return ToolResult.failure("1002", "请先登录");
        }
        CompanionTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure("1001", "不支持的工具");
        }
        try {
            return tool.execute(context, input == null ? Map.of() : input);
        } catch (BusinessException e) {
            return ToolResult.failure(String.valueOf(e.getCode()), e.getMessage());
        } catch (Exception e) {
            log.warn("CompanionTool 执行失败: name={}, err={}", name, e.getMessage());
            return ToolResult.failure("5000", "工具暂时不可用");
        }
    }
}
