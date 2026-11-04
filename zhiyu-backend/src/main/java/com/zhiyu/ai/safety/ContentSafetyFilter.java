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
     * <p>同步接口（recommendReason / workMessage / knowledgeAnswer 等）在返回前调用。
     * 流式接口 {@code AiClient.streamChat} 在完整回复生成后调用本方法做终检；
     * 由于 token 已经流出，无法逐 token 改写，命中 {@link SafetyAction#REWRITE}
     * 时由门面用安全文本替换<b>整段</b>回复，调用方不得把原始 token 直接推给客户端。
     * 两侧检查都会写 ai_audit_log（带 userId）。
     *
     * @param userId 当前用户 ID
     * @param scene  审计场景，如 {@code "chat"} / {@code "recommend"} / {@code "message"}
     * @param text   AI 输出文本
     * @return 过滤结果；越界返回 {@link SafetyAction#REWRITE}
     */
    SafetyResult checkOutput(Long userId, String scene, String text);

    /**
     * 句级流式安全检（SSE A 方案）。
     *
     * <p>与 {@link #checkOutput} 的区别：<b>不写审计日志</b>——
     * 审计由流结束后的终检统一写一条（避免每句一条撑爆 ai_audit_log）。
     * 命中高危词库时仍会触发 care_reminder（危机干预不因流式化被绕过），
     * 返回 {@link SafetyAction#BLOCK} 供调用方立即中断生成；
     * 命中越界词库返回 {@link SafetyAction#REWRITE}，调用方应中断并走终检撤回。
     *
     * @param userId    当前用户
     * @param sessionId 会话 ID（关怀提醒关联用）
     * @param text      单个句子
     */
    SafetyResult scanStreamSentence(Long userId, Long sessionId, String text);
}
