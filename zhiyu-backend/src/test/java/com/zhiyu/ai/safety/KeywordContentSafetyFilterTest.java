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

    @Autowired
    private HighRiskLexicon lexicon;

    @Test
    @DisplayName("普通输入 → PASS + 写审计")
    void testCheckInput_Normal_Pass() {
        SafetyResult result = safetyFilter.checkInput(5001L, 100L, "chat", "最近考试压力大");

        assertEquals(SafetyAction.PASS, result.getAction());
        assertFalse(result.isHighRisk());

        // 应写一条 PASS 审计，并带 userId
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE action = 'PASS'", Long.class);
        assertTrue(count >= 1, "应有 PASS 审计记录");
        Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM ai_audit_log WHERE scene = 'chat' AND reason = '输入检查通过'",
                Long.class);
        assertEquals(5001L, storedUserId, "输入审计必须带 userId");
    }

    @Test
    @DisplayName("高危输入 → BLOCK + 写 care_reminder + 写审计")
    void testCheckInput_HighRisk_Block() {
        SafetyResult result = safetyFilter.checkInput(5002L, 101L, "chat", "我不想活了，我想自杀");

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
        SafetyResult result = safetyFilter.checkOutput(5001L, "chat", "你可能患有抑郁症，建议就医");

        assertEquals(SafetyAction.REWRITE, result.getAction());
        assertNotNull(result.getRewrittenText());
        assertFalse(result.getRewrittenText().contains("抑郁症"));
    }

    @Test
    @DisplayName("输出正常 → PASS")
    void testCheckOutput_Normal_Pass() {
        SafetyResult result = safetyFilter.checkOutput(5001L, "chat", "我在这里陪你，慢慢说");

        assertEquals(SafetyAction.PASS, result.getAction());
    }

    @Test
    @DisplayName("空输入 → PASS，不写审计")
    void testCheckInput_Empty_NoAudit() {
        long auditBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log", Long.class);

        SafetyResult result = safetyFilter.checkInput(5001L, 100L, "chat", "");

        assertEquals(SafetyAction.PASS, result.getAction());
        long auditAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log", Long.class);
        assertEquals(auditBefore, auditAfter, "空输入不应写审计");
    }

    @Test
    @DisplayName("careMessage 含热线号码")
    void testCareMessage_ContainsHotline() {
        SafetyResult result = safetyFilter.checkInput(5003L, 102L, "chat", "想死");

        assertEquals(SafetyAction.BLOCK, result.getAction());
        String care = result.getCareMessage();
        assertNotNull(care);
        // 默认热线号码（test profile 未覆盖环境变量，用 application.yml 默认值）
        assertTrue(care.contains("心理中心") || care.contains("热线"),
                "关怀话术应含热线信息: " + care);
    }

    @Test
    @DisplayName("scene 传播：checkOutput(userId, 'recommend', text) → ai_audit_log.scene='recommend'")
    void testScenePropagation() {
        safetyFilter.checkOutput(5001L, "recommend", "这段推荐理由很温暖");

        // 查 ai_audit_log 中 scene='recommend' 的记录
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE scene = 'recommend'", Long.class);
        assertEquals(1, count, "应有一条 scene='recommend' 的审计记录");
    }

    // ── AU1（2026-11-04）：CARE_RISK 三档对齐 ──

    @Test
    @DisplayName("AU1：流式句级命中按词表真实分级（与 checkInput 同口径，不再硬编码 2）")
    void scanStreamSentenceUsesLexiconLevel() {
        SafetyResult urgent = safetyFilter.scanStreamSentence(5100L, 900L, "他提到割腕的细节");
        assertEquals(SafetyAction.BLOCK, urgent.getAction());
        Long lv3 = jdbcTemplate.queryForObject(
                "SELECT risk_level FROM care_reminder WHERE user_id=5100", Long.class);
        assertEquals(3L, lv3, "计划/行为组应为 3 紧急");

        SafetyResult watch = safetyFilter.scanStreamSentence(5101L, 901L, "觉得活着没意思");
        assertEquals(SafetyAction.BLOCK, watch.getAction());
        Long lv1 = jdbcTemplate.queryForObject(
                "SELECT risk_level FROM care_reminder WHERE user_id=5101", Long.class);
        assertEquals(1L, lv1, "绝望组应为 1 关注");
    }

    @Test
    @DisplayName("AU1：字典三档齐备且全部关键词等级落在 1/2/3（全词表校验）")
    void lexiconThreeGradesExistAndInRange() throws Exception {
        var res = new org.springframework.core.io.ClassPathResource("lexicon/high-risk.txt");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int current = -1;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                res.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.startsWith("#")) {
                    var m = java.util.regex.Pattern.compile("@level:(\\d)").matcher(t);
                    if (m.find()) {
                        current = Integer.parseInt(m.group(1));
                    }
                    continue;
                }
                assertTrue(current >= 1, "关键词「" + t + "」前缺少 @level 组标记");
                Integer lv = lexicon.levelOf(t);
                assertNotNull(lv, "词表内关键词应能定级: " + t);
                assertEquals(current, lv.intValue(), "关键词「" + t + "」等级应与组标记一致");
                assertTrue(lv >= 1 && lv <= 3, "等级必须落在 1/2/3: " + t);
                seen.add(lv);
            }
        }
        assertEquals(java.util.Set.of(1, 2, 3), seen, "字典必须三档齐备（1关注 2高危 3紧急）");
    }

    @Test
    @DisplayName("AU1：词表缺分级标记时不崩——未知等级保守取 3 紧急（checkInput 与流式同口径）")
    void missingLevelMarkerFallsBackWithoutCrash() {
        HighRiskLexicon lex = org.mockito.Mockito.mock(HighRiskLexicon.class);
        org.mockito.Mockito.when(lex.containsRisk(org.mockito.Mockito.anyString())).thenReturn(true);
        org.mockito.Mockito.when(lex.firstMatch(org.mockito.Mockito.anyString())).thenReturn("未分级词");
        org.mockito.Mockito.when(lex.levelOf(org.mockito.Mockito.anyString())).thenReturn(null);
        com.zhiyu.modules.audit.service.CareReminderService care =
                org.mockito.Mockito.mock(com.zhiyu.modules.audit.service.CareReminderService.class);
        KeywordContentSafetyFilter filter = new KeywordContentSafetyFilter(lex, care,
                org.mockito.Mockito.mock(com.zhiyu.modules.audit.service.AiAuditLogService.class),
                org.mockito.Mockito.mock(com.zhiyu.modules.crisis.CrisisSessionLogService.class));

        SafetyResult in = filter.checkInput(9L, 1L, "chat", "包含未分级词的输入");
        assertEquals(SafetyAction.BLOCK, in.getAction());
        org.mockito.Mockito.verify(care).trigger(org.mockito.Mockito.eq(9L), org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.eq(3), org.mockito.Mockito.anyString());

        SafetyResult stream = filter.scanStreamSentence(9L, 1L, "未分级词");
        assertEquals(SafetyAction.BLOCK, stream.getAction());
        org.mockito.Mockito.verify(care, org.mockito.Mockito.times(2)).trigger(
                org.mockito.Mockito.eq(9L), org.mockito.Mockito.eq(1L),
                org.mockito.Mockito.eq(3), org.mockito.Mockito.anyString());
    }
}
