package com.zhiyu.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI {@link ChatClient} 的 {@link LlmClient} 实现（T1.1a）。
 *
 * <p>通过 Spring AI 的 OpenAI starter 调 DeepSeek API（DeepSeek 兼容 OpenAI 规范）。
 *
 * <p>生效条件：{@code spring.ai.openai.api-key} 存在且不为占位值 {@code sk-placeholder}。
 * 当 API Key 为占位值时，{@link MockLlmClient} 接管。
 *
 * <p>本批次不写熔断 / 安全过滤 / 审计日志（T1.1b 做）。
 */
@Slf4j
@Component
@org.springframework.context.annotation.Primary
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' != '' and '${spring.ai.openai.api-key:}' != 'sk-placeholder'")
public class SpringAiLlmClient implements LlmClient {

    /** 仅用于 moderation JSON 的语法级可解析性校验（不做业务结构判断，不修补文本）。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ChatClient chatClient;
    private final com.zhiyu.modules.admin.service.RuntimeSettings settings;
    private final String modelName;
    private final String providerName;
    private final String workPrompt;
    /** moderation JSON 重试告知片段（外置 moderation-json-retry.st，AGENTS.md §4 不硬编码 prompt）。 */
    private final String jsonRetryPrompt;
    private org.springframework.ai.chat.prompt.ChatOptions options(){return org.springframework.ai.chat.prompt.ChatOptions.builder().temperature(settings.number("model","temperature")).maxTokens((int)settings.number("model","maxTokens")).build();}
    private final String companionPrompt;
    private final String safetyPrompt;
    private final String recommendPrompt;
    private final String ragPrompt;

    public SpringAiLlmClient(ChatClient.Builder builder, com.zhiyu.modules.admin.service.RuntimeSettings settings,
                             @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String modelName,
                             // provider 归属（2026-11-07 影子模式）：默认 deepseek=云端生产；
                             // 演示走本地模型时由另一条线用环境变量覆盖（application.yml 不在本任务改动范围）
                             @Value("${zhiyu.ai.provider:deepseek}") String providerName,
                             @Qualifier("companionPrompt") String companionPrompt,
                             @Qualifier("safetyPrompt") String safetyPrompt,
                             @Qualifier("recommendPrompt") String recommendPrompt,
                             @Qualifier("ragPrompt") String ragPrompt) {
        this.chatClient = builder.build();
        this.settings=settings;
        this.modelName = modelName;
        this.providerName = providerName;
        try(var in=new org.springframework.core.io.ClassPathResource("prompts/work_message.st").getInputStream()){this.workPrompt=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException("寄语模板读取失败",e);}
        try(var in=new org.springframework.core.io.ClassPathResource("prompts/moderation-json-retry.st").getInputStream()){this.jsonRetryPrompt=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException("moderation JSON 重试模板读取失败",e);}
        this.companionPrompt = companionPrompt;
        this.safetyPrompt = safetyPrompt;
        this.recommendPrompt = recommendPrompt;
        this.ragPrompt = ragPrompt;
        log.info("SpringAiLlmClient 初始化完成（provider={}，model={}，companion.st + safety.st + recommend.st + rag.st 已注入）", providerName, modelName);
    }

    @Override
    public void streamChat(List<ChatMessage> history, Consumer<String> onToken) {
        // prepend 系统提示词到 history 最前面：companion.st（人设）+ safety.st（安全边界）
        List<ChatMessage> withSystem = new ArrayList<>(history.size() + 2);
        withSystem.add(ChatMessage.system(companionPrompt+"\n"+settings.prompt("companion")));
        withSystem.add(ChatMessage.system(safetyPrompt));
        withSystem.addAll(history);
        var promptBuilder = chatClient.prompt().options(options()).messages(toSpringMessages(withSystem));
        promptBuilder.stream().content().doOnNext(onToken).blockLast();
    }

    @Override
    public String recommendReason(String mood, List<String> craftNames) {
        return recommendReasonWithUsage(mood, craftNames).text();
    }

    @Override
    public LlmChatResult recommendReasonWithUsage(String mood, List<String> craftNames) {
        // T1.5：Prompt 外置到 prompts/recommend.st，占位符 {mood} / {craftNames} 渲染
        String promptText = recommendPrompt
                .replace("{mood}", mood)
                .replace("{craftNames}", String.join("、", craftNames));
        return toResult(chatClient.prompt().options(options()).system(safetyPrompt)
                .user(promptText + "\n" + settings.prompt("recommendation")).call().chatResponse());
    }

    @Override
    public String workMessage(String workTitle, String mood) {
        return workMessageWithUsage(workTitle, mood).text();
    }

    @Override
    public LlmChatResult workMessageWithUsage(String workTitle, String mood) {
        String promptText = workPrompt.replace("{workTitle}", workTitle).replace("{mood}", mood);
        return toResult(chatClient.prompt().options(options()).system(safetyPrompt)
                .user(promptText + "\n" + settings.prompt("work")).call().chatResponse());
    }

    @Override
    public String reflectionSummary(String prompt) {
        return reflectionSummaryWithUsage(prompt).text();
    }

    @Override
    public LlmChatResult reflectionSummaryWithUsage(String prompt) {
        return toResult(chatClient.prompt().options(options()).system(safetyPrompt)
                .user(prompt).call().chatResponse());
    }

    @Override
    public String listeningSummary(String prompt) {
        return chatClient.prompt().options(options()).system(safetyPrompt).user(prompt).call().content();
    }

    @Override
    public String chat(String prompt) {
        return chatClient.prompt().options(options()).system(safetyPrompt).user(prompt).call().content();
    }

    @Override
    public LlmChatResult chatWithUsage(String prompt) {
        return toResult(chatClient.prompt().options(options()).system(safetyPrompt)
                .user(prompt).call().chatResponse());
    }

    /**
     * 带模型覆盖的一次性对话（B2 任务分档）。
     *
     * <p>与 {@link #chatWithUsage(String)} 的唯一区别是 {@link ChatOptions#getModel()}
     * 由参数指定；温度与 maxTokens 沿用运行配置（DeepSeek 同一 endpoint/key，
     * 仅切换模型名）。deepseek-reasoner 对 temperature 不敏感的差异由调用方评测记录。
     */
    @Override
    public LlmChatResult chatWithUsage(String prompt, String model) {
        var modelOptions = org.springframework.ai.chat.prompt.ChatOptions.builder()
                .temperature(settings.number("model", "temperature"))
                .maxTokens((int) settings.number("model", "maxTokens"))
                .model(model)
                .build();
        return toResult(chatClient.prompt().options(modelOptions).system(safetyPrompt)
                .user(prompt).call().chatResponse());
    }

    /**
     * JSON 结构化输出（2026-11-05 健壮性批）：{@code response_format=json_object}。
     * 仅 moderation 链路调用；model 为 null 时用默认模型。
     *
     * <p>受控重试（2026-11-07，p37 转义故障）：{@code json_object} 只是指令级约束，
     * 挡不住字符串值内未转义的英文双引号。首次输出未通过 JSON 语法解析时，
     * 以「同 prompt + 重试告知片段（moderation-json-retry.st）」原样重试 1 次；
     * 仍不合法则把原文原样返回，由业务层按既有口径降级（PARSE_FAIL → 人工兜底）——
     * 本层只做语法校验，绝不用正则/启发式修补或猜结构补全模型输出。
     * 重试发生时两次调用的 tokens 求和后随 {@link LlmChatResult} 透传（任一侧缺失记 null，
     * 不估算），保证记账不漏；最终输出仍由门面统一走安全过滤与审计。
     */
    @Override
    public LlmChatResult chatWithUsageJson(String prompt, String model) {
        var builder = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .temperature(settings.number("model", "temperature"))
                .maxTokens((int) settings.number("model", "maxTokens"))
                .responseFormat(org.springframework.ai.openai.api.ResponseFormat.builder()
                        .type(org.springframework.ai.openai.api.ResponseFormat.Type.JSON_OBJECT)
                        .build());
        if (model != null) {
            builder.model(model);
        }
        var options = builder.build();
        LlmChatResult first = toResult(chatClient.prompt().options(options).system(safetyPrompt)
                .user(prompt).call().chatResponse());
        if (isParseableJson(first.text())) {
            return first;
        }
        log.warn("moderation JSON 输出未通过语法解析，按受控策略同 prompt 重试 1 次: model={}", model);
        LlmChatResult second = toResult(chatClient.prompt().options(options).system(safetyPrompt)
                .user(prompt + "\n\n" + jsonRetryPrompt).call().chatResponse());
        if (!isParseableJson(second.text())) {
            log.warn("moderation JSON 重试后仍非合法 JSON，原样交回业务层按 PARSE_FAIL 降级: model={}", model);
        }
        return new LlmChatResult(second.text(),
                sumTokens(first.inputTokens(), second.inputTokens()),
                sumTokens(first.outputTokens(), second.outputTokens()));
    }

    /** JSON 语法校验（围栏容忍口径与业务层解析一致）；只校验，不修补文本。 */
    private static boolean isParseableJson(String text) {
        if (text == null) {
            return false;
        }
        try {
            String stripped = text.trim()
                    .replaceAll("(?s)^```(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*```$", "")
                    .trim();
            JSON.readTree(stripped);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** tokens 求和：任一侧缺失则整体记 null（拿不到不估算，与 AiCallMetrics 口径一致）。 */
    private static Integer sumTokens(Integer a, Integer b) {
        return a == null || b == null ? null : a + b;
    }

    /** 统一抽取文本与 usage；usage 缺失留 null，不估算。 */
    private LlmChatResult toResult(ChatResponse response) {
        String text = response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                ? "" : response.getResult().getOutput().getText();
        var usage = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getUsage();
        return new LlmChatResult(text,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens());
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    /**
     * 把 {@link ChatMessage} 列表转换成 Spring AI 的 {@link Message} 列表。
     */
    private List<Message> toSpringMessages(List<ChatMessage> history) {
        return history.stream().map(this::toSpringMessage).collect(Collectors.toList());
    }

    private Message toSpringMessage(ChatMessage msg) {
        return switch (msg.role()) {
            case "system" -> new SystemMessage(msg.content());
            case "assistant" -> new AssistantMessage(msg.content());
            default -> new UserMessage(msg.content());
        };
    }
}
