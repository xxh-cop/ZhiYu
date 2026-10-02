package com.zhiyu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PromptTemplateConfig} 单测（T1.3a）。
 */
@SpringBootTest
@ActiveProfiles("test")
class PromptTemplateConfigTest {

    @Autowired
    @Qualifier("companionPrompt")
    private String companionPrompt;

    @Test
    @DisplayName("companion.st 加载成功")
    void testCompanionPromptLoaded() {
        assertNotNull(companionPrompt);
        assertFalse(companionPrompt.isBlank());
        assertTrue(companionPrompt.length() > 50, "系统提示词应有一定长度");
    }

    @Test
    @DisplayName("渲染后包含系统提示词的关键约束")
    void testCompanionPromptContainsKeyConstraints() {
        assertTrue(companionPrompt.contains("不做任何心理或医学诊断"),
                "系统提示词应包含诊断禁止约束");
        assertTrue(companionPrompt.contains("织愈"),
                "系统提示词应包含织愈角色定位");
    }
}
