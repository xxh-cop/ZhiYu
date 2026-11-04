package com.zhiyu.modules.dialogue;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.ai.AiClient;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.companion.CompanionTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 对话上下文服务（P1b 2026-11-04，仅倾听链；rolling-summary 默认关闭）。
 *
 * <p>职责（docs/dialogue-unification-20261104.md §3、§9.3）：
 * <ul>
 *   <li>原文窗口：最近 {@code zhiyu.emotion.max-history-turns} 条消息（窗口本身仍由
 *       {@code EmotionController} 加载，本服务不替代原文窗口）；</li>
 *   <li>滚动摘要：第 triggerTurn 条消息起，每滑出 increment 条，把「已滑出原文窗口的
 *       最旧若干条」增量压缩进摘要；摘要在服务端生成与存储（AesEncryptor 应用层加密），
 *       <b>服务端是上下文的唯一权威，不接受客户端拼装上下文</b>；</li>
 *   <li>摘要 TTL：update_time 超过 ttl_days 视为过期，读取时按无摘要处理；</li>
 *   <li>降级：摘要生成失败（AiClient 返回 null）→ 本轮不做摘要、保留原文窗口并记
 *       {@code ai_trace(scene=dialogue-summary, result_status=FALLBACK)}，对话主链路不受影响。</li>
 * </ul>
 *
 * <p>「轮」与本系统历史口径一致 = 一条消息（emotion_message 按 id 升序的序号，从 1 计）。
 * 开关 {@code zhiyu.dialogue.rolling-summary} 为 false 时，本服务所有方法零副作用
 * （调用方在控制器层短路，根本不进本服务）。
 */
@Slf4j
@Service
public class DialogueContextService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String TRACE_SCENE = "dialogue-summary";
    private static final String PROMPT_VERSION = "context-summary.st#2026-11-04";

    private final JdbcTemplate db;
    private final AiClient aiClient;
    private final AesEncryptor crypto;
    private final CompanionTraceService trace;
    private final String template;
    private final boolean enabled;
    private final int windowTurns;
    private final int triggerTurn;
    private final int increment;
    private final int ttlDays;

    public DialogueContextService(
            JdbcTemplate db,
            AiClient aiClient,
            AesEncryptor crypto,
            CompanionTraceService trace,
            @Qualifier("contextSummaryPrompt") String template,
            @Value("${zhiyu.dialogue.rolling-summary:false}") boolean enabled,
            @Value("${zhiyu.emotion.max-history-turns:10}") int windowTurns,
            @Value("${zhiyu.dialogue.summary-trigger-turn:11}") int triggerTurn,
            @Value("${zhiyu.dialogue.summary-increment-turns:5}") int increment,
            @Value("${zhiyu.dialogue.summary-ttl-days:90}") int ttlDays) {
        this.db = db;
        this.aiClient = aiClient;
        this.crypto = crypto;
        this.trace = trace;
        this.template = template;
        this.enabled = enabled;
        this.windowTurns = windowTurns;
        this.triggerTurn = triggerTurn;
        this.increment = increment;
        this.ttlDays = ttlDays;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * 供倾听链注入的上下文前缀（含轮次范围标注）；开关关闭、无摘要或摘要过期时返回 null，
     * 调用方按旧版行为处理（不使用摘要）。
     */
    public String summaryPrefix(long userId, long sessionId) {
        if (!enabled) {
            return null;
        }
        var rows = db.queryForList(
                "SELECT summary_cipher, covered_start_turn, covered_end_turn, update_time "
                        + "FROM dialogue_context_summary WHERE user_id=? AND session_id=? AND deleted=0 "
                        + "ORDER BY id DESC LIMIT 1", userId, sessionId);
        if (rows.isEmpty()) {
            return null;
        }
        var row = rows.get(0);
        // 类型安全：MySQL Connector/J 8 把 DATETIME 映射为 LocalDateTime，H2 映射为 Timestamp，
        // 强转 Timestamp 会在 MySQL 上 ClassCastException（W5 真实验证抓到，2026-11-04 修复）
        LocalDateTime updateTime = toLocalDateTime(row.get("update_time"));
        if (updateTime.isBefore(LocalDateTime.now(ZONE).minusDays(ttlDays))) {
            return null;
        }
        String text = crypto.decrypt((String) row.get("summary_cipher"));
        return "【早前对话摘要（第" + row.get("covered_start_turn") + "-"
                + row.get("covered_end_turn") + "轮，供上下文连续参考，不代表用户原话）】\n" + text;
    }

    /**
     * 会话新消息落库后调用：到达增量边界则把新滑出窗口的若干轮压缩进摘要。
     * 任何失败内部消化（记 WARN + trace），绝不向对话主链路抛出。
     */
    @Transactional
    public void maybeRoll(long userId, long sessionId) {
        if (!enabled) {
            return;
        }
        long started = System.currentTimeMillis();
        try {
            // 串行化同会话的滚动（防并发双开同一 SSE 产生重复摘要行）
            db.queryForObject("SELECT id FROM emotion_session WHERE id=? FOR UPDATE", Long.class, sessionId);
            int total = db.queryForObject(
                    "SELECT COUNT(*) FROM emotion_message WHERE session_id=? AND deleted=0",
                    Integer.class, sessionId);
            int outside = total - windowTurns;
            if (outside <= 0) {
                return;
            }
            var rows = db.queryForList(
                    "SELECT id, covered_start_turn, covered_end_turn, summary_cipher "
                            + "FROM dialogue_context_summary WHERE user_id=? AND session_id=? AND deleted=0 "
                            + "ORDER BY id DESC LIMIT 1", userId, sessionId);
            int covered = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("covered_end_turn")).intValue();
            int pending = outside - covered;
            if (total < triggerTurn || pending < increment) {
                return;
            }
            // 只压缩 increment 的整数倍，零头留到下一轮
            int end = covered + (pending / increment) * increment;
            String turnsBlock = loadTurns(sessionId, covered + 1, end);
            if (turnsBlock.isBlank()) {
                return;
            }
            String previous = rows.isEmpty() ? null
                    : crypto.decrypt((String) rows.get(0).get("summary_cipher"));
            String prompt = render(previous, covered + 1, end, turnsBlock);
            String summary = aiClient.contextSummary(prompt);
            int latency = (int) (System.currentTimeMillis() - started);
            if (summary == null) {
                trace.record(userId, null, TRACE_SCENE, aiClient.modelName(), PROMPT_VERSION,
                        null, latency, "FALLBACK", "SUMMARY_FAILED", null, null);
                log.info("滚动摘要本轮降级（保留原文窗口）: sessionId={}, covered+={}", sessionId, pending);
                return;
            }
            int start = rows.isEmpty() ? covered + 1
                    : ((Number) rows.get(0).get("covered_start_turn")).intValue();
            LocalDateTime now = LocalDateTime.now(ZONE);
            if (rows.isEmpty()) {
                db.update("INSERT INTO dialogue_context_summary"
                                + "(id,user_id,session_id,covered_start_turn,covered_end_turn,summary_cipher,"
                                + " model,prompt_version,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,?,?,?,?,0)",
                        IdWorker.getId(), userId, sessionId, start, end,
                        crypto.encrypt(summary), aiClient.modelName(), PROMPT_VERSION, now, now);
            } else {
                db.update("UPDATE dialogue_context_summary SET covered_end_turn=?, summary_cipher=?,"
                                + " model=?, prompt_version=?, update_time=? WHERE id=?",
                        end, crypto.encrypt(summary), aiClient.modelName(), PROMPT_VERSION, now,
                        ((Number) rows.get(0).get("id")).longValue());
            }
            trace.record(userId, null, TRACE_SCENE, aiClient.modelName(), PROMPT_VERSION,
                    null, latency, "OK", null, null, null);
            log.info("滚动摘要已更新: sessionId={}, covered {}-{}", sessionId, start, end);
        } catch (Exception e) {
            log.warn("滚动摘要失败（已消化，不影响对话）: sessionId={}, err={}", sessionId, e.getMessage());
            try {
                trace.record(userId, null, TRACE_SCENE, aiClient.modelName(), PROMPT_VERSION,
                        null, (int) (System.currentTimeMillis() - started), "FALLBACK", "SUMMARY_ERROR", null, null);
            } catch (Exception ignored) {
                // trace 失败不再放大
            }
        }
    }

    /** 按消息序号（1 起，id 升序）加载 [startTurn, endTurn] 的原文并渲染为可读块。 */
    private String loadTurns(long sessionId, int startTurn, int endTurn) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT role, content FROM emotion_message WHERE session_id=? AND deleted=0 "
                        + "ORDER BY create_time, id LIMIT ? OFFSET ?",
                sessionId, endTurn - startTurn + 1, startTurn - 1);
        StringBuilder sb = new StringBuilder();
        int turn = startTurn;
        for (var row : rows) {
            String role = "user".equals(row.get("role")) ? "用户" : "小织";
            sb.append("第").append(turn++).append("轮 ").append(role).append("：")
                    .append(crypto.decrypt((String) row.get("content"))).append('\n');
        }
        return sb.toString();
    }

    /** JDBC 时间列 → LocalDateTime（兼容 MySQL 的 LocalDateTime 与 H2 的 Timestamp 映射差异）。 */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime t) {
            return t;
        }
        if (value instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (value instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZONE);
        }
        throw new IllegalStateException("无法识别的时间列类型: " + (value == null ? "null" : value.getClass()));
    }

    private String render(String previous, int startTurn, int endTurn, String turnsBlock) {
        String previousBlock = (previous == null || previous.isBlank())
                ? "（当前没有旧摘要，本次直接生成。）"
                : "旧摘要（请并入）：\n" + previous;
        return template
                .replace("{previousSummaryBlock}", previousBlock)
                .replace("{startTurn}", String.valueOf(startTurn))
                .replace("{endTurn}", String.valueOf(endTurn))
                .replace("{turnsBlock}", turnsBlock);
    }
}
