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
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' != '' and '${spring.ai.openai.api-key:}' != 'sk-placeholder'")
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;
    private final com.zhiyu.modules.admin.service.RuntimeSettings settings;
    private final String modelName;
    private final String workPrompt;
    private org.springframework.ai.chat.prompt.ChatOptions options(){return org.springframework.ai.chat.prompt.ChatOptions.builder().temperature(settings.number("model","temperature")).maxTokens((int)settings.number("model","maxTokens")).build();}
    private final String companionPrompt;
    private final String safetyPrompt;
    private final String recommendPrompt;
    private final String ragPrompt;

    public SpringAiLlmClient(ChatClient.Builder builder, com.zhiyu.modules.admin.service.RuntimeSettings settings,
                             @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String modelName,
                             @Qualifier("companionPrompt") String companionPrompt,
                             @Qualifier("safetyPrompt") String safetyPrompt,
                             @Qualifier("recommendPrompt") String recommendPrompt,
                             @Qualifier("ragPrompt") String ragPrompt) {
        this.chatClient = builder.build();
        this.settings=settings;
        this.modelName = modelName;
        try(var in=new org.springframework.core.io.ClassPathResource("prompts/work_message.st").getInputStream()){this.workPrompt=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException("寄语模板读取失败",e);}
        this.companionPrompt = companionPrompt;
        this.safetyPrompt = safetyPrompt;
        this.recommendPrompt = recommendPrompt;
        this.ragPrompt = ragPrompt;
        log.info("SpringAiLlmClient 初始化完成（真 DeepSeek API，companion.st + safety.st + recommend.st + rag.st 已注入）");
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
        // T1.5：Prompt 外置到 prompts/recommend.st，占位符 {mood} / {craftNames} 渲染
        String promptText = recommendPrompt
                .replace("{mood}", mood)
                .replace("{craftNames}", String.join("、", craftNames));
        return chatClient.prompt().options(options()).system(safetyPrompt).user(promptText+"\n"+settings.prompt("recommendation")).call().content();
    }

    @Override
    public String workMessage(String workTitle, String mood) {
        String promptText=workPrompt.replace("{workTitle}",workTitle).replace("{mood}",mood);
        return chatClient.prompt().options(options()).system(safetyPrompt).user(promptText+"\n"+settings.prompt("work")).call().content();
    }

    @Override
    public String reflectionSummary(String prompt) {
        return chatClient.prompt().options(options()).system(safetyPrompt).user(prompt).call().content();
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
        ChatResponse response = chatClient.prompt().options(options()).system(safetyPrompt)
                .user(prompt).call().chatResponse();
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
