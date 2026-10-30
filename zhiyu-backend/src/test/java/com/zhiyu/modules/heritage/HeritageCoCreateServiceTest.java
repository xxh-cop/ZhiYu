package com.zhiyu.modules.heritage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.heritage.service.HeritageCoCreateService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeritageCoCreateServiceTest {

    private AiClient ai;
    private HeritageCoCreateService service;

    @BeforeEach
    void setup() throws Exception {
        var json = new ObjectMapper();
        var catalogs = new HeritageCatalogService(json);
        ai = mock(AiClient.class);
        var prompt = new ByteArrayResource(("{{baseName}}|{{family}}|{{variant}}|{{detail}}|"
                + "{{variants}}|{{palettes}}|{{theme}}").getBytes(StandardCharsets.UTF_8));
        service = new HeritageCoCreateService(ai, catalogs, json, prompt);
        UserContext.set(new LoginUser(1L, 0));
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    private HeritageCoCreateService.Request request(boolean consent) {
        return new HeritageCoCreateService.Request("971201", "留白与节奏", consent);
    }

    private String validJson() {
        return """
                {"variants":[
                  {"variant":"twill","detail":8,"paletteId":"p0","name":"斜纹小变体","rationale":"保留交织结构，调整密度后更适合观察。"},
                  {"variant":"plain","detail":10,"paletteId":"p1","name":"疏密对照","rationale":"用更疏的色块观察画面呼吸。"},
                  {"variant":"diamond","detail":6,"paletteId":"p2","name":"菱格留白","rationale":"减少密度，让留白更清楚。"}
                ]}
                """;
    }

    @Test
    @DisplayName("模型返回合法三变体时只接受白名单结构")
    void validModelVariantsAreAccepted() {
        when(ai.heritageVariants(anyString(), anyString())).thenReturn(validJson());
        var result = service.create(request(true));
        assertTrue(result.generated());
        assertEquals(3, result.variants().size());
        assertTrue(result.variants().stream().allMatch(variant -> "weave".equals(variant.family())));
        assertTrue(result.variants().stream().allMatch(variant -> variant.palette().size() == 4));
    }

    @Test
    @DisplayName("模型不可用或 JSON 非法时回退三个不同内置变体")
    void invalidModelFallsBackToThreeBuiltInVariants() {
        when(ai.heritageVariants(anyString(), anyString())).thenReturn("not-json");
        var result = service.create(request(true));
        assertFalse(result.generated());
        assertEquals(3, result.variants().size());
        assertEquals(3, result.variants().stream()
                .map(variant -> variant.variant() + "|" + variant.detail())
                .distinct().count());
        assertTrue(result.variants().stream().allMatch(variant -> "weave".equals(variant.family())));
    }

    @Test
    @DisplayName("模型返回越界变体时整组回退")
    void outOfWhitelistVariantFallsBack() {
        when(ai.heritageVariants(anyString(), anyString())).thenReturn("""
                {"variants":[
                  {"variant":"sprig","detail":8,"paletteId":"p0","name":"越界","rationale":"越界变体"},
                  {"variant":"plain","detail":10,"paletteId":"p1","name":"合法","rationale":"合法变体"},
                  {"variant":"diamond","detail":6,"paletteId":"p2","name":"合法","rationale":"合法变体"}
                ]}
                """);
        var result = service.create(request(true));
        assertFalse(result.generated());
        assertEquals(3, result.variants().size());
    }

    @Test
    @DisplayName("未授权或未知纹样被拒绝")
    void consentAndPatternAreValidated() {
        var noConsent = assertThrows(BusinessException.class,
                () -> service.create(request(false)));
        assertEquals(1001, noConsent.getCode());
        var missing = assertThrows(BusinessException.class,
                () -> service.create(new HeritageCoCreateService.Request("missing", "", true)));
        assertEquals(1004, missing.getCode());
    }

    @Test
    @DisplayName("过长主题被拒绝")
    void themeLengthIsBounded() {
        var error = assertThrows(BusinessException.class,
                () -> service.create(new HeritageCoCreateService.Request("971201", "a".repeat(121), true)));
        assertEquals(1001, error.getCode());
    }

    @Test
    @DisplayName("模型返回空时仍提供可点击的内置变体")
    void nullModelStillReturnsFallback() {
        when(ai.heritageVariants(anyString(), anyString())).thenReturn(null);
        var result = service.create(request(true));
        assertFalse(result.generated());
        assertEquals(3, result.variants().size());
        assertEquals(3, result.variants().stream()
                .map(variant -> String.join("|", variant.palette()))
                .distinct().count());
        assertTrue(result.variants().stream().allMatch(variant -> variant.palette().size() == 4));
    }

    @Test
    @DisplayName("提示词示例只使用当前 family 的白名单结构")
    void promptExamplesUseCurrentFamily() throws Exception {
        var json = new ObjectMapper();
        var catalogs = new HeritageCatalogService(json);
        var prompt = new ByteArrayResource("{{example}}".getBytes(StandardCharsets.UTF_8));
        var stitchService = new HeritageCoCreateService(ai, catalogs, json, prompt);
        when(ai.heritageVariants(anyString(), anyString())).thenReturn(null);
        stitchService.create(new HeritageCoCreateService.Request("971101", "", true));
        verify(ai).heritageVariants(argThat(text -> text.contains("\"variant\":\"sprig\"")
                && !text.contains("\"variant\":\"twill\"")
                && text.contains("\"paletteId\":\"p0\"")), anyString());
    }
}
