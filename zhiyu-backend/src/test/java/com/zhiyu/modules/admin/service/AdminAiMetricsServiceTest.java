package com.zhiyu.modules.admin.service;

import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdminAiMetricsService} 集成单测（BO 批，2026-11-05，H2 内存库）。
 *
 * <p>隔离说明：本类与其他 @SpringBootTest 类共享测试上下文与同一个 H2 内存库，
 * 而门面指标是旁路异步写库（独立连接，不被 @Transactional 回滚）——
 * 其他用例触发的真实 ai_trace 行可能落在断言窗口内。因此本类一律使用
 * 独有的 {@code bo66-*} 场景名做断言，空窗口用历史区间，与执行顺序无关。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAiMetricsServiceTest {

    /** 本类专用场景名前缀：避开其他测试门面埋点写入的真实资源 key。 */
    private static final String CHAT = "bo66-chat";
    private static final String ASR = "bo66-asr";
    private static final String RAG = "bo66-rag";
    private static final String ERR = "bo66-err";

    @Autowired
    private AdminAiMetricsService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long rowId = 900_000_000_000L;

    private void insertTrace(String scene, String model, Integer latencyMs,
                             Integer inputTokens, Integer outputTokens,
                             String status, String reason, LocalDateTime createTime) {
        jdbc.update("""
                        INSERT INTO ai_trace(id, trace_id, user_id, scene, model,
                            latency_ms, input_tokens, output_tokens, result_status, fallback_reason, create_time)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?)
                        """, rowId++, java.util.UUID.randomUUID().toString(), 7001L, scene, model,
                latencyMs, inputTokens, outputTokens, status, reason, createTime);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sceneOf(Map<String, Object> result, String scene) {
        return ((List<Map<String, Object>>) result.get("scenes")).stream()
                .filter(row -> scene.equals(row.get("scene")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少场景聚合行: " + scene));
    }

    @Test
    @DisplayName("按资源 key 聚合计数、成功率、降级率与 P50/P95（happy path）")
    void aggregatesByScene() {
        LocalDateTime now = LocalDateTime.now();
        // bo66-chat：3 OK（100/200/300ms，tokens 10/20/30）+ 1 FALLBACK（50ms）+ 1 ERROR（无延迟）
        insertTrace(CHAT, "deepseek-chat", 100, 10, 5, "OK", null, now.minusHours(3));
        insertTrace(CHAT, "deepseek-chat", 200, 20, 6, "OK", null, now.minusHours(2));
        insertTrace(CHAT, "deepseek-chat", 300, 30, 7, "OK", null, now.minusHours(1));
        insertTrace(CHAT, "deepseek-chat", 50, null, null, "FALLBACK", "BREAKER_OPEN", now.minusMinutes(30));
        insertTrace(CHAT, "deepseek-chat", null, null, null, "ERROR", "PROVIDER_ERROR", now.minusMinutes(10));
        // bo66-asr：1 OK
        insertTrace(ASR, "QianwenSpeechClient", 400, null, null, "OK", null, now.minusHours(1));

        Map<String, Object> result = service.metrics(now.minusDays(1), now.plusHours(1));

        List<?> scenes = (List<?>) result.get("scenes");
        assertTrue(scenes.size() >= 2, "至少包含本类写入的两个场景");

        Map<String, Object> chat = sceneOf(result, CHAT);
        assertEquals(5L, ((Number) chat.get("calls")).longValue());
        assertEquals(3L, ((Number) chat.get("okCount")).longValue());
        assertEquals(1L, ((Number) chat.get("fallbackCount")).longValue());
        assertEquals(1L, ((Number) chat.get("errorCount")).longValue());
        assertEquals(60.0, (Double) chat.get("successRate"), 0.01);
        assertEquals(20.0, (Double) chat.get("fallbackRate"), 0.01);
        assertEquals(20.0, (Double) chat.get("errorRate"), 0.01);
        // tokens：仅记录了的行参与求和（10+20+30 / 5+6+7）
        assertEquals(60L, ((Number) chat.get("inputTokens")).longValue());
        assertEquals(18L, ((Number) chat.get("outputTokens")).longValue());
        // 延迟样本 [50,100,200,300]：avg=163（加权 650/4 四舍五入），P50=100，P95=300
        assertEquals(163L, ((Number) chat.get("avgLatencyMs")).longValue());
        assertEquals(100, ((Number) chat.get("p50LatencyMs")).intValue());
        assertEquals(300, ((Number) chat.get("p95LatencyMs")).intValue());
        assertEquals(List.of("deepseek-chat"), chat.get("models"));

        Map<String, Object> asr = sceneOf(result, ASR);
        assertEquals(1L, ((Number) asr.get("calls")).longValue());
        assertEquals(100.0, (Double) asr.get("successRate"), 0.01);
        assertEquals(400, ((Number) asr.get("p50LatencyMs")).intValue());
    }

    @Test
    @DisplayName("时间区间外的记录不参与聚合")
    void excludesRowsOutsideWindow() {
        LocalDateTime now = LocalDateTime.now();
        insertTrace(RAG, "deepseek-chat", 100, null, null, "OK", null, now.minusDays(10));
        insertTrace(RAG, "deepseek-chat", 200, null, null, "OK", null, now.minusHours(1));

        Map<String, Object> result = service.metrics(now.minusDays(1), now.plusHours(1));
        Map<String, Object> rag = sceneOf(result, RAG);
        assertEquals(1L, ((Number) rag.get("calls")).longValue(), "10 天前的记录不应计入");
        assertEquals(200, ((Number) rag.get("p50LatencyMs")).intValue());
    }

    @Test
    @DisplayName("空区间返回空 scenes，不伪造数据")
    void emptyWindowReturnsEmptyScenes() {
        // 历史区间（2020 年）：任何测试的异步埋点都不可能落在里面
        Map<String, Object> result = service.metrics(
                LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 1, 2, 0, 0));
        assertEquals(0, ((List<?>) result.get("scenes")).size());
    }

    @Test
    @DisplayName("from 不早于 to 抛 1001")
    void rejectsInvertedRange() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.metrics(now, now.minusHours(1)));
        assertEquals(1001, e.getCode());
    }

    @Test
    @DisplayName("区间超过 90 天抛 1001")
    void rejectsOverlongRange() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.metrics(now.minusDays(91), now));
        assertEquals(1001, e.getCode());
    }

    @Test
    @DisplayName("缺省参数取最近 7 天")
    void defaultsToLastSevenDays() {
        Map<String, Object> result = service.metrics(null, null);
        Map<?, ?> window = (Map<?, ?>) result.get("window");
        assertNotNull(window.get("from"));
        assertNotNull(window.get("to"));
        LocalDateTime from = LocalDateTime.parse(window.get("from").toString());
        LocalDateTime to = LocalDateTime.parse(window.get("to").toString());
        long days = java.time.Duration.between(from, to).toDays();
        assertTrue(days >= 6 && days <= 7, "缺省窗口应为最近 7 天");
    }

    @Test
    @DisplayName("无延迟样本时百分位为 null 而非 0")
    void nullPercentilesWhenNoLatencySamples() {
        LocalDateTime now = LocalDateTime.now();
        insertTrace(ERR, "deepseek-chat", null, null, null, "ERROR", "PROVIDER_ERROR", now);
        Map<String, Object> result = service.metrics(now.minusDays(1), now.plusHours(1));
        Map<String, Object> chat = sceneOf(result, ERR);
        assertNull(chat.get("p50LatencyMs"));
        assertNull(chat.get("p95LatencyMs"));
        assertNull(chat.get("avgLatencyMs"));
    }
}
