package com.zhiyu.ai;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class KnowledgeAnswerTest {
 @Test void blockedQuestionNeverReachesModel(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);var safety=mock(ContentSafetyFilter.class);when(safety.checkInput(any(),any(),eq("rag"),anyString())).thenReturn(SafetyResult.block("test","请先寻求支持"));assertEquals("请先寻求支持",new AiClient(llm,breaker,safety).knowledgeAnswer("test","prompt"));verifyNoInteractions(llm);}
 @Test void answerIsAuditedAndRewritten(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);var safety=mock(ContentSafetyFilter.class);when(safety.checkInput(any(),any(),eq("rag"),anyString())).thenReturn(SafetyResult.pass("pass"));when(breaker.allowRequest("llm-rag")).thenReturn(true);when(llm.chat("prompt")).thenReturn("unsafe answer");when(safety.checkOutput(any(),eq("rag"),anyString())).thenReturn(SafetyResult.rewrite("test","安全说明"));assertEquals("安全说明",new AiClient(llm,breaker,safety).knowledgeAnswer("question","prompt"));verify(breaker).recordSuccess("llm-rag");}
 @Test void providerFailureUsesFixedFallback(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);var safety=mock(ContentSafetyFilter.class);when(safety.checkInput(any(),any(),eq("rag"),anyString())).thenReturn(SafetyResult.pass("pass"));when(breaker.allowRequest("llm-rag")).thenReturn(true);when(llm.chat(anyString())).thenThrow(new IllegalStateException());assertTrue(new AiClient(llm,breaker,safety).knowledgeAnswer("question","prompt").contains("暂时不可用"));verify(breaker).recordFailure("llm-rag");}
}
