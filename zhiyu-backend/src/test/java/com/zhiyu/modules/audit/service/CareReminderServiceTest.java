package com.zhiyu.modules.audit.service;

import com.zhiyu.modules.audit.entity.CareReminder;
import com.zhiyu.modules.audit.mapper.CareReminderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CareReminderService} 集成单测（T1.3b）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CareReminderServiceTest {

    @Autowired
    private CareReminderService careReminderService;

    @Autowired
    private CareReminderMapper careReminderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("trigger 写入数据库，字段正确")
    void testTrigger_WriteFields() {
        careReminderService.trigger(6001L, 200L, 2, "在会话 #200 中表达了高危信号：自杀");

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM care_reminder WHERE user_id = 6001", Long.class);
        assertNotNull(id);
        CareReminder reminder = careReminderMapper.selectById(id);

        assertEquals(6001L, reminder.getUserId());
        assertEquals(200L, reminder.getSessionId());
        assertEquals(2, reminder.getRiskLevel());
        assertEquals(0, reminder.getStatus());
        assertEquals("在会话 #200 中表达了高危信号：自杀", reminder.getSummary());
    }

    @Test
    @DisplayName("summary 不含原始对话内容（脱敏验证）")
    void testTrigger_SummaryDesensitized() {
        String summary = "在会话 #201 中表达了高危信号：想死";
        careReminderService.trigger(6002L, 201L, 2, summary);

        String stored = jdbcTemplate.queryForObject(
                "SELECT summary FROM care_reminder WHERE user_id = 6002", String.class);
        assertEquals(summary, stored);
        // summary 不应包含任何原始对话长文
        assertFalse(stored.contains("我真的"), "summary 不应含原始对话内容");
    }

    @Test
    @DisplayName("sessionId 可为 null")
    void testTrigger_SessionIdNull() {
        careReminderService.trigger(6003L, null, 2, "在情绪对话中表达了高危信号：轻生");

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM care_reminder WHERE user_id = 6003", Long.class);
        CareReminder reminder = careReminderMapper.selectById(id);
        assertNull(reminder.getSessionId());
    }
}
