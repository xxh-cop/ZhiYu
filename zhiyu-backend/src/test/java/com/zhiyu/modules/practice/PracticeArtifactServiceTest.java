package com.zhiyu.modules.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.practice.dto.SavePracticeArtifactDTO;
import com.zhiyu.modules.practice.service.PracticeArtifactService;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeArtifactServiceTest {

    private JdbcTemplate db;
    private AesEncryptor crypto;
    private PracticeSessionService sessions;
    private HeritageCatalogService catalog;
    private PracticeArtifactService service;

    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        crypto = mock(AesEncryptor.class);
        sessions = mock(PracticeSessionService.class);
        catalog = mock(HeritageCatalogService.class);
        service = new PracticeArtifactService(db, new ObjectMapper(), crypto, sessions, catalog);
        when(catalog.catalog()).thenReturn(catalogData());
        when(crypto.encrypt(anyString())).thenReturn("cipher");
    }

    @Test
    @DisplayName("完成会话保存文化卡：加密写入并推进到 ARTIFACT_SAVED")
    void savesCompletedSessionArtifact() {
        when(sessions.get(7L, 99L)).thenReturn(session(5));
        when(sessions.transition(7L, 99L, "SAVE_ARTIFACT")).thenReturn(session(6));

        Map<String, Object> result = service.save(7L, 99L, request());

        assertEquals(true, result.get("saved"));
        assertEquals("经纬初见", result.get("patternName"));
        assertEquals("竹编（瑞昌竹编）", result.get("traditionName"));
        assertEquals("今天慢慢来", result.get("notes"));
        assertEquals(4, ((List<?>) result.get("palette")).size());
        assertEquals(1, ((List<?>) result.get("sources")).size());
        verify(crypto).encrypt(anyString());
        verify(db).update(contains("INSERT INTO wellbeing_record"), any(Object[].class));
        verify(sessions).transition(7L, 99L, "SAVE_ARTIFACT");
    }

    @Test
    @DisplayName("已保存会话再次保存只更新成果，不重复推进状态")
    void resavesAlreadySavedSessionWithoutTransition() {
        when(sessions.get(7L, 99L)).thenReturn(session(6));

        var result = service.save(7L, 99L, request());

        assertEquals(true, result.get("saved"));
        verify(sessions, never()).transition(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("未完成或未授权时拒绝保存")
    void rejectsIncompleteOrUnauthorized() {
        when(sessions.get(7L, 99L)).thenReturn(session(3));
        BusinessException state = assertThrows(BusinessException.class,
                () -> service.save(7L, 99L, request()));
        assertEquals(3001, state.getCode().intValue());

        when(sessions.get(8L, 99L)).thenReturn(session(5));
        var noConsent = request();
        noConsent.setConsent(false);
        BusinessException consent = assertThrows(BusinessException.class,
                () -> service.save(8L, 99L, noConsent));
        assertEquals(1001, consent.getCode().intValue());
        verify(crypto, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("色板不是四个合法十六进制颜色时拒绝")
    void rejectsInvalidPalette() {
        when(sessions.get(7L, 99L)).thenReturn(session(5));
        var bad = request();
        bad.setPalette(List.of("#fff", "#000000", "#A9A477", "#CFB584"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(7L, 99L, bad));

        assertEquals(1001, error.getCode().intValue());
        verify(crypto, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("未保存时返回 saved=false")
    void getReturnsNotSavedWhenMissing() {
        when(sessions.get(7L, 99L)).thenReturn(session(5));
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertEquals(false, service.get(7L, 99L).get("saved"));
    }

    @Test
    @DisplayName("读取已保存成果时解密并附上保存时间")
    void getDecryptsSavedArtifact() {
        when(sessions.get(7L, 99L)).thenReturn(session(6));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("payload", "cipher");
        row.put("update_time", "2026-10-30T22:00");
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
        when(crypto.decrypt("cipher")).thenReturn(
                "{\"patternName\":\"经纬初见\",\"palette\":[\"#F5F0DE\",\"#4B503C\",\"#A9A477\",\"#CFB584\"]}");

        var result = service.get(7L, 99L);

        assertEquals(true, result.get("saved"));
        assertEquals("经纬初见", result.get("patternName"));
        assertEquals("2026-10-30T22:00", result.get("savedAt"));
    }

    @Test
    @DisplayName("删除只软删成果，不改会话状态")
    void deleteSoftDeletesArtifact() {
        when(sessions.get(7L, 99L)).thenReturn(session(6));

        service.delete(7L, 99L);

        verify(db).update(contains("UPDATE wellbeing_record SET deleted=1"), any(Object[].class));
        verify(sessions, never()).transition(anyLong(), anyLong(), anyString());
    }

    private SavePracticeArtifactDTO request() {
        SavePracticeArtifactDTO request = new SavePracticeArtifactDTO();
        request.setConsent(true);
        request.setStructureLabel("编织 · 平纹 · 疏密 6");
        request.setNotes("今天慢慢来");
        request.setPalette(List.of("#F5F0DE", "#4B503C", "#A9A477", "#CFB584"));
        return request;
    }

    private PracticeSessionVO session(int status) {
        PracticeSessionVO session = new PracticeSessionVO();
        session.setPracticeSessionId(99L);
        session.setKind("HERITAGE");
        session.setStatus(status);
        session.setStatusLabel(status == 5 ? "已完成" : "已保存");
        session.setHeritagePatternId("971201");
        session.setMinutesBudget(15);
        return session;
    }

    private HeritageCatalog catalogData() {
        var source = new HeritageCatalog.Source("src-1", "瑞昌竹编来源", "瑞昌市文化馆",
                "https://example.com/source", "2026-01-01", "NOT_GRANTED", List.of("fact"));
        var tradition = new HeritageCatalog.Tradition("970102", "竹编（瑞昌竹编）", "竹编",
                "瑞昌", "传统技艺", "VIII-1", 2006, List.of("src-1"), "summary",
                List.of("竹"), List.of("挑"), List.of("注意"), "boundary");
        var pattern = new HeritageCatalog.Pattern("971201", "970102", "经纬初见", "weave",
                "plain", 6, List.of("#F5F0DE", "#4B503C", "#A9A477", "#CFB584"),
                "ORIGINAL_STUDY", List.of("src-1"), "设计说明", "学习点", 8, "入门");
        return new HeritageCatalog("v1", "disclaimer", List.of(source), List.of(tradition), List.of(pattern));
    }
}
