package com.zhiyu.modules.companion.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 固定 4 秒吸 / 6 秒呼；不声称治疗效果，不写数据库。 */
@Component
public class SuggestBreathingTool implements CompanionTool {

    @Override
    public String name() {
        return "suggestBreathing";
    }

    @Override
    public String description() {
        return "提供 4 秒吸气 / 6 秒呼气的短呼吸引导；可随时停止。";
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> input) {
        if (context == null || context.userId() == null) {
            return ToolResult.failure("1002", "请先登录");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inhaleSeconds", 4);
        data.put("exhaleSeconds", 6);
        data.put("cycles", 4);
        data.put("instruction", "吸气 4 秒，呼气 6 秒，做 4 轮；不舒服就停下来，回到自然呼吸。");
        data.put("disclaimer", "本系统不能替代专业心理咨询。");
        return ToolResult.success(data);
    }
}
