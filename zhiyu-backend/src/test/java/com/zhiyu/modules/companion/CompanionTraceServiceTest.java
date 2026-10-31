package com.zhiyu.modules.companion;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionTraceServiceTest {

    @Test
    void recordsNormalizedMetadataOnly() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var service = new CompanionTraceService(db);

        service.record(7L, 91L, "companion", "deepseek-chat", "companion-plan-v1",
                "recommendPractice", 120, "OK", null, 12, 8);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(contains("INSERT INTO ai_trace"), args.capture());
        String values = Arrays.toString(args.getValue());
        assertTrue(values.contains("companion"));
        assertTrue(values.contains("recommendPractice"));
        assertTrue(values.contains("OK"));
        assertTrue(values.contains("12"));
        assertTrue(values.contains("8"));
    }

    @Test
    void traceFailureNeverBreaksUserTurn() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));
        var service = new CompanionTraceService(db);

        assertDoesNotThrow(() -> service.record(7L, 91L, "companion", "MOCK",
                "companion-plan-v1", null, 10, "FALLBACK", "MODEL_FALLBACK"));
    }
}
