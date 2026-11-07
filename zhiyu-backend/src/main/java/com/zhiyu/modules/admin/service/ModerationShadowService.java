package com.zhiyu.modules.admin.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.common.util.JdbcTimes;
import com.zhiyu.modules.audit.service.AiAuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 软档影子模式（2026-11-07）：只统计「若开启软档会怎样」，<b>绝不改变线上判定与可见性</b>。
 *
 * <p><b>红线</b>：本服务只 INSERT/UPDATE 自己的观测表 {@code moderation_soft_shadow}——
 * 不写 {@code work}/{@code work_comment} 任何软档字段、不写 {@code moderation_soft_review}、
 * 不影响审核结论、不影响任何可见性与排序、不触发通知、不入抽检池。
 * 投稿/审核主流程零改动：判定由本服务的定时扫描（60s）从库里捞「已送审未影子判定」的行完成。
 *
 * <p><b>「若开启」口径</b>：提示词渲染与解析复用 {@link AdminModerationService} 的 public 静态
 * {@code render}/{@code parse}（评测 harness 同款复用位），强制 {@code softTierEnabled=true}
 * 与含 SOFT_CONCERN 的枚举集合——无论总开关现状如何，观测的正是「若开启」会走的路；
 * AI 调用走 {@link AiClient#moderationSuggest}（同一 {@code llm-moderation} 熔断 key 与分档）。
 *
 * <p><b>三条增补纪律（评审 a/b/c）</b>：
 * a) 失败不一次定终态——{@code attempts} 计次、{@code last_attempt_at} 留痕，每轮优先重试
 *    「PENDING 且未达 {@code max-attempts}」的记录，达上限才落 {@code UNAVAILABLE} 终态
 *    （熔断窗样本若永久缺判，分母会被系统性低估，比少判几条更危险）；
 * b) 日级 AI 调用预算硬上限（计数源 {@code ai_audit_log scene='moderation_shadow'}，与审核员
 *    频控同源做法），到限当日即停、日志标截断、统计接口返回 {@code dailyBudget.truncated=true}；
 * c) 统计接口在覆盖率低于 {@code coverage-warn-threshold}（默认 0.8）时返回
 *    {@code degraded=true} 与实际覆盖率，防低覆盖数字被当发生率用。
 *
 * <p><b>日志纪律</b>：只打对象 ID 与判定结果，不打内容原文/个人信息；原文只进加密的
 * {@code ai_audit_log}（scene 独立为 {@code moderation_shadow}，与审核员 {@code moderation}
 * 场景隔离，不污染其频控与证据链）。
 */
@Slf4j
@Service
public class ModerationShadowService {

    /** 影子判定审计场景（独立，避免污染审核员 moderation 场景的频控与统计）。 */
    static final String SCENE = "moderation_shadow";

    /** 「若开启」口径的允许枚举（含软档；窄门在 parse 内部按同口径执行）。 */
    static final Set<String> SOFT_SET = Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN", "SOFT_CONCERN");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate db;
    private final AiClient aiClient;
    private final AiAuditLogService auditLog;
    private final String promptTemplate;

    @Value("${zhiyu.moderation.soft-tier.shadow.enabled:false}")
    private boolean enabled;
    @Value("${zhiyu.moderation.soft-tier.shadow.lookback-hours:72}")
    private int lookbackHours;
    @Value("${zhiyu.moderation.soft-tier.shadow.batch-size:10}")
    private int batchSize;
    @Value("${zhiyu.moderation.soft-tier.shadow.max-attempts:3}")
    private int maxAttempts;
    @Value("${zhiyu.moderation.soft-tier.shadow.daily-budget:2000}")
    private int dailyBudget;
    @Value("${zhiyu.moderation.soft-tier.shadow.coverage-warn-threshold:0.8}")
    private double coverageWarnThreshold;

    /** 截断告警去重（单调度线程；重启后重打一次可接受）。 */
    private volatile LocalDate truncatedLoggedDate;

    public ModerationShadowService(JdbcTemplate db, AiClient aiClient, AiAuditLogService auditLog,
                                   @Qualifier("moderationSuggestPrompt") String promptTemplate) {
        this.db = db;
        this.aiClient = aiClient;
        this.auditLog = auditLog;
        this.promptTemplate = promptTemplate;
    }

    // ── 扫描与判定（写侧；开关关闭即整体空转） ──

    /** 影子判定扫描（每分钟，FileCleanupWorker 已全局 EnableScheduling）。 */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void scanAndJudge() {
        if (!enabled) {
            return;
        }
        try {
            int budgetLeft = dailyBudget - attemptsToday();
            if (budgetLeft <= 0) {
                logTruncatedOnce();
                return;
            }
            // 重试优先（样本越老越接近跳出回扫窗口），再判新送审；两类共享当日预算
            budgetLeft -= processRetries(Math.min(batchSize, budgetLeft));
            if (budgetLeft <= 0) {
                logTruncatedOnce();
                return;
            }
            budgetLeft -= processNewTargets("work", Math.min(batchSize, budgetLeft));
            if (budgetLeft > 0) {
                processNewTargets("comment", Math.min(batchSize, budgetLeft));
            } else {
                logTruncatedOnce();
            }
        } catch (Exception e) {
            log.error("软档影子扫描失败（下个周期重试）: {}", e.getMessage());
        }
    }

    /** 重试「未判成且未达次数上限」的记录（评审 a）。 */
    private int processRetries(int limit) {
        if (limit <= 0) {
            return 0;
        }
        var rows = db.queryForList("""
                SELECT id, target_type, target_id, attempts FROM moderation_soft_shadow
                WHERE judge_status='PENDING' AND attempts<? AND deleted=0
                ORDER BY last_attempt_at LIMIT ?
                """, maxAttempts, limit);
        int used = 0;
        for (var row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String targetType = String.valueOf(row.get("target_type"));
            long targetId = ((Number) row.get("target_id")).longValue();
            int attempts = ((Number) row.get("attempts")).intValue();
            var content = loadContent(targetType, targetId);
            if (content == null) { // 对象被物理删除（正常逻辑删除不影响）；达上限前保持 PENDING 口径，按失败计一次
                applyResult(id, targetType, targetId, null, attempts + 1);
                used++;
                continue;
            }
            applyResult(id, targetType, targetId, judgeOnce(targetType, targetId, content), attempts + 1);
            used++;
        }
        return used;
    }

    /** 新送审内容（回扫窗口内、无影子行；不做 deleted 过滤——注销/删除前送审的样本仍计入分母）。 */
    private int processNewTargets(String targetType, int limit) {
        if (limit <= 0) {
            return 0;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(lookbackHours);
        List<Map<String, Object>> rows;
        if ("work".equals(targetType)) {
            rows = db.queryForList("""
                    SELECT w.id, w.user_id, w.title, w.message, w.video_url, w.cover_url, w.create_time
                    FROM work w
                    WHERE w.create_time>=? AND NOT EXISTS (
                      SELECT 1 FROM moderation_soft_shadow s WHERE s.target_type='work' AND s.target_id=w.id)
                    ORDER BY w.id LIMIT ?
                    """, Timestamp.valueOf(since), limit);
        } else {
            rows = db.queryForList("""
                    SELECT c.id, c.user_id, c.content, c.create_time
                    FROM work_comment c
                    WHERE c.create_time>=? AND NOT EXISTS (
                      SELECT 1 FROM moderation_soft_shadow s WHERE s.target_type='comment' AND s.target_id=c.id)
                    ORDER BY c.id LIMIT ?
                    """, Timestamp.valueOf(since), limit);
        }
        int used = 0;
        for (var row : rows) {
            long targetId = ((Number) row.get("id")).longValue();
            long authorId = ((Number) row.get("user_id")).longValue();
            // DATETIME 行值走 JdbcTimes（驱动分叉统一出口，禁止裸强转——本类曾在真实库踩中 ClassCastException）
            LocalDateTime targetCreateTime = JdbcTimes.toLocalDateTime(row.get("create_time"));
            Content content = toContent(targetType, row);
            JudgeOutcome outcome = judgeOnce(targetType, targetId, content);
            insertRow(targetType, targetId, authorId, targetCreateTime, outcome);
            used++;
        }
        return used;
    }

    /** 单次「若开启」判定：渲染（强制软档开启口径）→ AiClient → 解析（含窄门）。 */
    private JudgeOutcome judgeOnce(String targetType, long targetId, Content content) {
        String prompt = AdminModerationService.render(promptTemplate, true,
                "work".equals(targetType) ? "作品" : "评论", content.title(), content.text(), content.mediaNote());
        var verdict = aiClient.moderationShadowSuggestWithAttribution(prompt);
        if (verdict == null) {
            auditLog.log(null, SCENE, prompt, null, "FALLBACK", "影子判定：AI 不可用或输出未过安全过滤", targetType, targetId);
            return null;
        }
        String raw = verdict.text();
        Map<String, Object> parsed = AdminModerationService.parse(raw, SOFT_SET);
        if (parsed == null) {
            auditLog.log(null, SCENE, prompt, raw, "FALLBACK", "影子判定：建议格式不可用", targetType, targetId);
            return null;
        }
        auditLog.log(null, SCENE, prompt, raw, "PASS", "影子判定（若开启口径，不影响线上）", targetType, targetId);
        return new JudgeOutcome(String.valueOf(parsed.get("tendency")), extractNarrative(raw),
                verdict.provider(), verdict.model());
    }

    /** 新样本落影子行（首次尝试即定 OK 或进入 PENDING/UNAVAILABLE）。 */
    private void insertRow(String targetType, long targetId, long authorId,
                           LocalDateTime targetCreateTime, JudgeOutcome outcome) {
        LocalDateTime now = LocalDateTime.now();
        long id = IdWorker.getId();
        String status = outcome != null ? "OK" : (1 >= maxAttempts ? "UNAVAILABLE" : "PENDING");
        db.update("""
                INSERT INTO moderation_soft_shadow
                (id,target_type,target_id,author_id,judge_status,tendency,narrative,audit_log_id,provider,model,attempts,last_attempt_at,target_create_time,create_time,update_time,deleted)
                VALUES(?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,0)
                """, id, targetType, targetId, authorId, status,
                outcome == null ? null : outcome.tendency(), outcome == null ? null : outcome.narrative(),
                lastAuditId(targetType, targetId),
                // 归属口径：只有「实际判成」才落真实 provider/model；失败样本保持列默认 unknown——
                // 不可用不代表任何模型判过，绝不把「最后尝试的端点」冒充归属
                outcome == null ? "unknown" : outcome.provider(),
                outcome == null ? "unknown" : outcome.model(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(targetCreateTime), Timestamp.valueOf(now), Timestamp.valueOf(now));
        if (outcome != null) {
            log.info("软档影子判定: targetType={}, targetId={}, tendency={}, provider={}, model={}",
                    targetType, targetId, outcome.tendency(), outcome.provider(), outcome.model());
        }
    }

    /** 重试结果落库：OK 终态；失败则 attempts+1，达上限落 UNAVAILABLE 终态，否则保持 PENDING。 */
    private void applyResult(long id, String targetType, long targetId, JudgeOutcome outcome, int attempts) {
        String status = outcome != null ? "OK" : (attempts >= maxAttempts ? "UNAVAILABLE" : "PENDING");
        if (outcome != null) {
            // 以最后一次实际判定为准：归属随成功判定覆盖（换端点重试成功的情形归属后者）；
            // 失败重试不动 provider/model（见下条 UPDATE），防「换了端点但都没判成」污染归属
            db.update("""
                    UPDATE moderation_soft_shadow
                    SET judge_status=?, tendency=?, narrative=?, audit_log_id=?, provider=?, model=?, attempts=?, last_attempt_at=?, update_time=?
                    WHERE id=?
                    """, status, outcome.tendency(), outcome.narrative(),
                    lastAuditId(targetType, targetId), outcome.provider(), outcome.model(), attempts,
                    Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), id);
            log.info("软档影子判定（重试）: shadowId={}, targetType={}, targetId={}, tendency={}, attempts={}",
                    id, targetType, targetId, outcome.tendency(), attempts);
        } else {
            db.update("""
                    UPDATE moderation_soft_shadow
                    SET judge_status=?, tendency=NULL, narrative=NULL, audit_log_id=?, attempts=?, last_attempt_at=?, update_time=?
                    WHERE id=?
                    """, status, lastAuditId(targetType, targetId), attempts,
                    Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), id);
            if ("UNAVAILABLE".equals(status)) {
                log.info("软档影子判定达重试上限落 UNAVAILABLE: shadowId={}, targetType={}, targetId={}, attempts={}",
                        id, targetType, targetId, attempts);
            }
        }
    }

    /** 重试时重新取内容（以库内当前文本为准，与生产 suggest 读行口径一致）。 */
    private Content loadContent(String targetType, long targetId) {
        List<Map<String, Object>> rows = "work".equals(targetType)
                ? db.queryForList("SELECT title,message,video_url,cover_url FROM work WHERE id=?", targetId)
                : db.queryForList("SELECT content FROM work_comment WHERE id=?", targetId);
        if (rows.isEmpty()) {
            return null;
        }
        return toContent(targetType, rows.get(0));
    }

    private static Content toContent(String targetType, Map<String, Object> row) {
        if ("work".equals(targetType)) {
            boolean hasMedia = row.get("video_url") != null || row.get("cover_url") != null;
            return new Content(str(row.get("title")), str(row.get("message")),
                    hasMedia ? "含图片或视频（AI 不可见，只能提示人工核对）" : "无");
        }
        return new Content("（评论，无标题）", str(row.get("content")), "无");
    }

    /** 窄门 narrative 原始枚举值留证（仅枚举 token；解析失败按 null，不阻断判定）。 */
    static String extractNarrative(String raw) {
        try {
            String text = raw.trim()
                    .replaceAll("(?s)^```(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*```$", "")
                    .trim();
            var node = JSON.readTree(text).path("narrative");
            return node.isTextual() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 当日已耗 AI 调用次数（评审 b 的计数源；与审核员频控同做法——直接数 ai_audit_log）。 */
    private int attemptsToday() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        Long c = db.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE scene=? AND create_time>=?",
                Long.class, SCENE, Timestamp.valueOf(dayStart));
        return c == null ? 0 : c.intValue();
    }

    private void logTruncatedOnce() {
        LocalDate today = LocalDate.now();
        if (!today.equals(truncatedLoggedDate)) {
            truncatedLoggedDate = today;
            log.warn("软档影子模式当日预算已用完，本日截断（剩余样本次日继续）: dailyBudget={}", dailyBudget);
        }
    }

    private Long lastAuditId(String targetType, long targetId) {
        return queryOne(
                "SELECT id FROM ai_audit_log WHERE scene=? AND ref_type=? AND ref_id=? ORDER BY id DESC LIMIT 1",
                Long.class, SCENE, targetType, targetId);
    }

    private <T> T queryOne(String sql, Class<T> type, Object... args) {
        var list = db.query(sql, (rs, i) -> rs.getObject(1, type), args);
        return list.isEmpty() ? null : list.get(0);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private record Content(String title, String text, String mediaNote) {
    }

    private record JudgeOutcome(String tendency, String narrative, String provider, String model) {
    }

    // ── 统计（读侧；不按开关 gated——关闭后仍需能读到此前采集的数据） ──

    /** 默认口径标识：排除演示来源，其余（含 unknown 存量）都算生产。 */
    public static final String DEFAULT_SCOPE = "production";

    /**
     * 演示来源清单（2026-11-07 反向排除口径）：单一集中定义处，可用
     * {@code ZHIYU_MODERATION_SOFT_TIER_SHADOW_DEMO_PROVIDERS} 覆盖（逗号分隔）。
     * 周报 SQL（sql/analytics/moderation-shadow-weekly.sql）因独立成文持有字面量副本，
     * 头部注释已声明以本配置为准——改清单需同步该文件。
     */
    @Value("${zhiyu.moderation.soft-tier.shadow.demo-providers:ollama,mock}")
    private String demoProvidersRaw;

    /** 演示来源集合（每次统计解析一次，量小不缓存，保证运行期改配置即生效）。 */
    private Set<String> demoProviders() {
        Set<String> out = new LinkedHashSet<>();
        for (String p : demoProvidersRaw.split(",")) {
            String v = p.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out.isEmpty() ? Set.of("ollama", "mock") : out;
    }

    /** 默认口径：排除演示来源的生产口径（unknown 存量计入）。 */
    public Map<String, Object> stats(LocalDate from, LocalDate to) {
        return stats(from, to, null);
    }

    /**
     * 影子统计（时间窗按对象送审时间 target_create_time，[from 00:00, to+1d 00:00)）。
     *
     * <p>口径：主指标 {@code softRate = soft/judged}（judged = judge_status='OK'）；
     * 同时返回 {@code coverageRate = judged/submitted}（submitted 直接 COUNT 业务表，
     * 含已逻辑删除——送审即分母，<b>不随 provider 过滤变化</b>）与保守下界
     * {@code softRateOnSubmitted = soft/submitted}；UNAVAILABLE 终态与 PENDING 待重试
     * 单独计数，不进分子分母。无害率：落软档记录实时 JOIN 业务表取人工终审 status
     * （1 通过=无害 / 2 驳回 / 0 未审结），{@code harmlessRate = approved/(approved+rejected)}。
     *
     * <p>provider 口径（2026-11-07 反向排除）：{@code providerScope} 缺省 = {@value #DEFAULT_SCOPE}
     * ——排除 {@code demo-providers}（ollama/mock，可配置），<b>其余全部计入，含 unknown 存量</b>
     * （存量行是云端时代的判定，且云端换供应商后不会被正向白名单静默归零）；
     * 传具体值（如演示的 ollama）单独取该归属；传 {@code "all"} 不过滤。
     * {@code byProvider} 为全窗不分 scope 的分桶（unknown 单独成桶可见）。
     * softRate / coverageRate / harmlessRate 的定义与分母口径、degraded 判定逻辑均不变，
     * 仅总体集合按 scope 过滤。
     *
     * <p><b>分子分母同源声明</b>：scope=all 时分子分母完全同源（与归属列出现前一致）；
     * 默认 production 口径分子排除演示判定行、分母仍含全部送审——窗口内存在演示判定时
     * 覆盖率会如实偏低（{@code demoJudgedInWindow} 字段给出被排除的演示判定条数，
     * 不需用户从数字里自己发现）；scope=具体 provider 时分子为该归属、分母为全部送审，
     * 是「该来源覆盖率」语义，同样以 demoJudgedInWindow 标注。
     */
    public Map<String, Object> stats(LocalDate from, LocalDate to, String providerScope) {
        String scope = (providerScope == null || providerScope.isBlank()) ? DEFAULT_SCOPE : providerScope.trim();
        Set<String> demoSet = demoProviders();
        // scopedProvider=null 表示不过滤；DEFAULT_SCOPE 走 NOT IN 排除
        boolean excludeDemo = DEFAULT_SCOPE.equalsIgnoreCase(scope);
        String scopedProvider = ("all".equalsIgnoreCase(scope) || excludeDemo) ? null : scope;
        ProviderFilter filter = excludeDemo ? new ProviderFilter(null, demoSet)
                : (scopedProvider == null ? new ProviderFilter(null, null) : new ProviderFilter(scopedProvider, null));
        Timestamp begin = Timestamp.valueOf(from.atStartOfDay());
        Timestamp end = Timestamp.valueOf(to.plusDays(1).atStartOfDay());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("providerScope", scope);
        out.put("excludedDemoProviders", excludeDemo ? List.copyOf(demoSet) : List.of());

        // 分母底座：业务表实际送审量（不看影子表，诚实覆盖率的前提；无 provider 维度，全量）
        long submittedWork = count("SELECT COUNT(*) FROM work WHERE create_time>=? AND create_time<?", begin, end);
        long submittedComment = count("SELECT COUNT(*) FROM work_comment WHERE create_time>=? AND create_time<?", begin, end);
        long submittedTotal = submittedWork + submittedComment;

        long judgedTotal = shadowCount("judge_status='OK'", null, filter, begin, end);
        long softTotal = shadowCount("judge_status='OK' AND tendency='SOFT_CONCERN'", null, filter, begin, end);
        long unavailableTotal = shadowCount("judge_status='UNAVAILABLE'", null, filter, begin, end);
        long pendingRetryTotal = shadowCount("judge_status='PENDING'", null, filter, begin, end);
        // 被排除的演示判定条数（分子分母同源标注：>0 时覆盖率如实偏低的原因在此，不让用户自己发现）
        out.put("demoJudgedInWindow", demoJudgedInWindow(demoSet, begin, end));

        out.put("submittedTotal", submittedTotal);
        out.put("judgedTotal", judgedTotal);
        out.put("unavailableTotal", unavailableTotal);
        out.put("pendingRetryTotal", pendingRetryTotal);
        Double coverage = submittedTotal == 0 ? null : judgedTotal / (double) submittedTotal;
        out.put("coverageRate", coverage);
        out.put("coverageWarnThreshold", coverageWarnThreshold);
        // 评审 c：覆盖不足显式降级，防低覆盖数字被当发生率
        out.put("degraded", coverage == null || coverage < coverageWarnThreshold);
        out.put("softTotal", softTotal);
        out.put("softRate", judgedTotal == 0 ? null : softTotal / (double) judgedTotal);
        out.put("softRateOnSubmitted", submittedTotal == 0 ? null : softTotal / (double) submittedTotal);

        // 无害率（落软档样本的人工终审分布，实时 JOIN 业务表）
        Map<String, Long> verdicts = humanVerdicts(filter, begin, end);
        long approved = verdicts.getOrDefault("approved", 0L);
        long rejected = verdicts.getOrDefault("rejected", 0L);
        long pending = verdicts.getOrDefault("pending", 0L);
        Map<String, Object> softReviewed = new LinkedHashMap<>();
        softReviewed.put("approved", approved);
        softReviewed.put("rejected", rejected);
        softReviewed.put("pending", pending);
        softReviewed.put("harmlessRate", approved + rejected == 0 ? null : approved / (double) (approved + rejected));
        out.put("softReviewed", softReviewed);

        // 分层明细：内容类型 / 校园（campus_verification 人工核验，未认证如实落「未认证」桶）
        List<Map<String, Object>> byType = new ArrayList<>();
        byType.add(typeRow("work", submittedWork, filter, begin, end));
        byType.add(typeRow("comment", submittedComment, filter, begin, end));
        out.put("byTargetType", byType);
        String schoolSql = """
                SELECT COALESCE(cv.school,'未认证') AS school, COUNT(*) AS judged,
                       SUM(CASE WHEN s.tendency='SOFT_CONCERN' THEN 1 ELSE 0 END) AS soft
                FROM moderation_soft_shadow s
                LEFT JOIN campus_verification cv ON cv.user_id=s.author_id AND cv.deleted=0
                WHERE s.judge_status='OK' AND s.deleted=0 AND s.target_create_time>=? AND s.target_create_time<?
                """ + filter.sql("s") + " GROUP BY COALESCE(cv.school,'未认证') ORDER BY judged DESC";
        out.put("bySchool", db.queryForList(schoolSql, filter.args(begin, end)).stream().map(r -> {
            long judged = ((Number) r.get("judged")).longValue();
            long soft = ((Number) r.get("soft")).longValue();
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("school", r.get("school"));
            m.put("judged", judged);
            m.put("soft", soft);
            m.put("softRate", judged == 0 ? null : soft / (double) judged);
            return m;
        }).toList());

        // provider 分桶（附加字段；全窗不分 scope——unknown/演示归属在此单独成桶可见）
        out.put("byProvider", db.queryForList("""
                SELECT s.provider AS provider, COUNT(*) AS rows_total,
                       SUM(CASE WHEN s.judge_status='OK' THEN 1 ELSE 0 END) AS judged,
                       SUM(CASE WHEN s.judge_status='OK' AND s.tendency='SOFT_CONCERN' THEN 1 ELSE 0 END) AS soft
                FROM moderation_soft_shadow s
                WHERE s.deleted=0 AND s.target_create_time>=? AND s.target_create_time<?
                GROUP BY s.provider ORDER BY rows_total DESC
                """, begin, end).stream().map(r -> {
            long judged = ((Number) r.get("judged")).longValue();
            long soft = ((Number) r.get("soft")).longValue();
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("provider", r.get("provider"));
            m.put("rows", ((Number) r.get("rows_total")).longValue());
            m.put("judged", judged);
            m.put("soft", soft);
            m.put("softRate", judged == 0 ? null : soft / (double) judged);
            return m;
        }).toList());

        // 评审 b：日预算用量与截断标记（全局 AI 支出计数，不分 provider）
        int usedToday = attemptsToday();
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("limit", dailyBudget);
        budget.put("usedToday", usedToday);
        budget.put("truncated", usedToday >= dailyBudget);
        out.put("dailyBudget", budget);
        return out;
    }

    private Map<String, Object> typeRow(String targetType, long submitted, ProviderFilter filter, Timestamp begin, Timestamp end) {
        long judged = shadowCount("judge_status='OK'", targetType, filter, begin, end);
        long soft = shadowCount("judge_status='OK' AND tendency='SOFT_CONCERN'", targetType, filter, begin, end);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetType", targetType);
        m.put("submitted", submitted);
        m.put("judged", judged);
        m.put("soft", soft);
        m.put("softRate", judged == 0 ? null : soft / (double) judged);
        return m;
    }

    /** 落软档样本的人工终审分布（work.status / work_comment.status：0 待审 1 通过 2 驳回；无物理删除故 JOIN 不丢行）。 */
    private Map<String, Long> humanVerdicts(ProviderFilter filter, Timestamp begin, Timestamp end) {
        Map<String, Long> out = new LinkedHashMap<>(Map.of("approved", 0L, "rejected", 0L, "pending", 0L));
        for (String type : List.of("work", "comment")) {
            String hotTable = "work".equals(type) ? "work" : "work_comment";
            String sql = """
                    SELECT t.status AS st, COUNT(*) AS c
                    FROM moderation_soft_shadow s JOIN """ + " " + hotTable + """
                     t ON t.id=s.target_id
                    WHERE s.target_type=? AND s.judge_status='OK' AND s.tendency='SOFT_CONCERN' AND s.deleted=0
                      AND s.target_create_time>=? AND s.target_create_time<?
                    """ + filter.sql("s") + " GROUP BY t.status";
            for (var r : db.queryForList(sql, filter.args(type, begin, end))) {
                int st = ((Number) r.get("st")).intValue();
                long c = ((Number) r.get("c")).longValue();
                if (st == 1) {
                    out.merge("approved", c, Long::sum);
                } else if (st == 2) {
                    out.merge("rejected", c, Long::sum);
                } else {
                    out.merge("pending", c, Long::sum);
                }
            }
        }
        return out;
    }

    /** 窗口内被默认口径排除的演示判定条数（judge_status='OK' 且归属在演示清单内）。 */
    private long demoJudgedInWindow(Set<String> demoSet, Timestamp begin, Timestamp end) {
        if (demoSet.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(demoSet.size(), "?"));
        var args = new ArrayList<Object>(List.of(begin, end));
        args.addAll(demoSet);
        Long c = db.queryForObject("SELECT COUNT(*) FROM moderation_soft_shadow"
                + " WHERE judge_status='OK' AND deleted=0 AND target_create_time>=? AND target_create_time<?"
                + " AND provider IN (" + placeholders + ")", Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    private long shadowCount(String where, String targetType, ProviderFilter filter, Timestamp begin, Timestamp end) {
        String sql = "SELECT COUNT(*) FROM moderation_soft_shadow WHERE deleted=0 AND " + where
                + " AND target_create_time>=? AND target_create_time<?"
                + (targetType == null ? "" : " AND target_type='" + targetType + "'")
                + filter.sql("moderation_soft_shadow");
        Long c = db.queryForObject(sql, Long.class, filter.args(begin, end));
        return c == null ? 0 : c;
    }

    /**
     * provider 过滤（全程参数化，不拼接用户输入）：exact=精确归属（?provider=具体值）；
     * exclude=反向排除演示清单（默认 production 口径）；两者 null=不过滤（all）。
     */
    private record ProviderFilter(String exact, Set<String> exclude) {
        String sql(String alias) {
            if (exact != null) {
                return " AND " + alias + ".provider=?";
            }
            if (exclude != null && !exclude.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < exclude.size(); i++) {
                    sb.append(" AND ").append(alias).append(".provider<>?");
                }
                return sb.toString();
            }
            return "";
        }

        Object[] args(Object... base) {
            var list = new ArrayList<>(List.of(base));
            if (exact != null) {
                list.add(exact);
            } else if (exclude != null) {
                list.addAll(exclude);
            }
            return list.toArray();
        }
    }

    private long count(String sql, Object... args) {
        Long c = db.queryForObject(sql, Long.class, args);
        return c == null ? 0 : c;
    }
}
