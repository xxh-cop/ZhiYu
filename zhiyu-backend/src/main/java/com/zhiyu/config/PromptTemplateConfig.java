package com.zhiyu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Prompt 模板配置（T1.3a）。
 *
 * <p>从 {@code resources/prompts/*.st} 加载外置 Prompt，避免硬编码在 Java 里（AGENTS.md §4）。
 * 本批次只加载 {@code companion.st}（系统提示词），其他 Prompt（recommend / work_message）
 * 后续批次外置。
 */
@Slf4j
@Configuration
public class PromptTemplateConfig {

    @Value("classpath:prompts/companion.st")
    private Resource companionPromptResource;

    /**
     * 系统提示词（companion.st 内容原文）。
     * 由 SpringAiLlmClient 在 streamChat 时 prepend 到 history 最前面。
     */
    @Bean("companionPrompt")
    public String companionPrompt() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(companionPromptResource.getInputStream(), StandardCharsets.UTF_8))) {
            String text = reader.lines().collect(Collectors.joining("\n"));
            log.info("companion.st 加载成功（{} 字符）", text.length());
            return text;
        } catch (Exception e) {
            throw new IllegalStateException("加载 prompts/companion.st 失败", e);
        }
    }
}
