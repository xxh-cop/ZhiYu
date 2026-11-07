package com.zhiyu.modules.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.admin.service.ModerationShadowService;
import com.zhiyu.modules.auth.service.AccountLifecycle;
import com.zhiyu.support.AuthenticatedTestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 软档影子模式集成测试（2026-11-07，场景对应 features/74-soft-tier-shadow.feature）。
 *
 * <p>覆盖：happy path（只落观测台账、线上零差异）、AI 不可用重试至上限落 UNAVAILABLE 终态、
 * 日预算截断、统计口径（主指标/保守下界/覆盖率降级/无害率/分层）、开关关闭空转、注销级联。
 *
 * <p>{@code @MockBean} 替换 {@link AiClient} 控制判定路径；类级 max-attempts=2 便于
 * 两轮即达上限；daily-budget/enabled 需在单测内临时改的值用 ReflectionTestUtils + finally 恢复。
 */
@SpringBootTest(properties = {
        "zhiyu.moderation.soft-tier.shadow.enabled=true",
        "zhiyu.moderation.soft-tier.shadow.max-attempts=2",
        "zhiyu.moderation.soft-tier.shadow.batch-size=10",
        "zhiyu.moderation.soft-tier.shadow.lookback-hours=72",
        "zhiyu.moderation.soft-tier.shadow.daily-budget=2000",
        "zhiyu.moderation.soft-tier.shadow.coverage-warn-threshold=0.8"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class ModerationShadowServiceTest {

    @Autowired private ModerationShadowService shadowService;
    @Autowired private AccountLifecycle lifecycle;
    @Autowired private JdbcTemplate db;
    @MockBean private AiClient aiClient;

    private static final long AUTHOR = 887001L;
    private static final long VICTIM = 887002L;

    /**
     * 统计窗口锚定日（2026-11-08 跨午夜抖动修复）：本类所有送审/判定时间与 stats 查询区间
     * 都从它派生，两侧同源即与墙钟解耦。此前数据时间取 {@code now().minusHours(h)}、窗口取
     * {@code LocalDate.now()}——凌晨（00:00–09:00）运行时数据落回昨天，被 stats 的自然日窗口
     * （target_create_time ∈ [from, to]）排除，统计计数全为 0（4 个用例 00:55 实证失败）。
     */
    private static final LocalDate ANCHOR_DAY = LocalDate.now();
    /**
     * 当日正午：距两个日界各 12h，本类最大回拨 9h 保证数据不跨日。
     * scan 回扫仅有 72h 下界、无上界（ModerationShadowService.processNewTargets），
     * 凌晨运行时锚点相对真实时刻是未来时间也同样被扫入，不受影响。
     */
    private static final LocalDateTime ANCHOR_NOON = ANCHOR_DAY.atTime(12, 0);

    private static final String SOFT_JSON =
            "{\"tendency\":\"SOFT_CONCERN\",\"narrative\":\"self_disclosure\",\"riskPoints\":[],\"uncertainty\":\"\"}";
    private static final String CLEAN_JSON =
            "{\"tendency\":\"NO_OBVIOUS_ISSUE\",\"narrative\":\"none\",\"riskPoints\":[],\"uncertainty\":\"\"}";

    @BeforeEach
    void setUp() {
        AuthenticatedTestUser.ensure(db, AUTHOR, 0);
        AuthenticatedTestUser.ensure(db, VICTIM, 0);
    }

    // ── happy path：只落观测台账，线上零差异 ──

    @Test
    @DisplayName("影子判定 happy path：落 moderation_soft_shadow（含真实归属），业务软档字段/审核结论/通知/抽检池零变化")
    void happyPathOnlyShadow() {
        long workId = insertWork(AUTHOR, "影子作品A", 0, hoursAgo(1));
        long commentId = insertComment(AUTHOR, workId, "影子评论A", 0, hoursAgo(0));
        when(aiClient.moderationShadowSuggestWithAttribution(anyString())).thenAnswer(inv -> {
            String prompt = inv.getArgument(0);
            return prompt.contains("影子作品A")
                    ? new AiClient.ModerationVerdict(SOFT_JSON, "deepseek", "deepseek-reasoner")
                    : new AiClient.ModerationVerdict(CLEAN_JSON, "deepseek", "deepseek-chat");
        });

        shadowService.scanAndJudge();

        Map<String, Object> work = shadowRow("work", workId);
        assertEquals("OK", work.get("judge_status"));
        assertEquals("SOFT_CONCERN", work.get("tendency"));
        assertEquals("self_disclosure", work.get("narrative"));
        assertEquals(1, ((Number) work.get("attempts")).intValue());
        assertNotNull(work.get("audit_log_id"));
        assertNotNull(work.get("target_create_time"));
        // 归属：实际判成即落真实 provider/model
        assertEquals("deepseek", work.get("provider"));
        assertEquals("deepseek-reasoner", work.get("model"));

        Map<String, Object> comment = shadowRow("comment", commentId);
        assertEquals("OK", comment.get("judge_status"));
        assertEquals("NO_OBVIOUS_ISSUE", comment.get("tendency"));
        assertEquals("deepseek", comment.get("provider"));
        assertEquals("deepseek-chat", comment.get("model"));

        // 线上零差异：热路径降权列/审核状态/抽检池/通知全部不变
        assertNull(db.queryForObject("SELECT soft_demote_until FROM work WHERE id=?", Timestamp.class, workId));
        assertNull(db.queryForObject("SELECT soft_demote_until FROM work_comment WHERE id=?", Timestamp.class, commentId));
        assertEquals(0, db.queryForObject("SELECT status FROM work WHERE id=?", Integer.class, workId).intValue());
        assertEquals(0, count("SELECT COUNT(*) FROM moderation_soft_review"));
        assertEquals(0, count("SELECT COUNT(*) FROM notification"));
        // 审计：scene 独立为 moderation_shadow（与审核员 moderation 隔离）
        assertEquals(2, count("SELECT COUNT(*) FROM ai_audit_log WHERE scene='moderation_shadow' AND action='PASS'"));
        assertEquals(0, count("SELECT COUNT(*) FROM ai_audit_log WHERE scene='moderation'"));
    }

    // ── 评审 a：失败不一次定终态，重试至上限落 UNAVAILABLE ──

    @Test
    @DisplayName("AI 连续不可用：PENDING(attempts=1) → UNAVAILABLE(attempts=2 终态) → 不再重试；归属保持 unknown 不被失败污染")
    void retryUntilUnavailable() {
        long workId = insertWork(AUTHOR, "熔断窗样本", 0, hoursAgo(1));
        when(aiClient.moderationShadowSuggestWithAttribution(anyString())).thenReturn(null);

        shadowService.scanAndJudge();
        Map<String, Object> row = shadowRow("work", workId);
        assertEquals("PENDING", row.get("judge_status"));
        assertEquals(1, ((Number) row.get("attempts")).intValue());
        assertEquals("unknown", row.get("provider")); // 失败尝试不落归属
        assertEquals("unknown", row.get("model"));

        shadowService.scanAndJudge();
        row = shadowRow("work", workId);
        assertEquals("UNAVAILABLE", row.get("judge_status"));
        assertEquals(2, ((Number) row.get("attempts")).intValue());
        assertEquals("unknown", row.get("provider")); // 终态仍 unknown：不可用不代表任何模型判过

        shadowService.scanAndJudge(); // 终态不再重试，也不重复发起 AI 调用
        row = shadowRow("work", workId);
        assertEquals("UNAVAILABLE", row.get("judge_status"));
        assertEquals(2, ((Number) row.get("attempts")).intValue());
        verify(aiClient, times(2)).moderationShadowSuggestWithAttribution(anyString());
        // UNAVAILABLE 单独统计，不进分子分母；反向排除口径下 unknown 归属计入默认（生产）统计
        var stats = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY);
        assertEquals(1L, stats.get("unavailableTotal"));
        assertEquals(0L, stats.get("judgedTotal"));
        assertNull(stats.get("softRate"));
        assertEquals(0L, stats.get("demoJudgedInWindow")); // 无演示归属行
        var unknownScope = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY, "unknown");
        assertEquals(1L, unknownScope.get("unavailableTotal")); // 显式取数能力保留
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProvider = (List<Map<String, Object>>) stats.get("byProvider");
        assertEquals(1, byProvider.size());
        assertEquals("unknown", byProvider.get(0).get("provider"));
        assertEquals(1L, byProvider.get(0).get("rows"));
    }

    @Test
    @DisplayName("重试成功：归属以最后一次实际判定为准（先 unknown 后覆盖为真实 provider/model）")
    void retrySuccessOverwritesAttribution() {
        long workId = insertWork(AUTHOR, "先败后成", 0, hoursAgo(1));
        when(aiClient.moderationShadowSuggestWithAttribution(anyString()))
                .thenReturn(null)
                .thenReturn(new AiClient.ModerationVerdict(SOFT_JSON, "deepseek", "deepseek-reasoner"));

        shadowService.scanAndJudge();
        Map<String, Object> row = shadowRow("work", workId);
        assertEquals("PENDING", row.get("judge_status"));
        assertEquals("unknown", row.get("provider"));

        shadowService.scanAndJudge();
        row = shadowRow("work", workId);
        assertEquals("OK", row.get("judge_status"));
        assertEquals(2, ((Number) row.get("attempts")).intValue());
        assertEquals("SOFT_CONCERN", row.get("tendency"));
        assertEquals("deepseek", row.get("provider"));
        assertEquals("deepseek-reasoner", row.get("model"));
    }

    // ── 评审 b：日级预算硬上限 ──

    @Test
    @DisplayName("日预算=1：当日第二条不再发起 AI 调用，统计接口 truncated=true")
    void dailyBudgetTruncates() {
        Object original = ReflectionTestUtils.getField(shadowService, "dailyBudget");
        ReflectionTestUtils.setField(shadowService, "dailyBudget", 1);
        try {
            insertWork(AUTHOR, "样本一", 0, hoursAgo(2));
            insertWork(AUTHOR, "样本二", 0, hoursAgo(1));
            when(aiClient.moderationShadowSuggestWithAttribution(anyString()))
                    .thenReturn(new AiClient.ModerationVerdict(CLEAN_JSON, "deepseek", "deepseek-chat"));

            shadowService.scanAndJudge(); // 只够判一条
            shadowService.scanAndJudge(); // 预算已尽，直接截断

            assertEquals(1, count("SELECT COUNT(*) FROM moderation_soft_shadow"));
            verify(aiClient, times(1)).moderationShadowSuggestWithAttribution(anyString());
            var stats = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY);
            @SuppressWarnings("unchecked")
            Map<String, Object> budget = (Map<String, Object>) stats.get("dailyBudget");
            assertEquals(1, budget.get("limit"));
            assertEquals(1, budget.get("usedToday"));
            assertEquals(true, budget.get("truncated"));
        } finally {
            ReflectionTestUtils.setField(shadowService, "dailyBudget", original);
        }
    }

    // ── 评审 c + 统计口径：主指标/保守下界/覆盖率降级/无害率/分层 ──

    @Test
    @DisplayName("统计口径：softRate=soft/judged、coverage 达阈值不降级、不足显式 degraded、无害率实时 JOIN")
    void statsCaliberAndDegraded() {
        // 10 条送审（作者有校园认证）；8 条 deepseek 判 OK（2 软档）、1 条 ollama 演示、1 条 unknown PENDING
        for (int i = 0; i < 10; i++) {
            insertWork(AUTHOR, "作品" + i, 0, hoursAgo(i));
        }
        List<Long> workIds = db.queryForList("SELECT id FROM work WHERE user_id=? ORDER BY id", Long.class, AUTHOR);
        for (int i = 0; i < 8; i++) {
            boolean soft = i < 2;
            insertShadow("work", workIds.get(i), AUTHOR, "OK",
                    soft ? "SOFT_CONCERN" : "NO_OBVIOUS_ISSUE", soft ? "self_disclosure" : "none",
                    "deepseek", soft ? "deepseek-reasoner" : "deepseek-chat", 1, hoursAgo(i));
        }
        insertShadow("work", workIds.get(8), AUTHOR, "OK", "SOFT_CONCERN", "self_disclosure",
                "ollama", "qwen2.5-local", 1, hoursAgo(8)); // 演示归属：不得混入默认口径
        insertShadow("work", workIds.get(9), AUTHOR, "PENDING", null, null,
                "unknown", "unknown", 1, hoursAgo(9)); // 存量/未判成归属
        // 人工终审：deepseek 软档样本一通过一驳回
        db.update("UPDATE work SET status=1 WHERE id=?", workIds.get(0));
        db.update("UPDATE work SET status=2 WHERE id=?", workIds.get(1));
        db.update("""
                INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,review_reason,reviewer_id,create_time,update_time,deleted)
                VALUES(?,?,?,'测试学院','证件密文','APPROVED','',NULL,NOW(),NOW(),0)
                """, IdWorker.getId(), AUTHOR, "测试大学");

        // 默认口径：反向排除演示（ollama/mock），deepseek 与 unknown 存量都计入；演示判定被排除并标注
        var stats = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY);
        assertEquals("production", stats.get("providerScope"));
        assertEquals(List.of("ollama", "mock"), stats.get("excludedDemoProviders"));
        assertEquals(1L, stats.get("demoJudgedInWindow")); // ollama 的 OK 行被排除且显式标注
        assertEquals(10L, stats.get("submittedTotal"));
        assertEquals(8L, stats.get("judgedTotal"));
        assertEquals(0L, stats.get("unavailableTotal"));
        assertEquals(1L, stats.get("pendingRetryTotal")); // unknown PENDING 计入默认口径
        assertEquals(2L, stats.get("softTotal")); // ollama 的 SOFT_CONCERN 不计入
        assertEquals(0.25, (Double) stats.get("softRate"), 1e-9);
        assertEquals(0.8, (Double) stats.get("coverageRate"), 1e-9);
        assertEquals(0.2, (Double) stats.get("softRateOnSubmitted"), 1e-9);
        assertEquals(false, stats.get("degraded")); // 0.8 不低于阈值 0.8

        @SuppressWarnings("unchecked")
        Map<String, Object> softReviewed = (Map<String, Object>) stats.get("softReviewed");
        assertEquals(1L, softReviewed.get("approved"));
        assertEquals(1L, softReviewed.get("rejected"));
        assertEquals(0L, softReviewed.get("pending"));
        assertEquals(0.5, (Double) softReviewed.get("harmlessRate"), 1e-9);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) stats.get("byTargetType");
        assertEquals(2, byType.size());
        assertEquals("work", byType.get(0).get("targetType"));
        assertEquals(10L, byType.get(0).get("submitted"));
        assertEquals(2L, byType.get(0).get("soft"));
        assertEquals(0L, byType.get(1).get("submitted")); // comment 行恒在，无数据为 0
        assertNull(byType.get(1).get("softRate"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bySchool = (List<Map<String, Object>>) stats.get("bySchool");
        assertEquals(1, bySchool.size());
        assertEquals("测试大学", bySchool.get(0).get("school"));
        assertEquals(8L, bySchool.get(0).get("judged")); // 演示/存量归属不进校园分层
        assertEquals(2L, bySchool.get(0).get("soft"));

        // byProvider 全窗分桶：unknown 单独成桶，演示归属可见
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byProvider = (List<Map<String, Object>>) stats.get("byProvider");
        Map<String, Map<String, Object>> bucket = byProvider.stream().collect(
                java.util.stream.Collectors.toMap(m -> (String) m.get("provider"), m -> m));
        assertEquals(3, bucket.size());
        assertEquals(8L, bucket.get("deepseek").get("judged"));
        assertEquals(2L, bucket.get("deepseek").get("soft"));
        assertEquals(1L, bucket.get("ollama").get("judged"));
        assertEquals(1L, bucket.get("ollama").get("soft"));
        assertEquals(1L, bucket.get("unknown").get("rows"));
        assertEquals(0L, bucket.get("unknown").get("judged"));
        assertNull(bucket.get("unknown").get("softRate"));

        // 演示数据单独取：?provider=ollama 仅该归属
        var demo = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY, "ollama");
        assertEquals("ollama", demo.get("providerScope"));
        assertEquals(1L, demo.get("judgedTotal"));
        assertEquals(1L, demo.get("softTotal"));
        assertEquals(1.0, (Double) demo.get("softRate"), 1e-9);
        assertEquals(true, demo.get("degraded")); // coverage=0.1 < 0.8

        // all 不过滤：含演示与存量
        var all = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY, "all");
        assertEquals("all", all.get("providerScope"));
        assertEquals(9L, all.get("judgedTotal"));
        assertEquals(3L, all.get("softTotal"));
        assertEquals(1L, all.get("pendingRetryTotal"));

        // 评审 c：再送审 2 条未判定 → coverage=8/12≈0.667 < 0.8 → 显式降级
        insertWork(AUTHOR, "未判一", 0, hoursAgo(0));
        insertWork(AUTHOR, "未判二", 0, hoursAgo(0));
        var degraded = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY);
        assertEquals(12L, degraded.get("submittedTotal"));
        assertEquals(true, degraded.get("degraded"));
        assertEquals(8.0 / 12, (Double) degraded.get("coverageRate"), 1e-9);
    }

    // ── 反向排除口径钉桩（2026-11-07 返工） ──

    @Test
    @DisplayName("钉桩①：存量 unknown 行计入默认口径分子，覆盖率不被压低；演示行被排除并标注")
    void legacyUnknownCountedInDefaultScope() {
        // 4 条送审：3 条 unknown 存量（1 软档）+ 1 条 ollama 演示
        long w1 = insertWork(AUTHOR, "存量一", 1, hoursAgo(4));
        long w2 = insertWork(AUTHOR, "存量二", 1, hoursAgo(3));
        long w3 = insertWork(AUTHOR, "存量三", 0, hoursAgo(2));
        long w4 = insertWork(AUTHOR, "演示一", 1, hoursAgo(1));
        insertShadow("work", w1, AUTHOR, "OK", "SOFT_CONCERN", "self_disclosure", "unknown", "unknown", 1, hoursAgo(4));
        insertShadow("work", w2, AUTHOR, "OK", "NO_OBVIOUS_ISSUE", "none", "unknown", "unknown", 1, hoursAgo(3));
        insertShadow("work", w3, AUTHOR, "OK", "NO_OBVIOUS_ISSUE", "none", "unknown", "unknown", 1, hoursAgo(2));
        insertShadow("work", w4, AUTHOR, "OK", "NO_OBVIOUS_ISSUE", "none", "ollama", "qwen2.5-local", 1, hoursAgo(1));

        var stats = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY);
        assertEquals("production", stats.get("providerScope"));
        assertEquals(4L, stats.get("submittedTotal"));
        // unknown 存量计入分子：judged=3（ollama 被排除），覆盖率=3/4=0.75 不被压成 0 或 1/4
        assertEquals(3L, stats.get("judgedTotal"));
        assertEquals(1L, stats.get("softTotal"));
        assertEquals(1.0 / 3, (Double) stats.get("softRate"), 1e-9);
        assertEquals(0.75, (Double) stats.get("coverageRate"), 1e-9);
        assertEquals(1L, stats.get("demoJudgedInWindow")); // 被排除的演示判定显式标注
    }

    @Test
    @DisplayName("钉桩②：云端换成非 deepseek 的 provider 名后，默认统计依然包含该批数据（防换供应商静默归零）")
    void nonDeepseekCloudProviderStillCounted() {
        // 云端将来换成 aliyun：3 条 aliyun 判定（1 软档）
        long w1 = insertWork(AUTHOR, "云一", 1, hoursAgo(3));
        long w2 = insertWork(AUTHOR, "云二", 1, hoursAgo(2));
        long w3 = insertWork(AUTHOR, "云三", 0, hoursAgo(1));
        insertShadow("work", w1, AUTHOR, "OK", "SOFT_CONCERN", "self_disclosure", "aliyun", "qwen-max", 1, hoursAgo(3));
        insertShadow("work", w2, AUTHOR, "OK", "NO_OBVIOUS_ISSUE", "none", "aliyun", "qwen-max", 1, hoursAgo(2));
        insertShadow("work", w3, AUTHOR, "OK", "NO_OBVIOUS_ISSUE", "none", "aliyun", "qwen-plus", 1, hoursAgo(1));

        var stats = shadowService.stats(ANCHOR_DAY, ANCHOR_DAY);
        assertEquals(3L, stats.get("submittedTotal"));
        assertEquals(3L, stats.get("judgedTotal")); // 非 deepseek 的云端归属照样计入，不归零
        assertEquals(1L, stats.get("softTotal"));
        assertEquals(1.0 / 3, (Double) stats.get("softRate"), 1e-9);
        assertEquals(1.0, (Double) stats.get("coverageRate"), 1e-9);
        assertEquals(false, stats.get("degraded"));
    }

    // ── 开关关闭空转；统计接口不随开关 gated ──

    @Test
    @DisplayName("影子开关关闭：扫描整体空转（无影子行、无 AI 调用、无审计行）")
    void disabledNoop() {
        Object original = ReflectionTestUtils.getField(shadowService, "enabled");
        ReflectionTestUtils.setField(shadowService, "enabled", false);
        try {
            insertWork(AUTHOR, "关闭期样本", 0, hoursAgo(1));
            shadowService.scanAndJudge();
            assertEquals(0, count("SELECT COUNT(*) FROM moderation_soft_shadow"));
            assertEquals(0, count("SELECT COUNT(*) FROM ai_audit_log WHERE scene='moderation_shadow'"));
            verify(aiClient, never()).moderationShadowSuggestWithAttribution(anyString());
        } finally {
            ReflectionTestUtils.setField(shadowService, "enabled", original);
        }
    }

    // ── 注销级联 ──

    @Test
    @DisplayName("作者注销：其影子行级联逻辑删除，他人影子行不受影响")
    void eraseCascade() {
        long w1 = insertWork(AUTHOR, "本人作品", 1, hoursAgo(2));
        long w2 = insertWork(VICTIM, "他人作品", 1, hoursAgo(1));
        insertShadow("work", w1, AUTHOR, "OK", "SOFT_CONCERN", "self_disclosure", "deepseek", "deepseek-reasoner", 1, hoursAgo(2));
        insertShadow("work", w2, VICTIM, "OK", "NO_OBVIOUS_ISSUE", "none", "deepseek", "deepseek-chat", 1, hoursAgo(1));

        lifecycle.erase(AUTHOR);

        assertEquals(1, db.queryForObject(
                "SELECT deleted FROM moderation_soft_shadow WHERE target_type='work' AND target_id=?",
                Integer.class, w1).intValue());
        assertEquals(0, db.queryForObject(
                "SELECT deleted FROM moderation_soft_shadow WHERE target_type='work' AND target_id=?",
                Integer.class, w2).intValue());
    }

    // ── 夹具 ──

    private Map<String, Object> shadowRow(String targetType, long targetId) {
        return db.queryForMap("SELECT * FROM moderation_soft_shadow WHERE target_type=? AND target_id=?",
                targetType, targetId);
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    private long insertWork(long userId, String title, int status, LocalDateTime createTime) {
        long id = IdWorker.getId();
        db.update("""
                INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,create_time,update_time,deleted)
                VALUES(?,?,?,'text',1,1,0,?,?,?,0)
                """, id, userId, title, status, Timestamp.valueOf(createTime), Timestamp.valueOf(createTime));
        return id;
    }

    private long insertComment(long userId, long workId, String content, int status, LocalDateTime createTime) {
        long id = IdWorker.getId();
        db.update("""
                INSERT INTO work_comment(id,work_id,user_id,content,status,create_time,update_time,deleted)
                VALUES(?,?,?,?,?,?,?,0)
                """, id, workId, userId, content, status, Timestamp.valueOf(createTime), Timestamp.valueOf(createTime));
        return id;
    }

    private void insertShadow(String targetType, long targetId, long authorId, String judgeStatus,
                              String tendency, String narrative, String provider, String model,
                              int attempts, LocalDateTime targetCreateTime) {
        db.update("""
                INSERT INTO moderation_soft_shadow
                (id,target_type,target_id,author_id,judge_status,tendency,narrative,audit_log_id,provider,model,attempts,last_attempt_at,target_create_time,create_time,update_time,deleted)
                VALUES(?,?,?,?,?,?,?,NULL,?,?,?,?,?,?,?,0)
                """, IdWorker.getId(), targetType, targetId, authorId, judgeStatus, tendency, narrative,
                provider, model, attempts, Timestamp.valueOf(targetCreateTime), Timestamp.valueOf(targetCreateTime),
                Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
    }

    /** 从锚点正午回拨 h 小时（不是真实 now——见 ANCHOR_DAY 注释）。 */
    private static LocalDateTime hoursAgo(int h) {
        return ANCHOR_NOON.minusHours(h);
    }
}
