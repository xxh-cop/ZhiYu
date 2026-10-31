package com.zhiyu.modules.companion.tool;

import com.zhiyu.modules.practice.service.AdaptivePracticeService;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 只读推荐：按可用时间、最近练习记录与已授权偏好选择 1–2 个原创教学纹样。
 * 不读取情绪原文，不根据情绪推断疗效，不写数据库。
 */
@Component
public class RecommendPracticeTool implements CompanionTool {

    private final AdaptivePracticeService adaptive;

    public RecommendPracticeTool(AdaptivePracticeService adaptive) {
        this.adaptive = adaptive;
    }

    @Override
    public String name() {
        return "recommendPractice";
    }

    @Override
    public String description() {
        return "按时间、最近完成情况与授权偏好推荐 1-2 个原创教学纹样；不评价情绪或疗效。";
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> input) {
        if (context == null || context.userId() == null) {
            return ToolResult.failure("1002", "请先登录");
        }
        Integer requested = null;
        if (input != null && input.containsKey("minutes")) {
            int minutes = integer(input.get("minutes"), 0);
            if (minutes < 1 || minutes > 180) {
                return ToolResult.failure("1001", "练习时间需在 1 到 180 分钟之间");
            }
            requested = minutes;
        }
        AdaptivePlanVO plan = adaptive.plan(context.userId(), requested);
        return ToolResult.success(plan);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
