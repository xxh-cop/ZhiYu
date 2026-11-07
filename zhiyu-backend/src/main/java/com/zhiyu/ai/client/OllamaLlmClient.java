package com.zhiyu.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 本地 Ollama 兜底客户端（2026-11-08 装配层批；演示兜底，非评测基线）。
 *
 * <p>Ollama 兼容 OpenAI 协议，与云端共用同一 Spring AI 栈，逻辑全部继承
 * {@link SpringAiLlmClient}（prompt 外置、JSON 受控重试、usage 透传不变）；
 * 差别只在端点与模型：独立装配第二个 {@link OpenAiChatModel} 指向本地
 * {@code zhiyu.ai.providers.local.*}（默认 localhost:11434，全部环境变量占位）。
 *
 * <p>provider 名固定 {@code ollama}——与影子判定统计的演示归属排除清单
 * （{@code shadow.demo-providers} 默认含 ollama）同源，保证本地产出可被机械区分。
 *
 * <p>生效条件：{@code zhiyu.ai.providers.local.enabled=true}。
 * 生产口径永远是云为主；本类只是云不可用时的兜底，不顶替任何 cloud-only 能力。
 */
@Slf4j
@Component
@ConditionalOnExpression("'${zhiyu.ai.providers.local.enabled:false}' == 'true'")
public class OllamaLlmClient extends SpringAiLlmClient {

    public OllamaLlmClient(com.zhiyu.modules.admin.service.RuntimeSettings settings,
                           @Value("${zhiyu.ai.providers.local.base-url:http://localhost:11434}") String baseUrl,
                           @Value("${zhiyu.ai.providers.local.api-key:ollama-local}") String apiKey,
                           @Value("${zhiyu.ai.providers.local.model:deepseek-r1:8b}") String model,
                           @Qualifier("companionPrompt") String companionPrompt,
                           @Qualifier("safetyPrompt") String safetyPrompt,
                           @Qualifier("recommendPrompt") String recommendPrompt,
                           @Qualifier("ragPrompt") String ragPrompt) {
        super(buildLocalBuilder(baseUrl, apiKey, model), settings, model, "ollama",
                companionPrompt, safetyPrompt, recommendPrompt, ragPrompt);
        log.info("OllamaLlmClient 初始化完成（本地兜底 provider=ollama, base-url={}, model={}）", baseUrl, model);
    }

    /** 独立装配第二个 OpenAI 兼容 ChatModel（与云端自动装配互不干扰）。 */
    private static ChatClient.Builder buildLocalBuilder(String baseUrl, String apiKey, String model) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
        return ChatClient.builder(chatModel);
    }
}
