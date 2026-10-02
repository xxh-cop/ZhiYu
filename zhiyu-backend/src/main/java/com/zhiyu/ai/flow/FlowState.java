package com.zhiyu.ai.flow;

/**
 * 心流状态枚举（T1.7）。
 *
 * <ul>
 *   <li>{@link #FLOW} —— 心流：连续 5 分钟无步骤切换且未主动退出，减少打扰</li>
 *   <li>{@link #ANXIOUS} —— 焦躁：步骤回退 > 3 次且未响应呼吸提醒，需呼吸引导</li>
 *   <li>{@link #NORMAL} —— 正常：不满足上述任一条件</li>
 * </ul>
 */
public enum FlowState {
    FLOW,
    ANXIOUS,
    NORMAL
}
