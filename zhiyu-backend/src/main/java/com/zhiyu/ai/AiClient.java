package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.security.UserContext;
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
 *   <li>{@code llm-chat}      —— 流式对话（走熔断；流式无法简单降级到同步 fallback）</li>
 *   <li>{@code llm-recommend}  —— 推荐理由</li>
 *   <li>{@code llm-message}    —— 作品寄语</li>
 * </ul>
 *
 * <p>超时降级：LLM 调用失败或熔断中 → 返回缓存图文推荐理由 + 图文引导，
 * 不让用户白屏。
 *
 * <p>内容安全过滤（AGENTS.md §4）：
 * <ul>
 *   <li>{@code recommendReason} / {@code workMessage} —— 输出过滤在本类内部完成，
 *       调用方无需再显式调 {@code ContentSafetyFilter.checkOutput}</li>
 *   <li>{@code streamChat} —— 输入过滤前置到本类；完整回复生成后再做输出过滤
 *       （流式 token 无法逐字改写）。调用方只消费 {@link StreamChatResult}，
 *       不得再自行调 {@link ContentSafetyFilter}，否则会产生重复审计/重复改写</li>
 * </ul>
 *
 * <p>审计粒度：每次 AI 调用恰好两条（输入一条、输出一条），由
 * {@link ContentSafetyFilter#checkInput} / {@link ContentSafetyFilter#checkOutput}
 * 内部写入，且都带 userId。
 */
@Slf4j
@Component
public class AiClient {

    private final LlmClient llmClient;
    private final CircuitBreakerStore breaker;
    private final ContentSafetyFilter contentSafetyFilter;
    private com.zhiyu.ai.client.SpeechClient speechClient;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSpeechClient(com.zhiyu.ai.client.SpeechClient speechClient) { this.speechClient=speechClient; }

    public boolean speechAvailable() { return speechClient!=null && speechClient.available(); }

    public String transcribe(byte[] audio,String mimeType) {
        requireSpeech("asr");
        String text;
        try { text=speechClient.transcribe(audio,mimeType);breaker.recordSuccess("asr"); }
        catch(RuntimeException e){breaker.recordFailure("asr");throw e;}
        var input=contentSafetyFilter.checkInput(UserContext.getUserId(),null,"asr",text);
        if(input.getAction()==SafetyAction.BLOCK)return input.getCareMessage();
        return applyOutputSafety("asr",text);
    }

    public byte[] synthesize(String text) {
        requireSpeech("tts");
        var input=contentSafetyFilter.checkInput(UserContext.getUserId(),null,"tts",text);
        String safe=input.getAction()==SafetyAction.BLOCK?input.getCareMessage():text;
        safe=applyOutputSafety("tts",safe);
        try{byte[] result=speechClient.synthesize(safe);breaker.recordSuccess("tts");return result;}
        catch(RuntimeException e){breaker.recordFailure("tts");throw e;}
    }

    private void requireSpeech(String resource) {
        if(!speechAvailable())throw new com.zhiyu.common.exception.BusinessException(4001,"语音服务尚未配置，请使用文字输入或文字引导");
        if(!breaker.allowRequest(resource))throw new com.zhiyu.common.exception.BusinessException(4001,"语音服务暂时繁忙，请稍后重试");
    }

    public AiClient(LlmClient llmClient, CircuitBreakerStore breaker,
                    ContentSafetyFilter contentSafetyFilter) {
        this.llmClient = llmClient;
        this.breaker = breaker;
        this.contentSafetyFilter = contentSafetyFilter;
        log.info("AiClient 初始化完成，breaker store={}", breaker.implementationName());
    }

    // ── 流式对话：不走降级 fallback，但走熔断 + 输入前置过滤 + 输出终检 ──

    /** 输出侧越界时，兜底交给用户的陪伴话术（含 AGENTS.md §1 免责声明）。 */
    static final String SAFE_CHAT_REPLY =
            "我在这里陪着你。我们可以先停一停，关注当下的感受。本系统不能替代专业心理咨询。";

    /**
     * 流式对话的返回结果。
     *
     * @param input  输入侧过滤结果（BLOCK 时不调 LLM）
     * @param output 完整回复的输出侧过滤结果；输入被 BLOCK 时为 {@code null}
     * @param reply  可安全展示给用户的最终文本：正常回复 / 输出改写文本 / 关怀话术
     */
    public record StreamChatResult(SafetyResult input, SafetyResult output, String reply) {
        /** 输入命中高危，未调用 LLM。 */
        public boolean blocked() {
            return input != null && input.getAction() == SafetyAction.BLOCK;
        }

        /** SSE done 事件的危机标志：输入或输出任一侧命中高危。 */
        public boolean crisis() {
            return (input != null && input.isHighRisk())
                    || (output != null && output.isHighRisk());
        }
    }

    /**
     * 流式情绪对话（输入前置过滤 + 完整回复终检）。
     *
     * <p>时序：
     * <ol>
     *   <li>{@link ContentSafetyFilter#checkInput}（写一条审计；BLOCK 时另写 care_reminder）</li>
     *   <li>BLOCK → 直接返回关怀话术，<b>不调 LLM、不回调 onToken</b></li>
     *   <li>PASS → 熔断检查 → 调 LLM，token 既累积到内部缓冲区也回调给调用方</li>
     *   <li>回复结束后 {@link ContentSafetyFilter#checkOutput}（写一条审计）；
     *       REWRITE 时用安全文本替换整个回复（流式场景无法逐 token 改写，
     *       调用方不应把原始 token 直接推给客户端）</li>
     * </ol>
     *
     * @param sessionId 会话 ID（可空；用于 care_reminder 关联与脱敏摘要）
     * @param userInput 本轮用户输入明文（只检查本轮，不用历史消息代替）
     * @param history   对话历史（含本轮用户消息）
     * @param onToken   流式 token 回调（仅供调用方累积或转发安全文本前的原始 token）
     * @return 过滤结果与可展示文本
     */
    public StreamChatResult streamChat(Long sessionId, String userInput,
                                       List<ChatMessage> history, Consumer<String> onToken) {
        Long userId = UserContext.getUserId();
        SafetyResult input = contentSafetyFilter.checkInput(userId, sessionId, "chat", userInput);
        if (input.getAction() == SafetyAction.BLOCK) {
            log.warn("流式对话输入侧拦截: userId={}, sessionId={}, 不调 LLM", userId, sessionId);
            return new StreamChatResult(input, null, input.getCareMessage());
        }

        if (!breaker.allowRequest("llm-chat")) {
            throw new com.zhiyu.common.exception.BusinessException(4001, "对话服务暂时不可用");
        }
        StringBuilder fullReply = new StringBuilder();
        try {
            llmClient.streamChat(history, token -> {
                fullReply.append(token);
                onToken.accept(token);
            });
            breaker.recordSuccess("llm-chat");
        } catch (RuntimeException e) {
            breaker.recordFailure("llm-chat");
            throw e;
        }

        SafetyResult output = contentSafetyFilter.checkOutput(userId, "chat", fullReply.toString());
        String reply = fullReply.toString();
        if (output.getAction() != SafetyAction.PASS) {
            String rewritten = output.getRewrittenText();
            reply = (rewritten == null || rewritten.isBlank()) ? SAFE_CHAT_REPLY : rewritten;
        }
        return new StreamChatResult(input, output, reply);
    }

    // ── 推荐理由：走熔断 ──

    private static final String KEY_RECOMMEND = "llm-recommend";
    private static final String FALLBACK_RECOMMEND = "（暂时无法生成个性化理由，先看看这些手作吧）";

    /**
     * 生成推荐理由（含输出安全过滤）。
     *
     * <p>LLM 返回后自动调 {@link ContentSafetyFilter#checkOutput} 做输出过滤，
     * 命中 {@link SafetyAction#REWRITE} 时用安全文本替换。
     * 审计日志由 checkOutput 内部写入，调用方无需再显式过滤。
     *
     * @param mood       用户当前情绪
     * @param craftNames 候选手作名称列表
     * @return LLM 生成的推荐理由文本；熔断或失败时返回降级文案
     */
    public String recommendReason(String mood, List<String> craftNames) {
        if (!breaker.allowRequest(KEY_RECOMMEND)) {
            log.warn("熔断中，走降级: key={}", KEY_RECOMMEND);
            return FALLBACK_RECOMMEND;
        }
        String result;
        try {
            result = llmClient.recommendReason(mood, craftNames);
            breaker.recordSuccess(KEY_RECOMMEND);
        } catch (Exception e) {
            breaker.recordFailure(KEY_RECOMMEND);
            log.warn("LLM 调用失败，已记录熔断计数: key={}, err={}", KEY_RECOMMEND, e.getMessage());
            return FALLBACK_RECOMMEND;
        }
        return applyOutputSafety("recommend", result);
    }

    // ── 作品寄语：走熔断 ──

    private static final String KEY_MESSAGE = "llm-message";
    private static final String FALLBACK_MESSAGE = "（暂时无法生成寄语，你的专注本身就很美好）";

    /**
     * 生成作品寄语（含输出安全过滤）。
     *
     * <p>LLM 返回后自动调 {@link ContentSafetyFilter#checkOutput} 做输出过滤，
     * 命中 {@link SafetyAction#REWRITE} 时用安全文本替换。
     * 审计日志由 checkOutput 内部写入，调用方无需再显式过滤。
     *
     * @param workTitle 作品标题
     * @param mood      创作时的心情
     * @return LLM 生成的寄语文本；熔断或失败时返回降级文案
     */
    public String workMessage(String workTitle, String mood) {
        if (!breaker.allowRequest(KEY_MESSAGE)) {
            log.warn("熔断中，走降级: key={}", KEY_MESSAGE);
            return FALLBACK_MESSAGE;
        }
        String result;
        try {
            result = llmClient.workMessage(workTitle, mood);
            breaker.recordSuccess(KEY_MESSAGE);
        } catch (Exception e) {
            breaker.recordFailure(KEY_MESSAGE);
            log.warn("LLM 调用失败，已记录熔断计数: key={}, err={}", KEY_MESSAGE, e.getMessage());
            return FALLBACK_MESSAGE;
        }
        return applyOutputSafety("message", result);
    }

    /** Knowledge answers keep the same audit and isolated breaker boundary as other AI calls. */
    public String knowledgeAnswer(String question, String prompt) {
        return knowledgeAnswer(question, () -> prompt);
    }

    public String knowledgeAnswer(String question, java.util.function.Supplier<String> promptSupplier) {
        SafetyResult input = contentSafetyFilter.checkInput(UserContext.getUserId(), null, "rag", question);
        if (input.getAction() == SafetyAction.BLOCK) return input.getCareMessage();
        String fallback = "非遗问答暂时不可用，请稍后再试。";
        if (!breaker.allowRequest("llm-rag")) return fallback;
        String answer;
        try {
            String prompt = promptSupplier.get();
            if (prompt == null) return "知识库暂未找到相关资料，可以换个关键词试试。";
            answer = llmClient.chat(prompt);
            if (answer == null || answer.isBlank()) throw new IllegalStateException("empty answer");
            breaker.recordSuccess("llm-rag");
        } catch (Exception e) {
            breaker.recordFailure("llm-rag");
            log.warn("知识问答调用失败，已使用固定降级文本");
            return fallback;
        }
        return applyOutputSafety("rag", answer);
    }

    public record CreativeResponse(String text, boolean generated) {}

    public CreativeResponse lineartPlan(String theme, String prompt) {
        SafetyResult input=contentSafetyFilter.checkInput(UserContext.getUserId(),null,"lineart",theme);
        if(input.getAction()==SafetyAction.BLOCK)
            throw new com.zhiyu.common.exception.BusinessException(1001,"这个主题暂不适合生成线稿。如需支持，可前往心理援助页面。");
        if(!breaker.allowRequest("llm-lineart"))return new CreativeResponse("",false);
        try {
            String answer=llmClient.chat(prompt);
            if(answer==null||answer.isBlank())throw new IllegalStateException("empty output");
            SafetyResult output=contentSafetyFilter.checkOutput(UserContext.getUserId(),"lineart",answer);
            breaker.recordSuccess("llm-lineart");
            return output.getAction()==SafetyAction.PASS?new CreativeResponse(answer,true):new CreativeResponse("",false);
        }catch(Exception e){
            breaker.recordFailure("llm-lineart");
            log.warn("线稿方案生成暂不可用，返回内置模板");
            return new CreativeResponse("",false);
        }
    }

    // ── 内部：输出安全过滤 ──

    /**
     * 对 LLM 输出文本做安全过滤。
     *
     * <p>从 {@link UserContext} 取当前 userId（同步接口均在请求线程内调用，
     * ThreadLocal 可用）。命中 REWRITE 时替换为安全文本，审计日志由
     * {@link ContentSafetyFilter#checkOutput} 内部写入。
     */
    private String applyOutputSafety(String scene, String text) {
        Long userId = UserContext.getUserId();
        SafetyResult sr = contentSafetyFilter.checkOutput(userId, scene, text);
        if (sr.getAction() == SafetyAction.REWRITE && sr.getRewrittenText() != null) {
            log.warn("AI 输出越界已改写: scene={}, userId={}, reason={}", scene, userId, sr.getReason());
            return sr.getRewrittenText();
        }
        return text;
    }

    /** 暴露底层 store 名（可观测） */
    public String breakerStoreName() {
        return breaker.implementationName();
    }
}
