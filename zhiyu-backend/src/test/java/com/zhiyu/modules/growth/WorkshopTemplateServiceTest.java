package com.zhiyu.modules.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopTemplateServiceTest {

    private WorkshopTemplateCatalog catalog;

    @BeforeEach
    void setup() throws Exception {
        catalog = new WorkshopTemplateService(new ObjectMapper()).catalog();
    }

    @Test
    @DisplayName("提供 3 个团体工作坊模板，ID 与时长合法")
    void hasThreeBoundedTemplates() {
        assertEquals(3, catalog.templates().size());
        assertEquals(3, new HashSet<>(catalog.templates().stream().map(t -> t.id()).toList()).size());
        assertTrue(catalog.templates().stream().allMatch(t -> "WORKSHOP".equals(t.kind())));
        assertTrue(catalog.templates().stream().allMatch(t -> t.minutes() >= 30 && t.minutes() <= 240));
        assertTrue(catalog.templates().stream().allMatch(t -> t.capacity() >= 1 && t.capacity() <= 200));
        assertTrue(catalog.templates().stream().allMatch(t -> t.tags().contains("团体")));
    }

    @Test
    @DisplayName("模板包含流程、材料、安全提示与来源说明")
    void templatesCarryOperationalDetails() {
        for (var template : catalog.templates()) {
            assertFalse(template.description().isBlank());
            assertFalse(template.materials().isBlank());
            assertFalse(template.safety().isBlank());
            assertFalse(template.sourceNote().isBlank());
            assertTrue(template.description().contains("不能替代专业心理咨询"));
        }
    }

    @Test
    @DisplayName("模板不出现诊断性结论")
    void templatesAvoidDiagnosisWording() {
        for (var template : catalog.templates()) {
            String text = template.title() + template.summary() + template.description();
            assertFalse(text.contains("抑郁症"));
            assertFalse(text.contains("焦虑症"));
            assertFalse(text.contains("你患有"));
        }
    }
}
