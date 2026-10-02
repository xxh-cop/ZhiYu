package com.zhiyu.ai.safety;

/**
 * 内容安全过滤动作（T1.3b）。
 *
 * <ul>
 *   <li>{@link #PASS} —— 放行</li>
 *   <li>{@link #REVIEW} —— 标记复核（本期未用，留 hook）</li>
 *   <li>{@link #REWRITE} —— 改写（输出侧越界时替换为安全文本）</li>
 *   <li>{@link #BLOCK} —— 阻断（输入侧高危，不调 LLM，直接走危机干预）</li>
 * </ul>
 */
public enum SafetyAction {
    PASS,
    REVIEW,
    REWRITE,
    BLOCK
}
