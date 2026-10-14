package com.zhiyu.ai;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class LineartSafetyTest {
 @Test void inputBlockStopsGeneration(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);var filter=mock(ContentSafetyFilter.class);when(filter.checkInput(any(),any(),eq("lineart"),anyString())).thenReturn(SafetyResult.block("test","care"));assertThrows(BusinessException.class,()->new AiClient(llm,breaker,filter).lineartPlan("input","prompt"));verifyNoInteractions(llm);}
 @Test void outputRewriteIsNotUsedAsGeometry(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);var filter=mock(ContentSafetyFilter.class);when(filter.checkInput(any(),any(),eq("lineart"),anyString())).thenReturn(SafetyResult.pass("ok"));when(breaker.allowRequest("llm-lineart")).thenReturn(true);when(llm.chat("prompt")).thenReturn("text");when(filter.checkOutput(any(),eq("lineart"),eq("text"))).thenReturn(SafetyResult.rewrite("test","safe"));assertFalse(new AiClient(llm,breaker,filter).lineartPlan("input","prompt").generated());verify(breaker).recordSuccess("llm-lineart");}
 @Test void openCircuitDoesNotCallProvider(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);var filter=mock(ContentSafetyFilter.class);when(filter.checkInput(any(),any(),eq("lineart"),anyString())).thenReturn(SafetyResult.pass("ok"));assertFalse(new AiClient(llm,breaker,filter).lineartPlan("input","prompt").generated());verifyNoInteractions(llm);}
}
