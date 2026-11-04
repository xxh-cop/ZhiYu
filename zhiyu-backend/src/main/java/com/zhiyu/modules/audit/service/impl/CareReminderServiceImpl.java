package com.zhiyu.modules.audit.service.impl;

import com.zhiyu.modules.audit.entity.CareReminder;
import com.zhiyu.modules.audit.mapper.CareReminderMapper;
import com.zhiyu.modules.audit.service.CareReminderService;
import com.zhiyu.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link CareReminderService} 实现（T1.3b）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareReminderServiceImpl implements CareReminderService {

    private final CareReminderMapper careReminderMapper;
    private final NotificationService notificationService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public void trigger(Long userId, Long sessionId, Integer riskLevel, String summary) {
        CareReminder reminder = new CareReminder();
        reminder.setUserId(userId);
        reminder.setSessionId(sessionId);
        reminder.setRiskLevel(riskLevel);
        reminder.setSummary(summary);
        reminder.setStatus(0);
        careReminderMapper.insert(reminder);
        log.warn("触发关怀提醒: userId={}, sessionId={}, riskLevel={}, careReminderId={}",
                userId, sessionId, riskLevel, reminder.getId());

        // 通知系统（2026-11-04）：触达授权角色（咨询师 role=2 / 管理员 role=3）。
        // AQ3（2026-11-04）收窄：只发管理员 + 已认领该用户个案的咨询师——
        // 与危机日志可见范围对齐（最小权限；未认领咨询师在日志页本就看不到对应记录）。
        // 通知正文为固定引导语，不含学生身份与会话内容（脱敏口径同 care_reminder.summary 的既有例外）。
        // 通知失败不阻断关怀主流程。
        try {
            List<Long> recipients = jdbc.queryForList(
                    "SELECT id FROM user WHERE role=3 AND status=1 AND deleted=0"
                            + " UNION SELECT c.owner_id FROM care_case c"
                            + " JOIN care_reminder r ON r.id=c.reminder_id"
                            + " WHERE r.user_id=? AND c.owner_id IS NOT NULL AND c.deleted=0",
                    Long.class, userId);
            for (Long recipientId : recipients) {
                notificationService.notifyCareReminder(recipientId, reminder.getId());
            }
        } catch (Exception e) {
            log.warn("关怀提醒通知写入失败（不阻断关怀流程）: careReminderId={}, err={}", reminder.getId(), e.getMessage());
        }
    }
}
