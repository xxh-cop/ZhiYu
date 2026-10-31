package com.zhiyu.modules.practice;

import com.zhiyu.modules.companion.memory.CompanionMemoryService;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.practice.entity.PracticeSession;
import com.zhiyu.modules.practice.mapper.PracticeSessionMapper;
import com.zhiyu.modules.practice.service.AdaptivePracticeService;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptivePracticeServiceTest {

    private PracticeSessionMapper sessions;
    private CompanionMemoryService memory;
    private AdaptivePracticeService service;

    @BeforeEach
    void setUp() {
        sessions = mock(PracticeSessionMapper.class);
        memory = mock(CompanionMemoryService.class);
        HeritageCatalogService catalogs = mock(HeritageCatalogService.class);
        when(catalogs.catalog()).thenReturn(catalog());
        when(sessions.selectList(any())).thenReturn(List.of());
        service = new AdaptivePracticeService(sessions, memory, catalogs);
    }

    @Test
    @DisplayName("无记录时使用默认 15 分钟与入门节奏，并明确不作疗效判断")
    void defaultsForNewUser() {
        when(memory.preferences(7L)).thenReturn(Map.of());

        AdaptivePlanVO plan = service.plan(7L, null);

        assertEquals(15, plan.minutes());
        assertEquals("DEFAULT", plan.source());
        assertEquals("入门", plan.levelBias());
        assertFalse(plan.historyUsed());
        assertTrue(plan.reason().contains("还没有练习记录"));
        assertTrue(plan.reason().contains("不作情绪或疗效判断"));
        assertFalse(plan.generated());
        assertTrue(plan.items().size() >= 1 && plan.items().size() <= 2);
    }

    @Test
    @DisplayName("显式时间优先于记忆偏好")
    void manualMinutesWinOverMemory() {
        when(memory.preferences(7L)).thenReturn(Map.of(CompanionMemoryService.KEY_MINUTES, "30"));

        AdaptivePlanVO manual = service.plan(7L, 10);
        AdaptivePlanVO fromMemory = service.plan(7L, null);

        assertEquals(10, manual.minutes());
        assertEquals("MANUAL", manual.source());
        assertEquals(30, fromMemory.minutes());
        assertEquals("MEMORY", fromMemory.source());
    }

    @Test
    @DisplayName("完成记录足够时提升到进阶节奏")
    void elevatesLevelWhenCompletionIsHigh() {
        when(memory.preferences(7L)).thenReturn(Map.of());
        when(sessions.selectList(any())).thenReturn(List.of(session(5), session(6), session(5)));

        AdaptivePlanVO plan = service.plan(7L, 15);

        assertEquals(3, plan.completedCount());
        assertEquals("进阶", plan.levelBias());
        assertEquals(1.0, plan.completionRate());
        assertTrue(plan.historyUsed());
    }

    @Test
    @DisplayName("偏好纹样族在同等难度与时长下优先")
    void preferredFamilyRanksFirst() {
        when(memory.preferences(7L)).thenReturn(Map.of(CompanionMemoryService.KEY_FAMILY, "weave"));

        AdaptivePlanVO plan = service.plan(7L, 10);

        assertEquals("weave", plan.items().get(0).family());
        assertTrue(plan.reason().contains("编织"));
    }

    private PracticeSession session(int status) {
        PracticeSession session = new PracticeSession();
        session.setUserId(7L);
        session.setStatus(status);
        return session;
    }

    private HeritageCatalog catalog() {
        var traditionA = new HeritageCatalog.Tradition("970101", "民间绣活（夏布绣）", "夏布绣",
                "新余", "传统美术", "Ⅶ-77", 2014, List.of(), "summary",
                List.of(), List.of(), List.of(), "boundary");
        var traditionB = new HeritageCatalog.Tradition("970102", "竹编（瑞昌竹编）", "竹编",
                "瑞昌", "传统美术", "Ⅶ-51", 2008, List.of(), "summary",
                List.of(), List.of(), List.of(), "boundary");
        var stitch = new HeritageCatalog.Pattern("971101", "970101", "线的回环", "stitch",
                "ribbon", 10, List.of("#F7F0E7", "#53494A", "#A78596", "#AAA87D"),
                "ORIGINAL_STUDY", List.of(), "design", "focus", 10, "入门");
        var weave = new HeritageCatalog.Pattern("971201", "970102", "经纬初见", "weave",
                "plain", 6, List.of("#F5F0DE", "#4B503C", "#A9A477", "#CFB584"),
                "ORIGINAL_STUDY", List.of(), "design", "focus", 10, "入门");
        return new HeritageCatalog("v1", "disclaimer", List.of(),
                List.of(traditionA, traditionB), List.of(stitch, weave));
    }
}
