package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
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
 * <p>审计粒度：对话与语音的输入/输出各一条；推荐/寄语只写输出一条；
 * 熔断或 LLM 调用失败时不写审计。审计由
 * {@link ContentSafetyFilter#checkInput} / {@link ContentSafetyFilter#checkOutput}
 * 内部写入，且都带 userId。
 */
@Slf4j
@Component
public class AiClient {

    private final LlmClient llmClient;
    private final CircuitBreakerStore breaker;
    private final ContentSafetyFilter contentSafetyFilter;
    private final com.zhiyu.ai.routing.ModelRouter modelRouter;
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

    /**
     * Spring 生产装配（4 参）：注入任务分档路由。
     * strong 档是否启用由 {@code zhiyu.ai.routing.strong-enabled} 决定。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AiClient(LlmClient llmClient, CircuitBreakerStore breaker,
                    ContentSafetyFilter contentSafetyFilter,
                    com.zhiyu.ai.routing.ModelRouter modelRouter) {
        this.llmClient = llmClient;
        this.breaker = breaker;
        this.contentSafetyFilter = contentSafetyFilter;
        // Mockito @InjectMocks 等场景可能传 null：按 strong 档关闭处理（与分档前行为一致）
        this.modelRouter = modelRouter != null ? modelRouter
                : new com.zhiyu.ai.routing.ModelRouter("deepseek-chat", "deepseek-reasoner", false);
        log.info("AiClient 初始化完成，breaker store={}，routing: {}",
                breaker.implementationName(), this.modelRouter.describe());
    }

    /**
     * 测试便捷装配（3 参）：strong 档关闭，倾听小结行为与分档前完全一致，
     * 不改变既有单测语义。
     */
    public AiClient(LlmClient llmClient, CircuitBreakerStore breaker,
                    ContentSafetyFilter contentSafetyFilter) {
        this(llmClient, breaker, contentSafetyFilter,
                new com.zhiyu.ai.routing.ModelRouter("deepseek-chat", "deepseek-reasoner", false));
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
     *   <li>PASS → 熔断检查 → 调 LLM，token 只累积到内部缓冲区，<b>不提前外发</b></li>
     *   <li>回复结束后 {@link ContentSafetyFilter#checkOutput}（写一条审计）；
     *       REWRITE/BLOCK 时用安全文本替换整个回复</li>
     *   <li>终检通过后，才把最终安全文本通过 {@code onToken} 回调一次</li>
     * </ol>
     *
     * <p><b>注意</b>：输出安全必须看到完整回复才能判定，因此本方法的
     * {@code onToken} 不是逐 token 实时流，而是“安全后的整段文本”。
     * 调用方不得绕过本方法把 {@link LlmClient} 的原始 token 直接推给客户端。
     *
     * @param sessionId 会话 ID（可空；用于 care_reminder 关联与脱敏摘要）
     * @param userInput 本轮用户输入明文（只检查本轮，不用历史消息代替）
     * @param history   对话历史（含本轮用户消息）
     * @param onToken   输出终检后的安全文本回调（整段一次）
     * @return 过滤结果与可展示文本
     */
    public StreamChatResult streamChat(Long sessionId, String userInput,
                                       List<ChatMessage> history, Consumer<String> onToken) {
        return streamChat(sessionId, userInput, history, onToken, null);
    }

    /**
     * 带预检输入的变体（P2 调度层，2026-11-04）：precheckedInput 非 null 时跳过门面内部
     * 输入检查——commander 已在路由前完成入口安检（审计+关怀落库），此处复用其结果，
     * 避免同一输入双写审计与双发 care_reminder。
     */
    public StreamChatResult streamChat(Long sessionId, String userInput,
                                       List<ChatMessage> history, Consumer<String> onToken,
                                       SafetyResult precheckedInput) {
        Long userId = UserContext.getUserId();
        SafetyResult input = precheckedInput != null ? precheckedInput
                : contentSafetyFilter.checkInput(userId, sessionId, "chat", userInput);
        if (input.getAction() == SafetyAction.BLOCK) {
            log.warn("流式对话输入侧拦截: userId={}, sessionId={}, 不调 LLM", userId, sessionId);
            return new StreamChatResult(input, null, input.getCareMessage());
        }

        if (!breaker.allowRequest("llm-chat")) {
            throw new com.zhiyu.common.exception.BusinessException(4001, "对话服务暂时不可用");
        }
        StringBuilder fullReply = new StringBuilder();
        try {
            // 原始 token 只进缓冲区；输出终检前绝不外发。
            llmClient.streamChat(history, fullReply::append);
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
        onToken.accept(reply);
        return new StreamChatResult(input, output, reply);
    }

    // ── 句级真流式对话（SSE A 方案，2026-11-02 拍板，docs/project-plan-20261102.md §2.1） ──

    /**
     * 句级真流式的返回结果。
     *
     * @param blocked 输入侧命中高危（未调 LLM）
     * @param crisis  done 事件的危机标志（输入/输出/句级任一侧命中高危）
     * @param revoked 是否需要前端撤回已推送内容并整体替换
     * @param reply   最终安全文本（revoked=true 时即替换文本；revoked=false 时等于已流出的全文）
     */
    public record LiveChatResult(SafetyResult input, SafetyResult output, String reply,
                                 boolean blocked, boolean crisis, boolean revoked) {
    }

    /** 句级安全检命中时用于中断 LLM 流的内部信号。 */
    private static final class SentenceSafetyInterrupt extends RuntimeException {
        private SentenceSafetyInterrupt() {
            super(null, null, false, false);
        }
    }

    /**
     * 句级真流式情绪对话。
     *
     * <p>时序：
     * <ol>
     *   <li>输入前置过滤（写一条审计；BLOCK 时不调 LLM、直接返回关怀话术）</li>
     *   <li>熔断检查 → 流式调 LLM，token 经 {@link SentenceSplitter} 分句；
     *       每句过 {@code scanStreamSentence}（不写审计）后立即回调 {@code onSentence}</li>
     *   <li>句级命中高危（BLOCK）→ 立即中断 LLM 流，终检写一条审计（scene=chat-revoked），
     *       返回关怀话术 + revoked=true + crisis=true</li>
     *   <li>流正常结束 → flush 尾句（同样过句级检）→ 全文终检（写一条审计）；
     *       终检非 PASS 时返回替换文本 + revoked=true</li>
     * </ol>
     * 输出审计整轮只写一条（在终检，revoked 时 scene 标记为 chat-revoked）。
     */
    public LiveChatResult streamChatLive(Long sessionId, String userInput,
                                         List<ChatMessage> history, Consumer<String> onSentence) {
        return streamChatLive(sessionId, userInput, history, onSentence, null);
    }

    /**
     * 带预检输入的句级真流式变体（P2 调度层，2026-11-04）：precheckedInput 非 null 时
     * 跳过门面内部输入检查（commander 入口安检结果复用），杜绝双审计/双关怀触发。
     */
    public LiveChatResult streamChatLive(Long sessionId, String userInput,
                                         List<ChatMessage> history, Consumer<String> onSentence,
                                         SafetyResult precheckedInput) {
        Long userId = UserContext.getUserId();
        SafetyResult input = precheckedInput != null ? precheckedInput
                : contentSafetyFilter.checkInput(userId, sessionId, "chat", userInput);
        if (input.getAction() == SafetyAction.BLOCK) {
            log.warn("流式对话输入侧拦截(live): userId={}, sessionId={}, 不调 LLM", userId, sessionId);
            return new LiveChatResult(input, null, input.getCareMessage(), true, true, true);
        }

        if (!breaker.allowRequest("llm-chat")) {
            throw new com.zhiyu.common.exception.BusinessException(4001, "对话服务暂时不可用");
        }

        StringBuilder full = new StringBuilder();
        SentenceSplitter splitter = new SentenceSplitter();
        final SafetyResult[] interruptScan = new SafetyResult[1];
        try {
            llmClient.streamChat(history, token -> {
                full.append(token);
                for (String sentence : splitter.feed(token)) {
                    SafetyResult scan = contentSafetyFilter.scanStreamSentence(userId, sessionId, sentence);
                    if (scan.getAction() != SafetyAction.PASS) {
                        interruptScan[0] = scan;
                        throw new SentenceSafetyInterrupt();
                    }
                    onSentence.accept(sentence);
                }
            });
            breaker.recordSuccess("llm-chat");
        } catch (SentenceSafetyInterrupt e) {
            // 模型本身未失败，不计熔断；中断原因在 interruptScan
            breaker.recordSuccess("llm-chat");
        } catch (RuntimeException e) {
            breaker.recordFailure("llm-chat");
            throw e;
        }

        // 流正常结束：尾句同样过句级检
        if (interruptScan[0] == null) {
            String tail = splitter.flush();
            if (tail != null) {
                SafetyResult scan = contentSafetyFilter.scanStreamSentence(userId, sessionId, tail);
                if (scan.getAction() != SafetyAction.PASS) {
                    interruptScan[0] = scan;
                } else {
                    onSentence.accept(tail);
                }
            }
        }

        boolean interrupted = interruptScan[0] != null;
        boolean blocked = interrupted && interruptScan[0].getAction() == SafetyAction.BLOCK;

        if (blocked) {
            // 句级高危：终检写一条 revoked 审计，返回关怀话术，不再发任何正文
            SafetyResult blockedOutput = contentSafetyFilter.checkOutput(userId, "chat-revoked", full.toString());
            return new LiveChatResult(input, blockedOutput, interruptScan[0].getCareMessage(), false, true, true);
        }

        SafetyResult output = contentSafetyFilter.checkOutput(
                userId, interrupted ? "chat-revoked" : "chat", full.toString());
        boolean crisis = input.isHighRisk() || output.isHighRisk();
        String reply;
        boolean revoked;
        if (output.getAction() != SafetyAction.PASS) {
            String rewritten = output.getRewrittenText();
            reply = (rewritten == null || rewritten.isBlank()) ? SAFE_CHAT_REPLY : rewritten;
            revoked = true;
        } else {
            reply = full.toString();
            // 句级中断（越界词）但终检 PASS：已流出内容不完整，仍需整体替换
            revoked = interrupted;
        }
        return new LiveChatResult(input, output, reply, false, crisis, revoked);
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

    /**
     * 生成创作回顾（一次性、非流式）。
     *
     * <p>输入只包含用户自填的作品名、情绪词与备注；不读取情绪对话原文。
     * 复用 {@code llm-message} 熔断 key；输入/输出均走内容安全过滤，
     * 输出越界或服务不可用时返回 {@code null}，由业务层使用固定模板。
     */
    public String reflectionSummary(String prompt, String userText) {
        SafetyResult input = contentSafetyFilter.checkInput(
                UserContext.getUserId(), null, "reflection", userText);
        if (input.getAction() == SafetyAction.BLOCK) {
            log.warn("创作回顾输入侧拦截，使用固定模板");
            return null;
        }
        if (!breaker.allowRequest(KEY_MESSAGE)) {
            log.warn("熔断中，创作回顾使用固定模板: key={}", KEY_MESSAGE);
            return null;
        }
        String result;
        try {
            result = llmClient.reflectionSummary(prompt);
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("empty reflection summary");
            }
            breaker.recordSuccess(KEY_MESSAGE);
        } catch (Exception e) {
            breaker.recordFailure(KEY_MESSAGE);
            log.warn("创作回顾生成失败，使用固定模板: {}", e.getMessage());
            return null;
        }
        SafetyResult output = contentSafetyFilter.checkOutput(
                UserContext.getUserId(), "reflection", result);
        if (output.getAction() == SafetyAction.BLOCK) {
            return null;
        }
        if (output.getAction() == SafetyAction.REWRITE
                && output.getRewrittenText() != null
                && !output.getRewrittenText().isBlank()) {
            return output.getRewrittenText();
        }
        return result;
    }

    public record ListeningResult(String text, boolean generated, boolean blocked) {
    }

    /** strong 档独立熔断资源 key（ai-config/ai-capabilities.yaml resilience.resources 已注册）。 */
    private static final String KEY_STRONG = "llm-strong";

    /**
     * 独立倾听小结。只接收本人已结束会话的用户发言；高危输入不生成，
     * 由业务层保留既有支持流程。
     *
     * <p>失败回落链（B2，2026-11-04）：strong 档（{@code llm-strong} 熔断隔离）
     * → standard 档（{@code llm-message} 熔断隔离）→ 返回 {@code generated=false}
     * 由业务层使用固定模板。每一档失败都记 WARN 日志与熔断计数，<b>不静默降级</b>。
     */
    public ListeningResult listeningSummary(Long sessionId, String prompt, String userText) {
        SafetyResult input = contentSafetyFilter.checkInput(
                UserContext.getUserId(), sessionId, "listening", userText);
        if (input.getAction() == SafetyAction.BLOCK) {
            return new ListeningResult(null, false, true);
        }
        // Mock 是本地降级实现，不能把它标成“已生成”，否则会掩盖未配置真实模型。
        if (llmClient instanceof MockLlmClient) {
            return new ListeningResult(null, false, false);
        }

        // ── 第一档：strong（倾听小结的声明档位） ──
        var route = modelRouter.resolve("listening_summary");
        if (route.llmAllowed() && route.available()) {
            if (breaker.allowRequest(KEY_STRONG)) {
                try {
                    var generation = llmClient.chatWithUsage(prompt, route.model());
                    String result = generation == null ? null : generation.text();
                    if (result == null || result.isBlank()) {
                        throw new IllegalStateException("empty listening summary");
                    }
                    breaker.recordSuccess(KEY_STRONG);
                    log.info("倾听小结由 strong 档生成: model={}", route.model());
                    return finishListening(result);
                } catch (Exception e) {
                    breaker.recordFailure(KEY_STRONG);
                    log.warn("倾听小结 strong 档失败（已记熔断），显式回落 standard 档: model={}, err={}",
                            route.model(), e.getMessage());
                }
            } else {
                log.warn("倾听小结 strong 档熔断中，显式回落 standard 档");
            }
        } else if (!route.available()) {
            log.info("倾听小结 strong 档未启用/未配置，直接使用 standard 档");
        }

        // ── 第二档：standard（分档前的原路径） ──
        if (!breaker.allowRequest(KEY_MESSAGE)) {
            return new ListeningResult(null, false, false);
        }
        try {
            String result = llmClient.listeningSummary(prompt);
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("empty listening summary");
            }
            breaker.recordSuccess(KEY_MESSAGE);
            return finishListening(result);
        } catch (Exception e) {
            breaker.recordFailure(KEY_MESSAGE);
            log.warn("倾听小结 standard 档也失败，由业务层使用固定模板（不静默）: {}", e.getMessage());
            return new ListeningResult(null, false, false);
        }
    }

    /** 输出安全过滤后封装倾听小结结果（strong/standard 两档共用）。 */
    private ListeningResult finishListening(String result) {
        SafetyResult output = contentSafetyFilter.checkOutput(
                UserContext.getUserId(), "listening", result);
        if (output.getAction() == SafetyAction.BLOCK) {
            return new ListeningResult(null, false, false);
        }
        String safe = output.getAction() == SafetyAction.REWRITE
                && output.getRewrittenText() != null
                && !output.getRewrittenText().isBlank()
                ? output.getRewrittenText() : result;
        return new ListeningResult(safe.trim(), true, false);
    }

    // ── 对话上下文滚动摘要（P1b 2026-11-04） ──

    /** 上下文摘要独立熔断资源 key（ai-config/ai-capabilities.yaml resilience.resources 已注册）。 */
    private static final String KEY_SUMMARY = "llm-summary";

    /**
     * 生成对话上下文滚动摘要（P1b，仅倾听链）。
     *
     * <p>刻意走 <b>standard 档</b>（{@code llmClient.chat} 默认配置）：摘要属于后台增量任务，
     * 不得占用 strong 档（deepseek-reasoner）造成成本与延迟失控。
     * 失败语义一律「返回 null」——熔断中、Mock 未配置真实模型、调用异常、输出空、
     * 输出安全未 PASS——由调用方（DialogueContextService）降级为「本轮不做摘要，
     * 保留原文窗口」，绝不让对话主链路因摘要失败而失败。
     * 输出经 {@link ContentSafetyFilter#checkOutput} 过滤并落审计（AGENTS.md §4）。
     *
     * @param prompt 渲染后的摘要请求（context-summary.st，含旧摘要与滑出轮次原文）
     * @return 摘要文本；任何失败返回 null
     */
    public String contextSummary(String prompt) {
        // Mock 是本地降级实现，不能把它产出的模板句当成"已生成摘要"落库（同 listeningSummary 判定）
        if (llmClient instanceof MockLlmClient) {
            return null;
        }
        if (!breaker.allowRequest(KEY_SUMMARY)) {
            log.warn("上下文摘要熔断中，本轮跳过（降级保留原文窗口）: key={}", KEY_SUMMARY);
            return null;
        }
        try {
            String result = llmClient.chat(prompt);
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("empty context summary");
            }
            SafetyResult output = contentSafetyFilter.checkOutput(
                    UserContext.getUserId(), "summary", result);
            if (output.getAction() != SafetyAction.PASS) {
                log.warn("上下文摘要输出未过终检（降级保留原文窗口）: action={}, reason={}",
                        output.getAction(), output.getReason());
                breaker.recordSuccess(KEY_SUMMARY);
                return null;
            }
            breaker.recordSuccess(KEY_SUMMARY);
            return result.trim();
        } catch (Exception e) {
            breaker.recordFailure(KEY_SUMMARY);
            log.warn("上下文摘要生成失败（已记熔断，降级保留原文窗口）: err={}", e.getMessage());
            return null;
        }
    }

    /**
     * @param text           模型原始计划 JSON；输出被安全改写时仍保留原始 JSON 供编排层解析
     * @param generated      是否由真实模型产出；Mock/熔断/异常为 false
     * @param blocked        是否命中高危或输出边界；true 时不得执行工具
     * @param rewrittenReply 输出侧安全改写后的用户可读文本；非空时编排层应丢弃工具并使用该文本
     */
    public record CompanionResult(String text, boolean generated, boolean blocked,
                                  String rewrittenReply, Integer inputTokens,
                                  Integer outputTokens) {
        public CompanionResult(String text, boolean generated, boolean blocked,
                               String rewrittenReply) {
            this(text, generated, blocked, rewrittenReply, null, null);
        }
    }

    /**
     * 受控陪伴计划：模型只能输出严格 JSON 计划，业务层再校验工具白名单并执行。
     * 高危输入直接返回关怀话术，不读取会话或执行工具。
     */
    public CompanionResult companionPlan(Long practiceSessionId, String prompt, String userText) {
        SafetyResult input = contentSafetyFilter.checkInput(
                UserContext.getUserId(), practiceSessionId, "companion", userText);
        if (input.getAction() == SafetyAction.BLOCK) {
            return new CompanionResult(input.getCareMessage(), false, true, null);
        }
        if (llmClient instanceof MockLlmClient) {
            return new CompanionResult(null, false, false, null);
        }
        if (!breaker.allowRequest(KEY_MESSAGE)) {
            return new CompanionResult(null, false, false, null);
        }
        try {
            com.zhiyu.ai.client.LlmChatResult generation = llmClient.chatWithUsage(prompt);
            String result = generation == null ? null : generation.text();
            if (result == null || result.isBlank()) {
                throw new IllegalStateException("empty companion plan");
            }
            breaker.recordSuccess(KEY_MESSAGE);
            SafetyResult output = contentSafetyFilter.checkOutput(
                    UserContext.getUserId(), "companion", result);
            if (output.getAction() == SafetyAction.BLOCK) {
                return new CompanionResult(null, false, true, SAFE_CHAT_REPLY,
                        generation.inputTokens(), generation.outputTokens());
            }
            if (output.getAction() == SafetyAction.REWRITE
                    && output.getRewrittenText() != null
                    && !output.getRewrittenText().isBlank()) {
                // 保留原始 JSON 供编排层解析阶段；改写文本交给编排层替换 reply 并丢弃工具。
                return new CompanionResult(result, true, false, output.getRewrittenText().trim(),
                        generation.inputTokens(), generation.outputTokens());
            }
            return new CompanionResult(result, true, false, null,
                    generation.inputTokens(), generation.outputTokens());
        } catch (Exception e) {
            breaker.recordFailure(KEY_MESSAGE);
            log.warn("陪伴计划生成失败，由编排层使用确定性建议");
            return new CompanionResult(null, false, false, null);
        }
    }

    /** Knowledge answers keep the same audit and isolated breaker boundary as other AI calls. */
    public record KnowledgeResult(String text, boolean generated, boolean blocked) {
    }

    /** 结构化检索前的输入审核；不调用模型，供向量不可用的问答链路复用。 */
    public KnowledgeResult reviewKnowledgeQuestion(String question) {
        SafetyResult input = contentSafetyFilter.checkInput(UserContext.getUserId(), null, "rag", question);
        if (input.getAction() == SafetyAction.BLOCK) {
            return new KnowledgeResult(input.getCareMessage(), false, true);
        }
        return new KnowledgeResult("", false, false);
    }

    public String knowledgeAnswer(String question, String prompt) {
        return knowledgeAnswerResult(question, () -> prompt).text();
    }

    public String knowledgeAnswer(String question, java.util.function.Supplier<String> promptSupplier) {
        return knowledgeAnswerResult(question, promptSupplier).text();
    }

    public KnowledgeResult knowledgeAnswerResult(String question,
                                                 java.util.function.Supplier<String> promptSupplier) {
        KnowledgeResult review = reviewKnowledgeQuestion(question);
        if (review.blocked()) return review;
        return generateKnowledgeAnswer(promptSupplier);
    }

    /** 已由 reviewKnowledgeQuestion 审核过输入时使用，避免重复审计。 */
    public KnowledgeResult knowledgeAnswerResultReviewed(String question,
                                                         java.util.function.Supplier<String> promptSupplier) {
        return generateKnowledgeAnswer(promptSupplier);
    }

    private KnowledgeResult generateKnowledgeAnswer(java.util.function.Supplier<String> promptSupplier) {
        String fallback = "非遗问答暂时不可用，请稍后再试。";
        if (!breaker.allowRequest("llm-rag")) return new KnowledgeResult(fallback, false, false);
        String answer;
        try {
            String prompt = promptSupplier.get();
            if (prompt == null) {
                return new KnowledgeResult("知识库暂未找到相关资料，可以换个关键词试试。", false, false);
            }
            answer = llmClient.chat(prompt);
            if (answer == null || answer.isBlank()) throw new IllegalStateException("empty answer");
            breaker.recordSuccess("llm-rag");
        } catch (Exception e) {
            breaker.recordFailure("llm-rag");
            log.warn("知识问答调用失败，已使用固定降级文本");
            return new KnowledgeResult(fallback, false, false);
        }
        return new KnowledgeResult(applyOutputSafety("rag", answer), true, false);
    }

    public record CreativeResponse(String text, boolean generated) {}

    /**
     * 非遗纹样共创：复用 lineart 熔断资源，输入/输出均走安全过滤。
     * 只返回模型原始 JSON 文本；变体白名单、数量和色板由业务层校验。
     * 输入只包含纹样结构、主题和色板，不包含对话、作品图片或心理记录。
     */
    public String heritageVariants(String prompt, String userText) {
        SafetyResult input = contentSafetyFilter.checkInput(
                UserContext.getUserId(), null, "heritage", userText);
        if (input.getAction() == SafetyAction.BLOCK) return null;
        if (!breaker.allowRequest("llm-lineart")) return null;
        try {
            String answer = llmClient.chat(prompt);
            if (answer == null || answer.isBlank()) throw new IllegalStateException("empty heritage variants");
            breaker.recordSuccess("llm-lineart");
            SafetyResult output = contentSafetyFilter.checkOutput(
                    UserContext.getUserId(), "heritage", answer);
            return output.getAction() == SafetyAction.PASS ? answer : null;
        } catch (Exception e) {
            breaker.recordFailure("llm-lineart");
            log.warn("非遗纹样共创不可用，使用内置变体");
            return null;
        }
    }

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

    /** 当前实际接入模式；Mock 不得被标记为真实模型。 */
    public String modelMode() {
        return llmClient instanceof MockLlmClient ? "MOCK" : "EXTERNAL";
    }

    /** 当前模型名；用于 AI 运营页版本展示与 trace。 */
    public String modelName() {
        return llmClient.modelName();
    }
}
