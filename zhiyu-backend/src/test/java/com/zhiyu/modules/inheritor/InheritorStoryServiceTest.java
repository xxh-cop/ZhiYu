package com.zhiyu.modules.inheritor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.inheritor.service.InheritorStoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InheritorStoryServiceTest {

    private InheritorStoryService service;

    @BeforeEach
    void setup() throws Exception {
        var json = new ObjectMapper();
        service = new InheritorStoryService(json, new HeritageCatalogService(json));
    }

    @Test
    @DisplayName("提供 3 条合成教学故事，ID 唯一且引用已知工艺")
    void hasThreeSyntheticStories() {
        var stories = service.list();
        assertEquals(3, stories.size());
        assertEquals(3, new HashSet<>(stories.stream().map(InheritorStoryService.Summary::id).toList()).size());
        assertTrue(stories.stream().allMatch(InheritorStoryService.Summary::synthetic));
        assertTrue(stories.stream().allMatch(story -> story.traditionName() != null && !story.traditionName().isBlank()));
        assertTrue(stories.stream().allMatch(story -> story.readingMinutes() >= 2 && story.readingMinutes() <= 6));
    }

    @Test
    @DisplayName("故事详情包含小节、观察提示、边界与来源")
    void detailCarriesBoundaryAndSources() {
        var detail = service.get("xiabu-first-leaf");
        assertEquals("夏布绣", detail.traditionName());
        assertEquals(3, detail.sections().size());
        assertFalse(detail.quote().isBlank());
        assertFalse(detail.learningPrompt().isBlank());
        assertTrue(detail.boundary().contains("合成"));
        assertTrue(detail.disclaimer().contains("不是真实"));
        assertEquals(1, detail.sources().size());
        assertTrue(detail.sources().get(0).url().startsWith("https://"));
    }

    @Test
    @DisplayName("故事不伪造真实传承人资质或口述史")
    void storiesDoNotClaimRealCredentials() {
        for (var summary : service.list()) {
            var detail = service.get(summary.id());
            String text = detail.title() + detail.subtitle() + detail.sections().toString() + detail.quote();
            assertFalse(text.contains("代表性传承人"));
            assertFalse(text.contains("从艺"));
            assertFalse(text.contains("大师"));
            assertTrue(detail.boundary().contains("非真实"));
        }
    }

    @Test
    @DisplayName("不存在的故事返回 1004")
    void missingStoryReturns1004() {
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.get("not-exists")).getCode());
    }
}
