package com.zhiyu.modules.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.SoftTierPoolService;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.entity.WorkComment;
import com.zhiyu.modules.work.mapper.WorkCommentMapper;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.modules.work.service.SoftTierRecordService;
import com.zhiyu.support.AuthenticatedTestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 软档处置链集成测试（2026-11-07，设计 docs/moderation-soft-tier-disposition-chain-20261107.md）。
 *
 * <p>覆盖：写池（mandatory 三层 + random + 溢出与 cap 绕过）、裁决（CLEAR/UPHOLD/BLOCK）、
 * SLA 扫描升级、申诉幂等与权限、位次降权排序（作品社区流与评论列表）。
 * 开关关闭的拒绝语义见 {@code SoftTierRecordDisabledTest}（纯单测，不起 Spring）。
 *
 * <p>测试属性：sample-rate=1.0（随机层确定性命中）、daily-cap=2（小 cap 便于溢出场景）、
 * demote-position=2（小 N 便于排序断言）。
 */
@SpringBootTest(properties = {
        "zhiyu.moderation.soft-tier.disposition.enabled=true",
        "zhiyu.moderation.soft-tier.disposition.sample-rate=1.0",
        "zhiyu.moderation.soft-tier.disposition.daily-cap=2",
        "zhiyu.moderation.soft-tier.disposition.demote-position=2",
        "zhiyu.moderation.soft-tier.disposition.sla-hours=24",
        "zhiyu.moderation.soft-tier.disposition.unsampled-demote-hours=72"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SoftTierDispositionChainTest {

    @Autowired private SoftTierRecordService recordService;
    @Autowired private SoftTierPoolService poolService;
    @Autowired private WorkMapper workMapper;
    @Autowired private WorkCommentMapper workCommentMapper;
    @Autowired private com.zhiyu.modules.work.service.WorkService workService;
    @Autowired private com.zhiyu.modules.work.service.WorkInteractionService workInteractionService;
    @Autowired private JdbcTemplate db;

    private static final long AUTHOR = 886001L;
    private static final long OTHER = 886002L;
    private static final long ADMIN = 886003L;

    @BeforeEach
    void setUp() {
        AuthenticatedTestUser.ensure(db, AUTHOR, 0);
        AuthenticatedTestUser.ensure(db, OTHER, 0);
        AuthenticatedTestUser.ensure(db, ADMIN, 3);
    }

    // ── 写池：抽样分层 ──

    @Test
    @DisplayName("词库命中 → mandatory_lexicon 必入池（cap 也不拦截）")
    void recordMandatoryLexicon() {
        long workId = insertWork(AUTHOR, "普通标题", hoursAgo(1));
        long reviewId = recordService.record("work", workId, AUTHOR, "心里闪过遗书的念头", null, false, "普通标题");
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("mandatory_lexicon", row.get("sample_layer"));
        assertEquals(1, ((Number) row.get("sampled")).intValue());
        assertEquals("PENDING", row.get("pool_status"));
        assertNotNull(row.get("sla_due_at"));
        // 抽中者降权硬上界 ≈ 30 天
        LocalDateTime demote = ((Timestamp) row.get("demote_until")).toLocalDateTime();
        assertTrue(demote.isAfter(LocalDateTime.now().plusDays(29)));
        assertNotNull(workMapper.selectById(workId).getSoftDemoteUntil());
        // 降权通知已发（作者可见，反静默折叠）
        assertEquals(1, countNotification(AUTHOR, "SOFT_TIER_NOTICE"));
    }

    @Test
    @DisplayName("riskPoints 非空（回显）→ mandatory_riskpoint 必入池")
    void recordMandatoryRiskpoint() {
        long workId = insertWork(AUTHOR, "今天的手工", hoursAgo(1));
        long reviewId = recordService.record("work", workId, AUTHOR, "完成了很好看", null, true, "今天的手工");
        assertEquals("mandatory_riskpoint", reviewRow(reviewId).get("sample_layer"));
    }

    @Test
    @DisplayName("作者当日首条 → mandatory_author_first；同日第二条走 random 层")
    void recordAuthorFirstThenRandom() {
        long w1 = insertWork(AUTHOR, "干净内容一", hoursAgo(2));
        long r1 = recordService.record("work", w1, AUTHOR, "干净内容一", null, false, "干净内容一");
        assertEquals("mandatory_author_first", reviewRow(r1).get("sample_layer"));

        long w2 = insertWork(AUTHOR, "干净内容二", hoursAgo(1));
        long r2 = recordService.record("work", w2, AUTHOR, "干净内容二", null, false, "干净内容二");
        Map<String, Object> row2 = reviewRow(r2);
        assertEquals("random", row2.get("sample_layer"));
        assertEquals(1, ((Number) row2.get("sampled")).intValue()); // sample-rate=1.0 确定性命中
    }

    @Test
    @DisplayName("cap 打满后 author_first/random 溢出为未抽中（保留 72h 窗口）；mandatory 层不受 cap 约束")
    void recordOverflowAndMandatoryBypass() {
        // 先用两条 mandatory_lexicon 把日入池数顶到 cap=2（mandatory 也计数）
        long w1 = insertWork(AUTHOR, "一", hoursAgo(3));
        recordService.record("work", w1, AUTHOR, "遗书", null, false, "一");
        long w2 = insertWork(OTHER, "二", hoursAgo(2));
        recordService.record("work", w2, OTHER, "想死", null, false, "二");
        // 新作者当日首条：cap 已满 → overflow_author_first，未抽中，72h 窗口
        long newAuthor = 886009L;
        AuthenticatedTestUser.ensure(db, newAuthor, 0);
        long w3 = insertWork(newAuthor, "三", hoursAgo(1));
        long r3 = recordService.record("work", w3, newAuthor, "干净内容", null, false, "三");
        Map<String, Object> row3 = reviewRow(r3);
        assertEquals("overflow_author_first", row3.get("sample_layer"));
        assertEquals(0, ((Number) row3.get("sampled")).intValue());
        assertEquals("NOT_SAMPLED", row3.get("pool_status"));
        LocalDateTime demote = ((Timestamp) row3.get("demote_until")).toLocalDateTime();
        assertTrue(demote.isBefore(LocalDateTime.now().plusHours(73)) && demote.isAfter(LocalDateTime.now().plusHours(71)));
        // mandatory_lexicon 绕过 cap：仍可入池
        long w4 = insertWork(newAuthor, "四", hoursAgo(0));
        long r4 = recordService.record("work", w4, newAuthor, "烧炭", null, false, "四");
        assertEquals("mandatory_lexicon", reviewRow(r4).get("sample_layer"));
        assertEquals(1, ((Number) reviewRow(r4).get("sampled")).intValue());
    }

    @Test
    @DisplayName("评论软档：写 work_comment 降权列；通知 ref 跳所属作品（与 COMMENT_AUDIT 同约定）")
    void recordComment() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(2));
        long commentId = insertComment(OTHER, workId, "很温暖", hoursAgo(1));
        long reviewId = recordService.record("comment", commentId, OTHER, "很温暖", null, false, null);
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("comment", row.get("target_type"));
        assertNotNull(workCommentMapper.selectById(commentId).getSoftDemoteUntil());
        var n = db.queryForList("SELECT ref_type, ref_id, sub_ref_id FROM notification WHERE user_id=? AND type='SOFT_TIER_NOTICE'", OTHER);
        assertEquals(1, n.size());
        assertEquals("comment", n.get(0).get("ref_type"));
        assertEquals(workId, ((Number) n.get(0).get("ref_id")).longValue());
        assertEquals(commentId, ((Number) n.get(0).get("sub_ref_id")).longValue());
    }

    // ── 裁决 ──

    @Test
    @DisplayName("CLEAR：清除降权 + 通知恢复；终裁后再裁决 → 3001")
    void verdictClear() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(1));
        long reviewId = recordService.record("work", workId, AUTHOR, "干净", null, false, "作品");
        poolService.verdict(reviewId, ADMIN, "CLEAR", null);
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("CLEAR", row.get("pool_status"));
        assertNull(row.get("demote_until"));
        assertNull(workMapper.selectById(workId).getSoftDemoteUntil());
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND type='SOFT_TIER_NOTICE' AND dedupe_key LIKE '%restore%'",
                Integer.class, AUTHOR));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> poolService.verdict(reviewId, ADMIN, "UPHOLD", null));
        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("UPHOLD：维持至 72h 窗口结束，内容状态不变")
    void verdictUphold() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(1));
        long reviewId = recordService.record("work", workId, AUTHOR, "干净", null, false, "作品");
        poolService.verdict(reviewId, ADMIN, "UPHOLD", null);
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("UPHOLD", row.get("pool_status"));
        LocalDateTime demote = ((Timestamp) row.get("demote_until")).toLocalDateTime();
        assertTrue(demote.isBefore(LocalDateTime.now().plusHours(73)));
        assertEquals(1, workMapper.selectById(workId).getStatus());
        assertNotNull(workMapper.selectById(workId).getSoftDemoteUntil());
    }

    @Test
    @DisplayName("BLOCK：走既有下架状态机（理由必填 + WORK_AUDIT 通知），降权列清除")
    void verdictBlockWork() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(1));
        long reviewId = recordService.record("work", workId, AUTHOR, "干净", null, false, "作品");
        BusinessException noReason = assertThrows(BusinessException.class,
                () -> poolService.verdict(reviewId, ADMIN, "BLOCK", " "));
        assertEquals(1001, noReason.getCode());
        poolService.verdict(reviewId, ADMIN, "BLOCK", "人工确认不适合公开");
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("BLOCK", row.get("pool_status"));
        Work w = workMapper.selectById(workId);
        assertEquals(2, w.getStatus());
        assertEquals("人工确认不适合公开", w.getAuditReason());
        assertNull(w.getSoftDemoteUntil());
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND type='WORK_AUDIT'", Integer.class, AUTHOR));
    }

    // ── SLA 扫描 ──

    @Test
    @DisplayName("SLA 扫描：PENDING 逾期 → ESCALATED + 管理员通知；默认维持降权不自动恢复")
    void slaScanEscalates() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(2));
        long reviewId = recordService.record("work", workId, AUTHOR, "干净", null, false, "作品");
        db.update("UPDATE moderation_soft_review SET sla_due_at=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(1)), reviewId);
        poolService.scanSlaBreach();
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("ESCALATED", row.get("pool_status"));
        // 保守默认：降权仍在（不自动恢复、不自动下架）
        assertNotNull(row.get("demote_until"));
        assertNotNull(workMapper.selectById(workId).getSoftDemoteUntil());
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND type='SOFT_TIER_NOTICE' AND ref_type='soft_review'",
                Integer.class, ADMIN));
    }

    // ── 申诉 ──

    @Test
    @DisplayName("申诉：理由必填+安全过滤+落库；升人工必审 + 幂等；他人申诉 → 1003；已终裁 → 3001")
    void appealFlow() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(1));
        long reviewId = recordService.record("work", workId, AUTHOR, "干净", null, false, "作品");
        // 理由缺失/超长/命中安全过滤均 1001
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> recordService.appeal(AUTHOR, "work", workId, " ")).getCode());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> recordService.appeal(AUTHOR, "work", workId, "x".repeat(201))).getCode());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> recordService.appeal(AUTHOR, "work", workId, "不想活了")).getCode());
        // 安全过滤写审计（scene=soft-appeal）
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM ai_audit_log WHERE user_id=? AND scene='soft-appeal'",
                Integer.class, AUTHOR) >= 1);
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> recordService.appeal(OTHER, "work", workId, "我觉得判错了"));
        assertEquals(1003, forbidden.getCode());

        recordService.appeal(AUTHOR, "work", workId, "这条只是记录手工过程，没有敏感内容");
        Map<String, Object> row = reviewRow(reviewId);
        assertEquals("ESCALATED", row.get("pool_status"));
        assertEquals("FILED", row.get("appeal_status"));
        assertEquals("这条只是记录手工过程，没有敏感内容", row.get("appeal_reason"));
        // 幂等：重复申诉不报错、状态与理由不覆盖
        recordService.appeal(AUTHOR, "work", workId, "换个理由也不覆盖");
        assertEquals("这条只是记录手工过程，没有敏感内容", reviewRow(reviewId).get("appeal_reason"));
        // 裁决后 appeal_status=RESOLVED；再申诉 → 3001
        poolService.verdict(reviewId, ADMIN, "CLEAR", null);
        assertEquals("RESOLVED", reviewRow(reviewId).get("appeal_status"));
        BusinessException closed = assertThrows(BusinessException.class,
                () -> recordService.appeal(AUTHOR, "work", workId, "已结束再申诉"));
        assertEquals(3001, closed.getCode());
    }

    // ── 申诉列表 / APPEAL 筛选 / 看板聚合 / 详情补全（2026-11-07 契约缺口补齐） ──

    @Test
    @DisplayName("申诉列表端点 + 池列表 APPEAL 筛选：单独可拉、口径一致")
    void appealsListAndStatusFilter() {
        long w1 = insertWork(AUTHOR, "作品一", hoursAgo(2));
        long r1 = recordService.record("work", w1, AUTHOR, "干净", null, false, "作品一");
        recordService.appeal(AUTHOR, "work", w1, "申诉理由一");
        long w2 = insertWork(OTHER, "作品二", hoursAgo(1));
        recordService.record("work", w2, OTHER, "干净", null, false, "作品二"); // 未申诉

        var appeals = poolService.appeals(1);
        assertEquals(1, appeals.get("total"));
        var appealRows = (List<Map<String, Object>>) appeals.get("records");
        assertEquals(String.valueOf(r1), String.valueOf(appealRows.get(0).get("id")));
        assertEquals("申诉理由一", appealRows.get(0).get("appealReason"));
        assertEquals("测试账号", appealRows.get(0).get("authorNickname"));

        var filtered = poolService.list(1, "APPEAL");
        assertEquals(1, filtered.get("total"));
        var open = poolService.list(1, "OPEN");
        assertEquals(2, open.get("total")); // 两条都在待办（一条因申诉升 ESCALATED，一条 PENDING）
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> poolService.list(1, "BOGUS")).getCode());
    }

    @Test
    @DisplayName("看板聚合：入池量/待复核/超时/申诉/裁决分布一次返回")
    void statsAggregation() {
        long w1 = insertWork(AUTHOR, "作品一", hoursAgo(3));
        long r1 = recordService.record("work", w1, AUTHOR, "干净", null, false, "作品一"); // PENDING→申诉升 ESCALATED
        long w2 = insertWork(OTHER, "作品二", hoursAgo(2));
        long r2 = recordService.record("work", w2, OTHER, "干净", null, false, "作品二"); // PENDING，手动造超时
        db.update("UPDATE moderation_soft_review SET sla_due_at=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(1)), r2);
        recordService.appeal(AUTHOR, "work", w1, "不服");
        // 注意 daily-cap=2：r1/r2 已把当日入池顶满，第三条必须走 mandatory_lexicon（bypass cap）才能稳定入池
        long w3 = insertWork(OTHER, "作品三", hoursAgo(1));
        long r3 = recordService.record("work", w3, OTHER, "烧炭", null, false, "作品三");
        poolService.verdict(r3, ADMIN, "CLEAR", null);

        var stats = poolService.stats();
        assertEquals(3, stats.get("todaySampled"));
        assertEquals(3, stats.get("todayRecorded"));
        assertEquals(1, stats.get("pending"));   // r2 仍 PENDING（本用例未触发扫描）
        assertEquals(1, stats.get("escalated")); // r1 申诉升级
        assertEquals(1, stats.get("overdue"));   // r2 sla 已过；r1 申诉后 SLA 重起为未来
        assertEquals(1, stats.get("appeals"));
        var verdicts = (Map<String, Object>) stats.get("verdicts");
        assertEquals(1, verdicts.get("CLEAR"));
        assertEquals(0, verdicts.get("UPHOLD"));
        assertEquals(0, verdicts.get("BLOCK"));
    }

    @Test
    @DisplayName("池详情：authorNickname 免二次请求；封面走授权读取（非媒体引用透传、失效引用降级 null）")
    void detailEnrichment() {
        // 静态封面（非私有媒体引用）→ 原样透传
        long w1 = insertWork(AUTHOR, "作品一", hoursAgo(1));
        db.update("UPDATE work SET cover_url='/static/covers/a.jpg', media_type='image' WHERE id=?", w1);
        long r1 = recordService.record("work", w1, AUTHOR, "干净", null, false, "作品一");
        var d1 = poolService.detail(r1, ADMIN);
        assertEquals("测试账号", d1.get("authorNickname"));
        var work1 = (Map<String, Object>) d1.get("work");
        assertEquals("/static/covers/a.jpg", work1.get("coverUrl"));

        // 私有媒体引用但媒体不存在 → 降级 null（绝不下发可长期访问 URL，也不造绕过通道）
        long w2 = insertWork(AUTHOR, "作品二", hoursAgo(0));
        db.update("UPDATE work SET cover_url='/api/media/999999999/content', media_type='image' WHERE id=?", w2);
        long r2 = recordService.record("work", w2, AUTHOR, "干净", null, false, "作品二");
        var d2 = poolService.detail(r2, ADMIN);
        var work2 = (Map<String, Object>) d2.get("work");
        assertNull(work2.get("coverUrl"));
    }

    @Test
    @DisplayName("作者视角三条件口径：开关开 + 本人 + 未过期才下发（作品详情/评论流）")
    void authorViewTripleRule() {
        // 作品详情：作者本人且降权未过期 → 下发；开关关闭 → 不下发（本上下文开关开启，另见 WorkVO 注释）
        long workId = insertWork(AUTHOR, "作品", hoursAgo(1));
        recordService.record("work", workId, AUTHOR, "干净", null, false, "作品");
        com.zhiyu.security.UserContext.set(new com.zhiyu.security.LoginUser(AUTHOR, 0));
        try {
            var vo = workService.getWork(AUTHOR, workId);
            assertNotNull(vo.getSoftDemoteUntil());
            // 评论流：本人评论有标记，他人评论无标记；过期不下发
            long c1 = insertComment(AUTHOR, workId, "作者自评", hoursAgo(0));
            demoteComment(c1, 10);
            long c2 = insertComment(OTHER, workId, "他人评论", hoursAgo(0));
            demoteComment(c2, 10);
            long c3 = insertComment(AUTHOR, workId, "过期的自评", hoursAgo(0));
            demoteComment(c3, -1);
            var page = workInteractionService.listComments(workId, 1, 10);
            var vos = page.getRecords();
            var byId = vos.stream().collect(java.util.stream.Collectors.toMap(v -> v.getId(), v -> v));
            assertNotNull(byId.get(c1).getSoftDemoteUntil());
            assertNull(byId.get(c2).getSoftDemoteUntil());
            assertNull(byId.get(c3).getSoftDemoteUntil());
        } finally {
            com.zhiyu.security.UserContext.clear();
        }
    }

    // ── 位次降权排序 ──

    @Test
    @DisplayName("社区流位次降权：软档落在第 N=2 条非软档之后；过期降权视同正常")
    void feedPositionDemotion() {
        // 非软档按时间倒序：n1 最新、n2、n3 最旧；d1/d2 软档（d1 较新）；x1 降权已过期（最新）
        long n3 = insertWork(AUTHOR, "n3", hoursAgo(50));
        long d2 = insertWork(AUTHOR, "d2", hoursAgo(40));
        long n2 = insertWork(AUTHOR, "n2", hoursAgo(30));
        long d1 = insertWork(AUTHOR, "d1", hoursAgo(20));
        long n1 = insertWork(AUTHOR, "n1", hoursAgo(10));
        long x1 = insertWork(AUTHOR, "x1", hoursAgo(0));
        demoteWork(d2, 10);
        demoteWork(d1, 10);
        demoteWork(x1, -1); // 已过期

        List<Work> records = workMapper.selectFeedPage(new Page<>(1, 10, false), 2).getRecords();
        List<Long> order = records.stream().map(Work::getId).toList();
        // 期望：x1（过期视同正常，最新）, n1 | d1, d2（第 2 位之后整体沉底块，组内时间倒序） | n2, n3
        assertEquals(List.of(x1, n1, d1, d2, n2, n3), order);
    }

    @Test
    @DisplayName("评论位次降权：软档评论落在第 N=2 条普通评论之后（时间正序）")
    void commentPositionDemotion() {
        long workId = insertWork(AUTHOR, "作品", hoursAgo(100));
        long c1 = insertComment(OTHER, workId, "c1", hoursAgo(50));
        long c2 = insertComment(OTHER, workId, "c2", hoursAgo(40));
        demoteComment(c2, 10);
        long c3 = insertComment(AUTHOR, workId, "c3", hoursAgo(30));
        List<WorkComment> records = workCommentMapper
                .selectCommentPageDemoted(new Page<>(1, 10, false), workId, null, 2).getRecords();
        List<Long> order = records.stream().map(WorkComment::getId).toList();
        // 时间正序普通：c1, c3；软档 c2 沉到第 2 位之后
        assertEquals(List.of(c1, c3, c2), order);
    }

    // ── 夹具 ──

    private Map<String, Object> reviewRow(long id) {
        return db.queryForMap("SELECT * FROM moderation_soft_review WHERE id=?", id);
    }

    private int countNotification(long userId, String type) {
        return db.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND type=?",
                Integer.class, userId, type);
    }

    private long insertWork(long userId, String title, LocalDateTime createTime) {
        long id = IdWorker.getId();
        db.update("""
                INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,create_time,update_time,deleted)
                VALUES(?,?,?,'text',1,1,0,1,?,?,0)
                """, id, userId, title, Timestamp.valueOf(createTime), Timestamp.valueOf(createTime));
        return id;
    }

    private long insertComment(long userId, long workId, String content, LocalDateTime createTime) {
        long id = IdWorker.getId();
        db.update("""
                INSERT INTO work_comment(id,work_id,user_id,content,status,create_time,update_time,deleted)
                VALUES(?,?,?,?,1,?,?,0)
                """, id, workId, userId, content, Timestamp.valueOf(createTime), Timestamp.valueOf(createTime));
        return id;
    }

    private void demoteWork(long workId, int hoursFromNow) {
        db.update("UPDATE work SET soft_demote_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().plusHours(hoursFromNow)), workId);
    }

    private void demoteComment(long commentId, int hoursFromNow) {
        db.update("UPDATE work_comment SET soft_demote_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().plusHours(hoursFromNow)), commentId);
    }

    private static LocalDateTime hoursAgo(int h) {
        return LocalDateTime.now().minusHours(h);
    }
}
