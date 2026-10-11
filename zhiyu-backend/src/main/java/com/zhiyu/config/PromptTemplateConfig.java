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
 * Prompt 模板配置（T1.3a / T1.3b / T1.5）。
 *
 * <p>从 {@code resources/prompts/*.st} 加载外置 Prompt，避免硬编码在 Java 里（AGENTS.md §4）。
 *
 * <p>本配置加载：
 * <ul>
 *   <li>{@code companion.st} —— 陪伴者人设（系统提示词第一条）</li>
 *   <li>{@code safety.st} —— 安全边界约束（系统提示词第二条，T1.3b 引入）</li>
 *   <li>{@code recommend.st} —— 推荐理由 Prompt 模板（T1.5 引入）</li>
 *   <li>{@code rag.st} —— RAG 问答 Prompt 模板（M3-3 引入）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class PromptTemplateConfig {

    @Value("classpath:prompts/companion.st")
    private Resource companionPromptResource;

    @Value("classpath:prompts/safety.st")
    private Resource safetyPromptResource;

    @Value("classpath:prompts/recommend.st")
    private Resource recommendPromptResource;

    @Value("classpath:prompts/rag.st")
    private Resource ragPromptResource;

    /**
     * 陪伴者系统提示词（companion.st 内容原文）。
     * 由 SpringAiLlmClient 在 streamChat 时作为第一条 system message 注入。
     */
    @Bean("companionPrompt")
    public String companionPrompt() {
        return loadPrompt(companionPromptResource, "prompts/companion.st");
    }

    /**
     * 安全边界系统提示词（safety.st 内容原文，T1.3b 引入）。
     * 由 SpringAiLlmClient 在 streamChat 时作为第二条 system message 追加。
     */
    @Bean("safetyPrompt")
    public String safetyPrompt() {
        return loadPrompt(safetyPromptResource, "prompts/safety.st");
    }

    /**
     * 推荐理由 Prompt 模板（recommend.st 内容原文，T1.5 引入）。
     * 由 SpringAiLlmClient.recommendReason 使用，占位符 {mood} / {craftNames} 渲染后发给 LLM。
     */
    @Bean("recommendPrompt")
    public String recommendPrompt() {
        return loadPrompt(recommendPromptResource, "prompts/recommend.st");
    }

    /**
     * RAG 问答 Prompt 模板（rag.st 内容原文，M3-3 引入）。
     * 由 RagService 使用，占位符 {context} / {question} 渲染后发给 LLM。
     */
    @Bean("ragPrompt")
    public String ragPrompt() {
        return loadPrompt(ragPromptResource, "prompts/rag.st");
    }

    private String loadPrompt(Resource resource, String desc) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String text = reader.lines().collect(Collectors.joining("\n"));
            log.info("{} 加载成功（{} 字符）", desc, text.length());
            return text;
        } catch (Exception e) {
            throw new IllegalStateException("加载 " + desc + " 失败", e);
        }
    }
}
