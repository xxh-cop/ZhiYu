package com.zhiyu.ai.flow;

/**
 * 心流检测派生信号（2026-11-05 心流度量修复）。
 *
 * <p>{@link FlowDetectionService} 每次评估返回本次心跳新产生的信号列表，
 * 由会话层映射为 {@code craft_session_event} 事件落库。
 *
 * <p>焦躁双信号分开记录、不合并：
 * <ul>
 *   <li>{@link #STEP_BACKTRACK} —— 步骤回退（stepNo 减小），既有口径；</li>
 *   <li>{@link #DWELL_STAGNATION} —— 同一步骤反复停留（dwell 不增反降计一次重新停留，
 *       次数超过阈值），AGENTS.md §7 文档口径，本批补齐。</li>
 * </ul>
 */
public enum FlowSignal {

    /** 步骤前进（stepNo 增大） */
    STEP_ADVANCE,

    /** 步骤回退（stepNo 减小；焦躁信号之一，既有口径） */
    STEP_BACKTRACK,

    /** 同一步骤反复停留超阈值（焦躁信号之二，本批补齐；与回退分开记录） */
    DWELL_STAGNATION,

    /** 呼吸引导触发（进入焦躁态的瞬间，每轮提醒只发一次） */
    BREATHING_PROMPT,

    /** 用户响应了呼吸引导（响应时仍有未完结的提醒才发） */
    BREATHING_RESPONDED
}
