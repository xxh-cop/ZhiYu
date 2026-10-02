package com.zhiyu.modules.audit.service.impl;

import com.zhiyu.modules.audit.entity.CareReminder;
import com.zhiyu.modules.audit.mapper.CareReminderMapper;
import com.zhiyu.modules.audit.service.CareReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link CareReminderService} 实现（T1.3b）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareReminderServiceImpl implements CareReminderService {

    private final CareReminderMapper careReminderMapper;

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
    }
}
