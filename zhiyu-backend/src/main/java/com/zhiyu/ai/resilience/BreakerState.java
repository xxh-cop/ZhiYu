package com.zhiyu.ai.resilience;

/**
 * 熔断器三态（AGENTS.md §4：连续失败 5 次 → 熔断 60 秒 → 半开试探 1 次）。
 *
 * <ul>
 *   <li>{@link #CLOSED}    —— 正常，允许请求通过</li>
 *   <li>{@link #OPEN}      —— 熔断中，直接走 fallback</li>
 *   <li>{@link #HALF_OPEN} —— 半开，允许一次试探请求</li>
 * </ul>
 */
public enum BreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
