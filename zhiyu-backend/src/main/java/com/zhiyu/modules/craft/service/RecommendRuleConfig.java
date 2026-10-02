package com.zhiyu.modules.craft.service;

import java.util.List;
import java.util.Map;

/**
 * 推荐规则配置（T1.5）。
 *
 * <p>维护情绪 → 优先疗愈效果标签的映射关系。
 * 与 {@code ai-config/ai-capabilities.yaml} 的 {@code recommendation.rules} 同步。
 *
 * <p>规则层先行（{@code rule_first: true}），LLM 只负责生成理由，不决定选品。
 */
public final class RecommendRuleConfig {

    private RecommendRuleConfig() {
    }

    /**
     * 情绪 code → 优先疗愈效果标签列表。
     *
     * <p>与 ai-config/ai-capabilities.yaml 的 recommendation.rules 同步：
     * <pre>
     * anxious   → [静心, 专注, 安抚]
     * stressed  → [放空, 释放, 触觉安抚]
     * depressed → [成就感, 陪伴, 创造表达]
     * angry     → [物理宣泄, 释放, 触觉安抚]
     * tired     → [轻量成就, 安抚, 陪伴]
     * </pre>
     */
    private static final Map<String, List<String>> MOOD_TAGS = Map.of(
            "anxious", List.of("静心", "专注", "安抚"),
            "stressed", List.of("放空", "释放", "触觉安抚"),
            "depressed", List.of("成就感", "陪伴", "创造表达"),
            "angry", List.of("物理宣泄", "释放", "触觉安抚"),
            "tired", List.of("轻量成就", "安抚", "陪伴")
    );

    /**
     * 获取指定情绪的优先疗愈效果标签。
     *
     * @param mood 情绪 code（如 {@code anxious}）
     * @return 标签列表；未知情绪返回空列表
     */
    public static List<String> get(String mood) {
        if (mood == null) {
            return List.of();
        }
        return MOOD_TAGS.getOrDefault(mood, List.of());
    }
}
