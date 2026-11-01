package com.zhiyu.modules.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialKitServiceTest {

    private MaterialKitCatalog catalog;

    @BeforeEach
    void setup() throws Exception {
        var json = new ObjectMapper();
        var heritage = new HeritageCatalogService(json);
        catalog = new MaterialKitService(json, heritage, new WorkshopTemplateService(json)).catalog();
    }

    @Test
    @DisplayName("3 份材料包与 3 个工作坊模板一一对应")
    void hasThreeKitsBoundToWorkshops() {
        assertEquals(3, catalog.kits().size());
        assertEquals(3, new HashSet<>(catalog.kits().stream().map(k -> k.id()).toList()).size());
        assertTrue(catalog.kits().stream().allMatch(k ->
                new HashSet<>(java.util.List.of("bamboo-weave-evening", "xiabu-leaf-stitch", "paper-window-breath"))
                        .contains(k.workshopTemplateId())));
        assertTrue(catalog.kits().stream().allMatch(k -> k.capacity() >= 1 && k.capacity() <= 200));
    }

    @Test
    @DisplayName("材料包包含材料、工具、准备步骤、安全与无材料替代")
    void kitsCarryPreparationAndSafety() {
        for (var kit : catalog.kits()) {
            assertTrue(kit.items().size() >= 4);
            assertFalse(kit.tools().isEmpty());
            assertTrue(kit.preparation().size() >= 2);
            assertFalse(kit.safety().isEmpty());
            assertFalse(kit.digitalAlternative().isBlank());
            assertFalse(kit.sourceNote().isBlank());
            assertTrue(kit.items().stream().allMatch(item ->
                    !item.name().isBlank() && !item.quantity().isBlank() && !item.note().isBlank()));
        }
    }

    @Test
    @DisplayName("材料包不含采购链接或价格")
    void kitsDoNotContainPricesOrLinks() {
        for (var kit : catalog.kits()) {
            String text = kit.name() + kit.audience() + kit.sourceNote() + kit.digitalAlternative();
            assertFalse(text.contains("http://"));
            assertFalse(text.contains("https://"));
            assertFalse(text.contains("¥"));
            assertFalse(text.contains("元/"));
        }
        assertTrue(catalog.disclaimer().contains("不含采购链接与价格"));
    }
}
