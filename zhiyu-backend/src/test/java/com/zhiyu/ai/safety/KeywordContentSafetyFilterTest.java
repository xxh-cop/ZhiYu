package com.zhiyu.ai.safety;

import com.zhiyu.modules.audit.entity.AiAuditLog;
import com.zhiyu.modules.audit.entity.CareReminder;
import com.zhiyu.modules.audit.mapper.AiAuditLogMapper;
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
 * {@link KeywordContentSafetyFilter} 集成单测（T1.3b）。
 *
 * <p>用 H2 + @SpringBootTest 验证：输入侧高危命中 → BLOCK + 写 care_reminder + 写 ai_audit_log；
 * 输出侧越界 → REWRITE；空输入 → PASS 不写审计。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KeywordContentSafetyFilterTest {

    @Autowired
    private ContentSafetyFilter safetyFilter;

    @Autowired
    private CareReminderMapper careReminderMapper;

    @Autowired
    private AiAuditLogMapper aiAuditLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("普通输入 → PASS + 写审计")
    void testCheckInput_Normal_Pass() {
        SafetyResult result = safetyFilter.checkInput(5001L, 100L, "最近考试压力大");

        assertEquals(SafetyAction.PASS, result.getAction());
        assertFalse(result.isHighRisk());

        // 应写一条 PASS 审计
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE action = 'PASS'", Long.class);
        assertTrue(count >= 1, "应有 PASS 审计记录");
    }

    @Test
    @DisplayName("高危输入 → BLOCK + 写 care_reminder + 写审计")
    void testCheckInput_HighRisk_Block() {
        SafetyResult result = safetyFilter.checkInput(5002L, 101L, "我不想活了，我想自杀");

        assertEquals(SafetyAction.BLOCK, result.getAction());
        assertTrue(result.isHighRisk());
        assertNotNull(result.getCareMessage());

        // care_reminder 应有高危记录
        long careCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_reminder WHERE user_id = ? AND risk_level = 2",
                Long.class, 5002L);
        assertEquals(1, careCount);

        // ai_audit_log 应有 BLOCK 记录
        long blockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE action = 'BLOCK'", Long.class);
        assertTrue(blockCount >= 1);
    }

    @Test
    @DisplayName("输出含\"抑郁症\" → REWRITE")
    void testCheckOutput_Boundary_Rewrite() {
        SafetyResult result = safetyFilter.checkOutput(5001L, "你可能患有抑郁症，建议就医");

        assertEquals(SafetyAction.REWRITE, result.getAction());
        assertNotNull(result.getRewrittenText());
        assertFalse(result.getRewrittenText().contains("抑郁症"));
    }

    @Test
    @DisplayName("输出正常 → PASS")
    void testCheckOutput_Normal_Pass() {
        SafetyResult result = safetyFilter.checkOutput(5001L, "我在这里陪你，慢慢说");

        assertEquals(SafetyAction.PASS, result.getAction());
    }

    @Test
    @DisplayName("空输入 → PASS，不写审计")
    void testCheckInput_Empty_NoAudit() {
        long auditBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log", Long.class);

        SafetyResult result = safetyFilter.checkInput(5001L, 100L, "");

        assertEquals(SafetyAction.PASS, result.getAction());
        long auditAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log", Long.class);
        assertEquals(auditBefore, auditAfter, "空输入不应写审计");
    }

    @Test
    @DisplayName("careMessage 含热线号码")
    void testCareMessage_ContainsHotline() {
        SafetyResult result = safetyFilter.checkInput(5003L, 102L, "想死");

        assertEquals(SafetyAction.BLOCK, result.getAction());
        String care = result.getCareMessage();
        assertNotNull(care);
        // 默认热线号码（test profile 未覆盖环境变量，用 application.yml 默认值）
        assertTrue(care.contains("心理中心") || care.contains("热线"),
                "关怀话术应含热线信息: " + care);
    }
}
