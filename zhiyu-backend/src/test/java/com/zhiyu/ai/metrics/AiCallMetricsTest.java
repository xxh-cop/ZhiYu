package com.zhiyu.ai.metrics;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiCallMetrics} 单测（BO 批，2026-11-05）。
 */
class AiCallMetricsTest {

    @Test
    void writesNormalizedMetadataOnly() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var metrics = new AiCallMetrics(db);

        metrics.write(7L, "llm-chat", "deepseek-chat", "deepseek", 820L,
                AiCallMetrics.OK, null, 12, 8);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(contains("INSERT INTO ai_trace"), args.capture());
        String values = Arrays.toString(args.getValue());
        assertTrue(values.contains("llm-chat"), "应记录资源 key");
        assertTrue(values.contains("deepseek-chat"), "应记录模型标识");
        assertTrue(values.contains("deepseek"), "应记录 provider 归属（2026-11-08 装配层批）");
        assertTrue(values.contains("OK"), "应记录结果状态");
        assertTrue(values.contains("820"), "应记录延迟");
        assertTrue(values.contains("12"), "应记录入参 tokens");
        assertTrue(values.contains("8"), "应记录出参 tokens");
        metrics.shutdown();
    }

    @Test
    void writeFailureNeverBreaksBusiness() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));
        var metrics = new AiCallMetrics(db);

        assertDoesNotThrow(() -> metrics.write(7L, "llm-recommend", "deepseek-chat", null, 50L,
                AiCallMetrics.FALLBACK, AiCallMetrics.BREAKER_OPEN, null, null));
        metrics.shutdown();
    }

    @Test
    void recordIsAsyncAndDoesNotBlockCaller() {
        JdbcTemplate slow = mock(JdbcTemplate.class);
        when(slow.update(anyString(), any(Object[].class)))
                .thenAnswer(inv -> {
                    Thread.sleep(2000);
                    return 1;
                });
        var metrics = new AiCallMetrics(slow);

        // 慢 2s 的写库桩下 record 必须立即返回（主流程不被指标拖慢）
        assertTimeoutPreemptively(java.time.Duration.ofMillis(500), () ->
                metrics.record(7L, "llm-message", "deepseek-chat", 100L,
                        AiCallMetrics.OK, null, null, null));
        metrics.shutdown();
    }

    @Test
    void noopNeverTouchesDatabase() {
        var metrics = AiCallMetrics.noop();
        // noop 持有 null JdbcTemplate：任何写库尝试都会 NPE，这里无异常即证明未触库
        assertDoesNotThrow(() -> metrics.record(1L, "llm-chat", "mock", 1L,
                AiCallMetrics.OK, null, null, null));
        assertDoesNotThrow(metrics::shutdown);
    }

    @Test
    void overlongFieldsAreCutToColumnWidth() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var metrics = new AiCallMetrics(db);

        metrics.write(1L, "x".repeat(40), "y".repeat(100), "p".repeat(30), 0L,
                AiCallMetrics.ERROR, "z".repeat(200), null, null);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(anyString(), args.capture());
        Object[] values = args.getValue();
        assertTrue(((String) values[3]).length() <= 30, "scene 截断到 VARCHAR(30)");
        assertTrue(((String) values[4]).length() <= 80, "model 截断到 VARCHAR(80)");
        assertTrue(((String) values[5]).length() <= 20, "provider 截断到 VARCHAR(20)");
        assertTrue(((String) values[10]).length() <= 120, "fallback_reason 截断到 VARCHAR(120)");
        metrics.shutdown();
    }
}
