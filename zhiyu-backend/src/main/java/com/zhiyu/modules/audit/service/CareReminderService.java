package com.zhiyu.modules.audit.service;

/**
 * 关怀提醒服务（T1.3b）。
 *
 * <p>当内容安全过滤器命中高危信号时调用 {@link #trigger} 写入 care_reminder。
 */
public interface CareReminderService {

    /**
     * 触发一条关怀提醒。
     *
     * @param userId     用户 ID
     * @param sessionId  会话 ID（可空）
     * @param riskLevel  风险等级：1 一般 / 2 高危
     * @param summary    脱敏摘要（不含原始对话原文）
     */
    void trigger(Long userId, Long sessionId, Integer riskLevel, String summary);
}
