package com.zhiyu.ai.safety;

/**
 * 内容安全过滤门面接口（T1.3b / T1.5）。
 *
 * <p>所有 AI 输入输出都必须经过此过滤器（AGENTS.md §4）。
 */
public interface ContentSafetyFilter {

    /**
     * 输入侧检查（用户 → AI）。
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 ID（可空，新建会话时尚未生成）
     * @param scene     审计场景，如 {@code "chat"} / {@code "recommend"} / {@code "message"}
     * @param text      用户输入文本
     * @return 过滤结果；命中高危词返回 {@link SafetyAction#BLOCK}
     */
    SafetyResult checkInput(Long userId, Long sessionId, String scene, String text);

    /**
     * 输出侧检查（AI → 用户）。
     *
     * <p>用于同步接口（recommendReason / workMessage）的返回前拦截改写。
     * 流式接口（streamChat）不在此拦截，改由 Prompt 约束 + 事后审计。
     *
     * @param userId 当前用户 ID
     * @param scene  审计场景，如 {@code "chat"} / {@code "recommend"} / {@code "message"}
     * @param text   AI 输出文本
     * @return 过滤结果；越界返回 {@link SafetyAction#REWRITE}
     */
    SafetyResult checkOutput(Long userId, String scene, String text);
}
