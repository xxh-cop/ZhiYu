package com.zhiyu.modules.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.practice.service.ShortPracticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortPracticeServiceTest {

    private ShortPracticeService service;

    @BeforeEach
    void setup() throws Exception {
        var json = new ObjectMapper();
        service = new ShortPracticeService(json, new HeritageCatalogService(json));
    }

    @Test
    @DisplayName("目录包含 8 条短练习，ID 唯一且都在 1–10 分钟")
    void catalogIsCompleteAndBounded() {
        var practices = service.catalog().practices();
        assertEquals(8, practices.size());
        assertEquals(8, new HashSet<>(practices.stream().map(p -> p.id()).toList()).size());
        assertTrue(practices.stream().allMatch(p -> p.minutes() >= 1 && p.minutes() <= 10));
        assertTrue(practices.stream().allMatch(p -> p.steps().size() >= 3 && p.steps().size() <= 5));
        assertFalse(service.catalog().disclaimer().isBlank());
    }

    @Test
    @DisplayName("5 分钟请求优先返回 5 分钟练习")
    void recommendsByClosestMinutes() {
        var result = service.recommend(5, null, null);
        assertEquals(8, result.size());
        assertEquals(5, result.get(0).minutes());
    }

    @Test
    @DisplayName("心情标签参与排序")
    void recommendsByMood() {
        var stressed = service.recommend(5, "stressed", null);
        assertTrue(stressed.get(0).moodTags().contains("stressed"));
        assertEquals("weave-300", stressed.get(0).id());

        var tired = service.recommend(5, "tired", null);
        assertEquals("leaf-300", tired.get(0).id());
    }

    @Test
    @DisplayName("kind 过滤只返回对应类型")
    void filtersByKind() {
        var result = service.recommend(null, null, "drawing");
        assertEquals(1, result.size());
        assertEquals("color-480", result.get(0).id());
        assertEquals("DRAWING", result.get(0).kind());
    }

    @Test
    @DisplayName("不支持的 kind 返回 1001")
    void rejectsUnknownKind() {
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.recommend(null, null, "SLEEP")).getCode());
    }

    @Test
    @DisplayName("按 ID 读取；不存在返回 1004")
    void findsById() {
        assertEquals("leaf-300", service.find("leaf-300").id());
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.find("not-exists")).getCode());
    }
}
