package com.zhiyu.modules.emotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.modules.emotion.service.ListeningSummaryService;
import com.zhiyu.security.BusinessAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListeningSummaryServiceTest {

    private JdbcTemplate db;
    private AiClient ai;
    private EmotionService emotionService;
    private ListeningSummaryService service;

    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        AesEncryptor crypto = mock(AesEncryptor.class);
        ai = mock(AiClient.class);
        emotionService = mock(EmotionService.class);
        BusinessAccess access = mock(BusinessAccess.class);
        var prompt = new ByteArrayResource("{{turns}}|{{transcript}}".getBytes(StandardCharsets.UTF_8));
        service = new ListeningSummaryService(db, new ObjectMapper(), crypto, ai, emotionService, access, prompt);
        when(access.consentedUser()).thenReturn(7L);
        when(access.user()).thenReturn(7L);
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(db.queryForList(contains("SELECT status FROM emotion_session"), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", 1)));
        when(db.queryForList(contains("FROM care_reminder"), any(Object[].class)))
                .thenReturn(List.of(Map.of("count", 0)));
    }

    @Test
    void shortConversationReturnsGuidanceWithoutModelCall() {
        when(emotionService.loadRecentHistory(11L, 40)).thenReturn(List.of(
                new ChatMessage("user", "今天有点累")));
        var result = service.generate(11L, true);
        assertFalse((Boolean) result.get("generated"));
        assertTrue(result.get("summary").toString().contains("还比较短"));
        verify(ai, never()).listeningSummary(any(), anyString(), anyString());
    }

    @Test
    void careReminderSessionNeverGeneratesAiSummary() {
        when(db.queryForList(contains("FROM care_reminder"), any(Object[].class)))
                .thenReturn(List.of(Map.of("count", 1)));
        var result = service.generate(11L, true);
        assertTrue((Boolean) result.get("safetyHold"));
        verify(ai, never()).listeningSummary(any(), anyString(), anyString());
    }

    @Test
    void generatedSummaryIsNotSavedUntilSaveIsCalled() {
        when(emotionService.loadRecentHistory(11L, 40)).thenReturn(List.of(
                new ChatMessage("user", "最近作业很多，我有点喘不过气。"),
                new ChatMessage("user", "说出来以后轻一点了。")));
        when(ai.listeningSummary(any(), anyString(), anyString()))
                .thenReturn(new AiClient.ListeningResult("你提到作业压力，也发现自己说出来后轻了一点。", true, false));

        var generated = service.generate(11L, true);
        assertTrue((Boolean) generated.get("generated"));
        assertEquals("你提到作业压力，也发现自己说出来后轻了一点。", generated.get("summary"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveRequiresEndedSession() {
        when(db.queryForList(contains("SELECT status FROM emotion_session"), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", 0)));
        assertThrows(com.zhiyu.common.exception.BusinessException.class,
                () -> service.save(11L, "小结", false, true));
    }
}
