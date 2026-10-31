package com.zhiyu.modules.companion.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestBreathingToolTest {

    @Test
    void returnsFourSixBreathingWithoutSideEffects() {
        var result = new SuggestBreathingTool().execute(new ToolContext(7L, null), Map.of());
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) result.data();
        assertEquals(4, data.get("inhaleSeconds"));
        assertEquals(6, data.get("exhaleSeconds"));
        assertEquals(4, data.get("cycles"));
        assertTrue(data.get("disclaimer").toString().contains("不能替代专业心理咨询"));
    }
}
