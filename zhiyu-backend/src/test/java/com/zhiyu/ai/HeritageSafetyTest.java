package com.zhiyu.ai;

import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HeritageSafetyTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final CircuitBreakerStore breaker = mock(CircuitBreakerStore.class);
    private final ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
    private final AiClient ai = new AiClient(llm, breaker, filter);

    @Test
    void inputBlockStopsModelCall() {
        when(filter.checkInput(any(), any(), eq("heritage"), any())).thenReturn(SafetyResult.block("test", "care"));
        assertNull(ai.heritageVariants("prompt", "主题"));
        verifyNoInteractions(llm);
    }

    @Test
    void openCircuitDoesNotCallProvider() {
        when(filter.checkInput(any(), any(), eq("heritage"), any())).thenReturn(SafetyResult.pass("ok"));
        assertNull(ai.heritageVariants("prompt", "主题"));
        verifyNoInteractions(llm);
    }

    @Test
    void rewrittenOutputIsNotTreatedAsJson() {
        when(filter.checkInput(any(), any(), eq("heritage"), any())).thenReturn(SafetyResult.pass("ok"));
        when(breaker.allowRequest("llm-lineart")).thenReturn(true);
        when(llm.chat("prompt")).thenReturn("{\"variants\":[]}");
        when(filter.checkOutput(any(), eq("heritage"), any()))
                .thenReturn(SafetyResult.rewrite("test", "safe"));
        assertNull(ai.heritageVariants("prompt", "主题"));
        verify(breaker).recordSuccess("llm-lineart");
    }

    @Test
    void passingOutputIsReturnedRawForServiceValidation() {
        when(filter.checkInput(any(), any(), eq("heritage"), any())).thenReturn(SafetyResult.pass("ok"));
        when(breaker.allowRequest("llm-lineart")).thenReturn(true);
        when(llm.chat("prompt")).thenReturn("{\"variants\":[]}");
        when(filter.checkOutput(any(), eq("heritage"), any())).thenReturn(SafetyResult.pass("ok"));
        assertEquals("{\"variants\":[]}", ai.heritageVariants("prompt", "主题"));
    }

    @Test
    void emptyAnswerRecordsFailure() {
        when(filter.checkInput(any(), any(), eq("heritage"), any())).thenReturn(SafetyResult.pass("ok"));
        when(breaker.allowRequest("llm-lineart")).thenReturn(true);
        when(llm.chat("prompt")).thenReturn(" ");
        assertNull(ai.heritageVariants("prompt", "主题"));
        verify(breaker).recordFailure("llm-lineart");
    }
}
