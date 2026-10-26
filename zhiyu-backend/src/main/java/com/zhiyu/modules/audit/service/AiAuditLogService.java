package com.zhiyu.modules.audit.service;

/**
 * AI 审计日志服务（T1.3b）。
 *
 * <p>所有 AI 输入输出与过滤动作都通过此服务落库（AGENTS.md §4）。
 * 每条记录都必须携带 {@code userId}，便于按用户追溯与注销/审计核对。
 */
public interface AiAuditLogService {

    /**
     * 记录一条审计日志。
     *
     * <p>{@code rawInput} 与 {@code aiOutput} 在落库前用 {@link com.zhiyu.common.crypto.AesEncryptor}
     * 加密；{@code null} 保持 {@code null}。
     *
     * @param userId    关联用户 ID（落 user_id 列；迁移前历史行可能为 null）
     * @param scene     场景：chat / recommend / work_message
     * @param rawInput  原始输入明文（加密存储），可空
     * @param aiOutput  AI 输出明文（加密存储），可空
     * @param action    过滤动作：PASS / REVIEW / REWRITE / BLOCK
     * @param reason    命中原因
     */
    void log(Long userId, String scene, String rawInput, String aiOutput,
             String action, String reason);
}
