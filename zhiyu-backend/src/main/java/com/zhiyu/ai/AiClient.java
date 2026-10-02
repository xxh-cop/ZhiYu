package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 统一门面（T1.1b）。
 *
 * <p>所有业务代码只调 {@link AiClient}，不直接碰 {@link LlmClient}。
 * 本类在 LLM 调用外包裹熔断器（{@link CircuitBreakerStore}），
 * 按 AGENTS.md §4 的资源 key 隔离：
 * <ul>
 *   <li>{@code llm-chat}      —— 流式对话（本批次不走熔断，T1.3 接 SSE 时处理）</li>
 *   <li>{@code llm-recommend}  —— 推荐理由</li>
 *   <li>{@code llm-message}    —— 作品寄语</li>
 * </ul>
 *
 * <p>超时降级：LLM 调用失败或熔断中 → 返回缓存图文推荐理由 + 图文引导，
 * 不让用户白屏。
 *
 * <p>T1.1b 尚未加：内容安全过滤、审计日志（T1.3/T1.4 做）。
 */
@Slf4j
@Component
public class AiClient {

    private final LlmClient llmClient;
    private final CircuitBreakerStore breaker;

    public AiClient(LlmClient llmClient, CircuitBreakerStore breaker) {
        this.llmClient = llmClient;
        this.breaker = breaker;
        log.info("AiClient 初始化完成，breaker store={}", breaker.implementationName());
    }

    // ── 流式对话：不走熔断（流式无法简单降级到同步 fallback） ──

    public void streamChat(List<ChatMessage> history, Consumer<String> onToken) {
        llmClient.streamChat(history, onToken);
    }

    // ── 推荐理由：走熔断 ──

    private static final String KEY_RECOMMEND = "llm-recommend";
    private static final String FALLBACK_RECOMMEND = "（暂时无法生成个性化理由，先看看这些手作吧）";

    public String recommendReason(String mood, List<String> craftNames) {
        if (!breaker.allowRequest(KEY_RECOMMEND)) {
            log.warn("熔断中，走降级: key={}", KEY_RECOMMEND);
            return FALLBACK_RECOMMEND;
        }
        try {
            String result = llmClient.recommendReason(mood, craftNames);
            breaker.recordSuccess(KEY_RECOMMEND);
            return result;
        } catch (Exception e) {
            breaker.recordFailure(KEY_RECOMMEND);
            log.warn("LLM 调用失败，已记录熔断计数: key={}, err={}", KEY_RECOMMEND, e.getMessage());
            return FALLBACK_RECOMMEND;
        }
    }

    // ── 作品寄语：走熔断 ──

    private static final String KEY_MESSAGE = "llm-message";
    private static final String FALLBACK_MESSAGE = "（暂时无法生成寄语，你的专注本身就很美好）";

    public String workMessage(String workTitle, String mood) {
        if (!breaker.allowRequest(KEY_MESSAGE)) {
            log.warn("熔断中，走降级: key={}", KEY_MESSAGE);
            return FALLBACK_MESSAGE;
        }
        try {
            String result = llmClient.workMessage(workTitle, mood);
            breaker.recordSuccess(KEY_MESSAGE);
            return result;
        } catch (Exception e) {
            breaker.recordFailure(KEY_MESSAGE);
            log.warn("LLM 调用失败，已记录熔断计数: key={}, err={}", KEY_MESSAGE, e.getMessage());
            return FALLBACK_MESSAGE;
        }
    }

    /** 暴露底层 store 名（可观测） */
    public String breakerStoreName() {
        return breaker.implementationName();
    }
}
