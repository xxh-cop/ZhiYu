package com.zhiyu.modules.companion.tool;

import com.zhiyu.modules.practice.service.AdaptivePracticeService;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendPracticeToolTest {

    private AdaptivePracticeService adaptive;
    private RecommendPracticeTool tool;

    @BeforeEach
    void setUp() {
        adaptive = mock(AdaptivePracticeService.class);
        tool = new RecommendPracticeTool(adaptive);
    }

    @Test
    void recommendsOneOrTwoPracticesByMinutes() {
        when(adaptive.plan(7L, 10)).thenReturn(plan(10, "MANUAL"));

        var result = tool.execute(new ToolContext(7L, null), Map.of("minutes", 10));

        assertTrue(result.success());
        var plan = (AdaptivePlanVO) result.data();
        assertTrue(plan.items().size() >= 1 && plan.items().size() <= 2);
        assertTrue(plan.items().stream().allMatch(item -> item.patternId() != null));
        assertTrue(plan.reason().contains("不作情绪或疗效判断"));
        verify(adaptive).plan(7L, 10);
    }

    @Test
    void missingMinutesUsesAdaptiveDefault() {
        when(adaptive.plan(7L, null)).thenReturn(plan(15, "DEFAULT"));

        var result = tool.execute(new ToolContext(7L, null), Map.of());

        assertTrue(result.success());
        verify(adaptive).plan(7L, null);
    }

    @Test
    void rejectsInvalidMinutes() {
        var result = tool.execute(new ToolContext(7L, null), Map.of("minutes", 0));

        assertFalse(result.success());
        assertEquals("1001", result.code());
        verify(adaptive, never()).plan(7L, 0);
    }

    private AdaptivePlanVO plan(int minutes, String source) {
        return new AdaptivePlanVO(minutes, "入门", 0.5, 1, 1, source, true, false,
                "按最近记录安排入门节奏。只调整练习节奏，不作情绪或疗效判断。",
                List.of(new AdaptivePlanVO.Item("971201", "经纬初见", "竹编（瑞昌竹编）",
                        "weave", "编织", 8, "入门", "观察交替节奏",
                        List.of("#F5F0DE", "#4B503C", "#A9A477", "#CFB584"),
                        "8 分钟 · 入门 · 适合当前时间预算。")));
    }
}
