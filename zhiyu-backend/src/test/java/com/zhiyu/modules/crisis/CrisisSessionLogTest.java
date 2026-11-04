package com.zhiyu.modules.crisis;

import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 危机路径留存（AJ，2026-11-04）。
 *
 * <p>覆盖：命中危机才记录 / 库内无原文（含加密落库验证）/ 规则摘要不经 LLM 不阻断 /
 * 脱敏规则 / 授权可见性（role 2/3 + 认领范围）/ 学生与未授权角色不得访问。
 *
 * <p>注意：记录是旁路异步（独立 daemon 线程直接写库、自动提交），故本类<b>不加
 * {@code @Transactional}</b>——断言用唯一用户 id 隔离 + 轮询等待异步落库。
 */
@SpringBootTest
@ActiveProfiles("test")
class CrisisSessionLogTest {

    @Autowired
    private ContentSafetyFilter safetyFilter;
    @Autowired
    private CrisisSessionLogService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AesEncryptor crypto;
    @Autowired
    private com.zhiyu.ai.safety.HighRiskLexicon lexicon;

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private long newUser(String tag, int role) {
        long id = System.nanoTime() % 1_000_000_000L + 700_000_000L;
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?, ?,1,1,0,0)", id, "aj_" + tag + "_" + id, "AJ用户", role);
        return id;
    }

    private Map<String, Object> awaitRow(long userId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var rows = jdbc.queryForList(
                    "SELECT * FROM crisis_session_log WHERE user_id=? AND deleted=0", userId);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    @Test
    @DisplayName("命中危机才记录；摘要是概括非摘抄；库内无原文且密文不落明文")
    void crisisInputRecordsDesensitizedSummaryOnly() throws Exception {
        long uid = newUser("hit", 0);
        // 含 PII 的危机输入：姓名 + 手机号 + 非关键词叙述
        String pii = "我叫张三，电话13800001111，住在幸福路23号，我不想活了";
        var result = safetyFilter.checkInput(uid, null, "chat", pii);
        assertEquals(com.zhiyu.ai.safety.SafetyAction.BLOCK, result.getAction());

        Map<String, Object> row = awaitRow(uid, 5000);
        assertTrue(row != null, "危机输入应旁路落 crisis_session_log");
        String cipher = (String) row.get("summary_cipher");
        // 库内是密文：直接读不含任何明文片段
        assertFalse(cipher.contains("张三"), "密文列不得含姓名明文");
        assertFalse(cipher.contains("13800001111"), "密文列不得含手机号明文");
        assertFalse(cipher.contains("我不想活了"), "密文列不得含任何明文（含命中词）");
        assertEquals(2, ((Number) row.get("risk_level")).intValue());
        assertEquals("chat", row.get("scene"));

        // 解密后是概括：含命中词（平台词表成员，作为危机类型）+ 场景 + 长度档；不含 PII 与叙述原文
        String summary = crypto.decrypt(cipher);
        assertTrue(summary.contains("危机关键词"), "摘要应说明命中危机词: " + summary);
        assertTrue(summary.contains("chat"), "摘要应含场景: " + summary);
        assertFalse(summary.contains("张三"), "摘要不得含姓名: " + summary);
        assertFalse(summary.contains("13800001111"), "摘要不得含手机号: " + summary);
        assertFalse(summary.contains("幸福路"), "摘要不得含住址: " + summary);
        assertFalse(summary.contains("我叫"), "摘要不得含叙述原文: " + summary);
    }

    @Test
    @DisplayName("非危机输入不产生记录")
    void normalInputProducesNoRecord() throws Exception {
        long uid = newUser("normal", 0);
        var result = safetyFilter.checkInput(uid, null, "chat", "今天天气不错");
        assertEquals(com.zhiyu.ai.safety.SafetyAction.PASS, result.getAction());
        assertEquals(null, awaitRow(uid, 800), "非危机输入不得落 crisis_session_log");
    }

    @Test
    @DisplayName("规则摘要不经 LLM 且记录不阻塞调用方（慢 DB 桩下 recordAsync 立即返回）")
    void recordAsyncDoesNotBlockCaller() {
        // 慢 2s 的 JdbcTemplate 桩：recordAsync 必须立即返回（危机响应不被阻塞）
        JdbcTemplate slow = org.mockito.Mockito.mock(JdbcTemplate.class);
        org.mockito.Mockito.when(slow.update(org.mockito.Mockito.anyString(),
                        org.mockito.Mockito.<Object[]>any()))
                .thenAnswer(inv -> { Thread.sleep(2000); return 1; });
        CrisisSessionLogService stubbed = new CrisisSessionLogService(
                slow, null, crypto, null);
        long start = System.currentTimeMillis();
        stubbed.recordAsync(1L, 2L, "chat", "自残", 10, 2);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 500, "recordAsync 必须异步（实际耗时 " + elapsed + "ms）");
    }

    @Test
    @DisplayName("脱敏规则：手机号/身份证/姓名线索/学校班级/住址全部占位符化")
    void desensitizerRules() {
        String out = CrisisDesensitizer.sanitize(
                "我叫张三，身份证 360102199901011234，手机 13812345678，在江西师范大学 3 班，住北京路 12 栋 2 单元 301 室");
        for (String raw : List.of("张三", "360102199901011234", "13812345678", "江西师范大学", "北京路", "301")) {
            assertFalse(out.contains(raw), "脱敏后不得含「" + raw + "」: " + out);
        }
        for (String placeholder : List.of("[姓名]", "[身份证号]", "[手机号]", "[学校]", "[路名]")) {
            assertTrue(out.contains(placeholder), "应含占位符 " + placeholder + ": " + out);
        }
    }

    @Test
    @DisplayName("学生角色访问被拒（1003）；管理员可见；咨询师仅见认领个案用户")
    void visibilityScoping() {
        long student = newUser("s", 0);
        long admin = newUser("a", 3);
        long consultant = newUser("c", 2);
        long target = newUser("t", 0);
        // 造一条目标用户的留存记录（同步写）
        service.recordSafely(target, 42L, "chat", "自残", 15, 2);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM crisis_session_log WHERE user_id=?",
                Integer.class, target) > 0);

        // 学生 → 1003（BusinessAccess.role 拒绝，HTTP 层映射 403）
        UserContext.set(new LoginUser(student, 0));
        assertThrows(BusinessException.class, () -> service.list(1));

        // 咨询师未认领 → 列表为空（叠加关怀范围过滤）
        UserContext.set(new LoginUser(consultant, 2));
        assertEquals(0, service.list(1).get("total"));

        // 咨询师认领该用户的关怀个案后 → 可见
        long reminderId = System.nanoTime() % 1_000_000_000L + 800_000_000L;
        jdbc.update("INSERT INTO care_reminder(id,user_id,risk_level,summary,status,deleted) VALUES(?,?,2,'脱敏',0,0)",
                reminderId, target);
        jdbc.update("INSERT INTO care_case(id,reminder_id,owner_id,state,create_time,update_time,deleted)"
                + " VALUES(?,?,?,'OPEN',NOW(),NOW(),0)", reminderId + 1, reminderId, consultant);
        assertEquals(1, service.list(1).get("total"));

        // 管理员看全部
        UserContext.set(new LoginUser(admin, 3));
        assertTrue(((Number) service.list(1).get("total")).intValue() >= 1);

        // 详情：返回脱敏摘要 + 落查看审计
        Long logId = jdbc.queryForObject("SELECT id FROM crisis_session_log WHERE user_id=?",
                Long.class, target);
        Map<String, Object> detail = service.detail(logId);
        assertTrue(String.valueOf(detail.get("summary")).contains("危机关键词"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE action='crisis_log.view' AND target_id=?",
                Integer.class, logId) > 0, "查看详情必须落操作审计");

        // 学生查详情 → 1003
        UserContext.set(new LoginUser(student, 0));
        assertThrows(BusinessException.class, () -> service.detail(logId));
    }

    @Test
    @DisplayName("AQ1：列表与详情统一 camelCase；详情含用户标识（userId 字符串）")
    void fieldStyleConsistentAndDetailHasUser() {
        long admin = newUser("fa", 3);
        long target = newUser("ft", 0);
        service.recordSafely(target, 42L, "chat", "自残", 15, 2);
        UserContext.set(new LoginUser(admin, 3));

        var list = service.list(1);
        @SuppressWarnings("unchecked")
        var records = (java.util.List<Map<String, Object>>) list.get("records");
        assertFalse(records.isEmpty());
        var first = records.get(0);
        assertTrue(first.containsKey("userId") && first.containsKey("riskLevel") && first.containsKey("createTime"),
                "列表必须 camelCase: " + first.keySet());
        assertFalse(first.containsKey("user_id"), "列表不得出现 snake_case");

        Long logId = jdbc.queryForObject("SELECT id FROM crisis_session_log WHERE user_id=?",
                Long.class, target);
        Map<String, Object> detail = service.detail(logId);
        assertEquals(String.valueOf(target), detail.get("userId"), "详情必须含用户标识");
    }

    @Test
    @DisplayName("AQ2：词表分级——计划/行为 3、自伤意念 2、绝望 1、词表外 null")
    void lexiconLevels() {
        assertEquals(3, lexicon.levelOf("割腕"));
        assertEquals(2, lexicon.levelOf("想死"));
        assertEquals(1, lexicon.levelOf("活着没意思"));
        org.junit.jupiter.api.Assertions.assertNull(lexicon.levelOf("词表外词汇"));
    }

    @Test
    @DisplayName("AQ2：危机留存落库真实分级（割腕→3 紧急）")
    void crisisRecordCarriesRealLevel() throws Exception {
        long uid = newUser("lv", 0);
        safetyFilter.checkInput(uid, null, "chat", "我写了遗书，买了安眠药");
        Map<String, Object> row = awaitRow(uid, 5000);
        assertTrue(row != null, "危机输入应落留存");
        assertEquals(3, ((Number) row.get("risk_level")).intValue(), "计划/行为组应为 3 紧急");
    }
}
