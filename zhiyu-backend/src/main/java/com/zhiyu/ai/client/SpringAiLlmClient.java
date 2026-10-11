package com.zhiyu.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final String companionPrompt;
    private final String safetyPrompt;
    private final String recommendPrompt;
    private final String ragPrompt;

    public SpringAiLlmClient(ChatClient.Builder builder,
                             @Qualifier("companionPrompt") String companionPrompt,
                             @Qualifier("safetyPrompt") String safetyPrompt,
                             @Qualifier("recommendPrompt") String recommendPrompt,
                             @Qualifier("ragPrompt") String ragPrompt) {
        this.chatClient = builder.build();
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
        withSystem.add(ChatMessage.system(companionPrompt));
        withSystem.add(ChatMessage.system(safetyPrompt));
        withSystem.addAll(history);
        var promptBuilder = chatClient.prompt().messages(toSpringMessages(withSystem));
        promptBuilder.stream().content().doOnNext(onToken).blockLast();
    }

    @Override
    public String recommendReason(String mood, List<String> craftNames) {
        // T1.5：Prompt 外置到 prompts/recommend.st，占位符 {mood} / {craftNames} 渲染
        String promptText = recommendPrompt
                .replace("{mood}", mood)
                .replace("{craftNames}", String.join("、", craftNames));
        return chatClient.prompt().user(promptText).call().content();
    }

    @Override
    public String workMessage(String workTitle, String mood) {
        // T1.1a 先硬编码 prompt；T1.1b/T1.3 外置到 prompts/work_message.st
        String promptText = String.format(
                "你是一位非遗艺术疗愈助手。用户刚完成了作品「%s」，" +
                "创作时的心情是「%s」。请为ta写一段简短的寄语（不超过50字），" +
                "肯定ta的专注与表达，语气温暖但不夸张。",
                workTitle, mood);
        return chatClient.prompt().user(promptText).call().content();
    }

    @Override
    public String chat(String prompt) {
        return chatClient.prompt().user(prompt).call().content();
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
