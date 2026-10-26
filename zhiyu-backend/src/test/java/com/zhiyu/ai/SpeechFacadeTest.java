package com.zhiyu.ai;
import com.zhiyu.ai.client.*;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.*;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpeechFacadeTest {
 @Test void disabledProviderDoesNotRun(){var speech=mock(SpeechClient.class);var ai=new AiClient(mock(LlmClient.class),mock(CircuitBreakerStore.class),mock(ContentSafetyFilter.class));ai.setSpeechClient(speech);assertThrows(BusinessException.class,()->ai.transcribe(new byte[2],"audio/mpeg"));verify(speech,never()).transcribe(any(),any());}
 @Test void speechFailureUsesAsrBreakerOnly(){var speech=mock(SpeechClient.class);var breaker=mock(CircuitBreakerStore.class);when(speech.available()).thenReturn(true);when(breaker.allowRequest("asr")).thenReturn(true);when(speech.transcribe(any(),any())).thenThrow(new BusinessException(4001,"暂不可用"));var ai=new AiClient(mock(LlmClient.class),breaker,mock(ContentSafetyFilter.class));ai.setSpeechClient(speech);assertThrows(BusinessException.class,()->ai.transcribe(new byte[2],"audio/mpeg"));verify(breaker).recordFailure("asr");verify(breaker,never()).recordFailure("tts");}
 @Test void asrDangerousTextIsNotReturned(){var speech=mock(SpeechClient.class);var breaker=mock(CircuitBreakerStore.class);var safety=mock(ContentSafetyFilter.class);when(speech.available()).thenReturn(true);when(breaker.allowRequest("asr")).thenReturn(true);when(speech.transcribe(any(),any())).thenReturn("unsafe");when(safety.checkInput(any(),isNull(),eq("asr"),eq("unsafe"))).thenReturn(SafetyResult.block("risk","请先关注安全"));var ai=new AiClient(mock(LlmClient.class),breaker,safety);ai.setSpeechClient(speech);assertEquals("请先关注安全",ai.transcribe(new byte[2],"audio/mpeg"));}
 @Test void chatFailureIsRecorded(){var llm=mock(LlmClient.class);var breaker=mock(CircuitBreakerStore.class);when(breaker.allowRequest("llm-chat")).thenReturn(true);doThrow(new IllegalStateException()).when(llm).streamChat(any(),any());var ai=new AiClient(llm,breaker,mock(ContentSafetyFilter.class));assertThrows(IllegalStateException.class,()->ai.streamChat(java.util.List.of(),s->{}));verify(breaker).recordFailure("llm-chat");}
}
