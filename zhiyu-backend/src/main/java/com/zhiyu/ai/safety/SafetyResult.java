package com.zhiyu.ai.safety;

import lombok.Builder;
import lombok.Data;

/**
 * 内容安全过滤结果（T1.3b）。
 *
 * <ul>
 *   <li>{@code highRisk} —— 是否高危（输入侧命中高危词库）</li>
 *   <li>{@code action} —— 过滤动作</li>
 *   <li>{@code reason} —— 命中原因（写入 ai_audit_log.reason）</li>
 *   <li>{@code rewrittenText} —— 仅 {@link SafetyAction#REWRITE} 时非 null，
 *       供调用方替换原文</li>
 *   <li>{@code careMessage} —— 仅 {@link SafetyAction#BLOCK} 时非 null，
 *       返回给用户的关怀话术（含热线号码）</li>
 * </ul>
 */
@Data
@Builder
public class SafetyResult {

    private boolean highRisk;
    private SafetyAction action;
    private String reason;

    /** REWRITE 时的安全替换文本。 */
    private String rewrittenText;

    /** BLOCK 时返回给用户的关怀话术。 */
    private String careMessage;

    public static SafetyResult pass(String reason) {
        return SafetyResult.builder()
                .highRisk(false)
                .action(SafetyAction.PASS)
                .reason(reason)
                .build();
    }

    public static SafetyResult block(String reason, String careMessage) {
        return SafetyResult.builder()
                .highRisk(true)
                .action(SafetyAction.BLOCK)
                .reason(reason)
                .careMessage(careMessage)
                .build();
    }

    public static SafetyResult rewrite(String reason, String rewrittenText) {
        return SafetyResult.builder()
                .highRisk(false)
                .action(SafetyAction.REWRITE)
                .reason(reason)
                .rewrittenText(rewrittenText)
                .build();
    }
}
