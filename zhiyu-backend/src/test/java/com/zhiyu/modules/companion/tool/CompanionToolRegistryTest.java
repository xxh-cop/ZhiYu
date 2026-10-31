package com.zhiyu.modules.companion.tool;

import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanionToolRegistryTest {

    @Test
    void registersAndExecutesWhitelistedTool() {
        CompanionTool tool = mock(CompanionTool.class);
        when(tool.name()).thenReturn("demo");
        when(tool.execute(any(), any())).thenReturn(ToolResult.success(Map.of("ok", true)));
        CompanionToolRegistry registry = new CompanionToolRegistry(List.of(tool));

        assertTrue(registry.names().contains("demo"));
        var result = registry.execute(new ToolContext(7L, null), "demo", Map.of());
        assertTrue(result.success());
    }

    @Test
    void unknownToolAndMissingUserAreRejected() {
        CompanionToolRegistry registry = new CompanionToolRegistry(List.of());
        assertEquals("1001", registry.execute(new ToolContext(7L, null), "missing", Map.of()).code());
        assertEquals("1002", registry.execute(new ToolContext(null, null), "missing", Map.of()).code());
    }

    @Test
    void duplicateToolNamesFailFast() {
        CompanionTool first = mock(CompanionTool.class);
        CompanionTool second = mock(CompanionTool.class);
        when(first.name()).thenReturn("duplicate");
        when(second.name()).thenReturn("duplicate");
        assertThrows(IllegalStateException.class,
                () -> new CompanionToolRegistry(List.of(first, second)));
    }

    @Test
    void businessExceptionIsConvertedToFailure() {
        CompanionTool tool = mock(CompanionTool.class);
        when(tool.name()).thenReturn("bad");
        when(tool.execute(any(), any())).thenThrow(new BusinessException(3001, "bad state"));
        CompanionToolRegistry registry = new CompanionToolRegistry(List.of(tool));
        var result = registry.execute(new ToolContext(7L, null), "bad", Map.of());
        assertEquals("3001", result.code());
        assertEquals("bad state", result.message());
    }
}
