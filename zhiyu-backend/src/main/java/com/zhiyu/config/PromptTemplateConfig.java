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

    @Value("classpath:prompts/context-summary.st")
    private Resource contextSummaryPromptResource;

    @Value("classpath:prompts/moderation-suggest.st")
    private Resource moderationSuggestPromptResource;

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

    /**
     * 对话上下文滚动摘要 Prompt 模板（context-summary.st 内容原文，P1b 2026-11-04 引入）。
     * 由 DialogueContextService 使用，占位符 {previousSummaryBlock} / {startTurn} / {endTurn} / {turnsBlock} 渲染。
     * 模板约束：只压缩事实与情绪线索、不做诊断结论、不虚构、保留轮次范围。
     */
    @Bean("contextSummaryPrompt")
    public String contextSummaryPrompt() {
        return loadPrompt(contextSummaryPromptResource, "prompts/context-summary.st");
    }

    /**
     * 审核辅助建议 Prompt 模板（BL 2026-11-05 引入）。
     * 由 AdminModerationService 使用，占位符 {targetType}/{title}/{content}/{mediaNote}。
     * 模板铁律：只给参考方向不裁决、不编造媒体内容、不确定就说不确定。
     */
    @Bean("moderationSuggestPrompt")
    public String moderationSuggestPrompt() {
        return loadPrompt(moderationSuggestPromptResource, "prompts/moderation-suggest.st");
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
