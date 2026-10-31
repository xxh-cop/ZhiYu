package com.zhiyu.modules.companion.memory;

import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionMemoryServiceTest {

    private JdbcTemplate db;
    private AesEncryptor crypto;
    private CompanionMemoryService service;

    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        crypto = mock(AesEncryptor.class);
        BusinessAccess access = mock(BusinessAccess.class);
        service = new CompanionMemoryService(db, crypto, access);
        when(access.consentedUser()).thenReturn(7L);
        when(crypto.encrypt(anyString())).thenAnswer(inv -> "c:" + inv.getArgument(0));
        when(crypto.decrypt(anyString())).thenAnswer(inv ->
                ((String) inv.getArgument(0)).substring(2));
    }

    @Test
    @DisplayName("默认关闭：没有控制行时 enabled=false，但仍可查看已有偏好")
    void getDisabledStillShowsStoredPreferences() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("preferred_minutes", "15")));

        Map<String, Object> result = service.get();

        assertEquals(false, result.get("enabled"));
        assertEquals(180, result.get("ttlDays"));
        assertEquals(1, ((List<?>) result.get("items")).size());
    }

    @Test
    @DisplayName("开启记忆必须确认授权")
    void enableRequiresConsent() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.enable(true, false));
        assertEquals(1001, error.getCode().intValue());
        verify(crypto, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("开启记忆写入控制行")
    void enableWritesControlRow() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("memory_enabled", "true")));

        Map<String, Object> result = service.enable(true, true);

        assertEquals(true, result.get("enabled"));
        verify(crypto).encrypt("true");
        verify(db).update(contains("INSERT INTO user_ai_memory"), any(Object[].class));
    }

    @Test
    @DisplayName("未开启记忆时拒绝保存偏好")
    void saveRejectsWhenMemoryDisabled() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("memory_enabled", "false")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(true, Map.of("preferred_minutes", "15")));

        assertEquals(3001, error.getCode().intValue());
        verify(crypto, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("保存三个白名单偏好：加密写入并返回可读标签")
    void savesWhitelistedPreferences() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("memory_enabled", "true")));

        service.save(true, Map.of(
                "preferred_minutes", "15",
                "preferred_material", "竹",
                "preferred_pattern_family", "编织"));

        verify(crypto).encrypt("15");
        verify(crypto).encrypt("bamboo");
        verify(crypto).encrypt("weave");
        verify(db, times(3)).update(contains("INSERT INTO user_ai_memory"), any(Object[].class));
    }

    @Test
    @DisplayName("拒绝未知键或非法值")
    void rejectsUnknownKeysAndInvalidValues() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("memory_enabled", "true")));

        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.save(true, Map.of("raw_conversation", "秘密"))).getCode().intValue());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.save(true, Map.of("preferred_minutes", "0"))).getCode().intValue());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.save(true, Map.of("preferred_material", "塑料"))).getCode().intValue());
    }

    @Test
    @DisplayName("关闭记忆时不向编排器返回任何偏好")
    void preferencesAreEmptyWhenDisabled() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("memory_enabled", "false"), row("preferred_minutes", "15")));

        assertTrue(service.preferences(7L).isEmpty());
        assertEquals("无", service.promptSummary(7L));
    }

    @Test
    @DisplayName("开启记忆时编排器获得可读偏好摘要")
    void promptSummaryUsesLabels() {
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                row("memory_enabled", "true"),
                row("preferred_minutes", "15"),
                row("preferred_material", "bamboo")));

        String summary = service.promptSummary(7L);

        assertTrue(summary.contains("常用练习时长 15 分钟"));
        assertTrue(summary.contains("偏好材料 竹"));
        assertFalse(summary.contains("raw"));
    }

    @Test
    @DisplayName("删除全部记忆：软删并返回关闭状态")
    void deleteAllSoftDeletesAndDisables() {
        Map<String, Object> result = service.deleteAll();

        assertEquals(false, result.get("enabled"));
        verify(db).update(contains("UPDATE user_ai_memory SET deleted=1"), any(Object[].class));
    }

    private Map<String, Object> row(String key, String value) {
        return Map.of("memory_key", key, "value_cipher", "c:" + value);
    }
}
