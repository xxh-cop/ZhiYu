package com.zhiyu.modules.emotion.controller;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.modules.dialogue.DialogueCommander;
import com.zhiyu.modules.dialogue.DialogueTurnRequest;
import com.zhiyu.modules.dialogue.DialogueTurnResult;
import com.zhiyu.modules.emotion.dto.ChatDTO;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情绪倾听 SSE 控制器（T1.3a / T1.3b）。
 *
 * <p>POST /api/emotion/chat —— SSE 流式对话。
 *
 * <p>流程（内容安全过滤 / 审计全部下沉到 {@link AiClient#streamChat}，本类不重复过滤）：
 * <ol>
 *   <li>从 UserContext 取 userId</li>
 *   <li>sessionId 为 null → 新建会话</li>
 *   <li>保存用户消息（加密）并加载历史</li>
 *   <li>调 AiClient.streamChat（门面内部完成输入前置过滤 + 输出终检 + 两次审计）</li>
 *   <li>输入被 BLOCK（命中高危）→ 推送关怀话术 → event:done(crisis=true)，
 *       <b>不调 LLM</b>，直接返回</li>
 *   <li>正常 → 保存门面返回的安全回复（加密）→ 推送 → event:done(crisis)</li>
 *   <li>异常发 event: error（HTTP 恒 200）</li>
 * </ol>
 *
 * <p>安全提示：门面可能把流式 token 回调给本类后再在完整回复上做 REWRITE，
 * 因此本类只累积 token、不逐条转发；最终只发送 {@link AiClient.StreamChatResult#reply()}。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class EmotionController {

    private static final long SSE_TIMEOUT = 120_000L;

    /** 会话总结最大长度（取 AI 最后回复的前 N 字） */
    private static final int SUMMARY_MAX_LENGTH = 100;

    private final com.zhiyu.security.BusinessAccess access;
    private final AiClient aiClient;
    private final EmotionService emotionService;
    private final ObjectMapper objectMapper;
    private final com.zhiyu.modules.dialogue.DialogueContextService dialogueContext;
    private final DialogueCommander dialogueCommander;
    @Qualifier("sseExecutor")
    private final ThreadPoolTaskExecutor sseExecutor;

    /** D4 修复（2026-11-04 P1b）：历史窗口从配置读取，不再硬编码 10（改配置真生效）。 */
    @org.springframework.beans.factory.annotation.Value("${zhiyu.emotion.max-history-turns:10}")
    private int maxHistoryTurns;

    /** P1b 滚动摘要总开关（默认 false）：false 时本类不触碰 DialogueContextService，行为与旧版一致。 */
    @org.springframework.beans.factory.annotation.Value("${zhiyu.dialogue.rolling-summary:false}")
    private boolean rollingSummaryEnabled;

    /**
     * AK1（2026-11-04）细粒度灰度：倾听链是否委托调度层由本开关单独控制，
     * 与 C1 端点注册（commander）解耦——可以只放倾听、陪伴（companion-adapter，P3）不动。
     * 危机输入不受任何开关影响：两条路径都先做入口安检、零 LLM（永久直连语义）。
     */
    @org.springframework.beans.factory.annotation.Value("${zhiyu.dialogue.listening-via-commander:false}")
    private boolean listeningViaCommander;

    @PostMapping(value = "/api/emotion/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatDTO dto) {
        Long userId = access.consentedUser();
        if (dto.getSessionId() != null) access.emotionSession(dto.getSessionId(), userId);
        var identity = UserContext.get();
        // P2 调度层（AK1 细粒度：仅 listening-via-commander 开启时委托；默认关闭走原路径）
        if (listeningViaCommander) {
            return chatViaCommander(dto, userId, identity);
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseExecutor.execute(() -> {
            var previousIdentity = UserContext.get();
            UserContext.set(identity);
            try {
                access.consentedUser();

                // 1. 新建或复用会话
                Long sessionId = dto.getSessionId();
                if (sessionId == null) {
                    sessionId = emotionService.createSession(userId, dto.getMood());
                }

                // 2. 保存用户消息（加密，幂等）并加载历史；内容安全检查由 AiClient 门面负责
                emotionService.appendMessage(sessionId, "user", dto.getContent());
                List<ChatMessage> history = emotionService.loadRecentHistory(sessionId, maxHistoryTurns);
                // P1b（2026-11-04）：rolling-summary 开启时，若存在有效滚动摘要，
                // 以 system 消息注入到原文窗口之前；摘要由服务端生成与加密存储（唯一权威）。
                // 开关关闭时本分支不执行，历史与旧版完全一致（见 DialogueContextService 注解）。
                if (rollingSummaryEnabled) {
                    String contextPrefix = dialogueContext.summaryPrefix(userId, sessionId);
                    if (contextPrefix != null) {
                        var withContext = new java.util.ArrayList<ChatMessage>(history.size() + 1);
                        withContext.add(ChatMessage.system(contextPrefix));
                        withContext.addAll(history);
                        history = withContext;
                    }
                }

                if ("live".equals(dto.getMode())) {
                    handleLiveStream(emitter, userId, sessionId, dto.getContent(), history);
                    return;
                }

                // 3. 门面内部：输入前置过滤（命中高危不调 LLM）→ 熔断 → 生成 →
                //    完整回复输出终检。终检前不外发任何 token。
                AiClient.StreamChatResult chat = aiClient.streamChat(
                        sessionId, dto.getContent(), history, ignored -> { });

                // 3a. 输入命中高危 → 危机干预流程：门面未调 LLM
                if (chat.blocked()) {
                    String careMessage = chat.reply();
                    if (careMessage != null) {
                        emitter.send(SseEmitter.event().data(careMessage));
                    }
                    // 更新会话总结（危机路径：用关怀话术作为 summary，不改 status）
                    emotionService.updateSummary(sessionId, truncate(careMessage));
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(donePayload(sessionId, true, true)));
                    emitter.complete();
                    log.warn("危机干预触发: userId={}, sessionId={}, 不调 LLM", userId, sessionId);
                    return;
                }

                // 4. 保存并推送门面筛过的安全回复
                String safeReply = chat.reply();
                access.consentedUser();
                access.emotionSession(sessionId, userId);
                emotionService.appendMessage(sessionId, "assistant", safeReply);
                emotionService.updateSummary(sessionId, truncate(safeReply));
                // P1b：滚动摘要触发点（失败内部消化，绝不影响本轮回包）
                if (rollingSummaryEnabled) {
                    dialogueContext.maybeRoll(userId, sessionId);
                }
                emitter.send(SseEmitter.event().data(safeReply));

                // 5. done 事件（携带 crisis 标志）
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(donePayload(sessionId, chat.crisis(), false, chat.provider())));
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE 对话异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"code\":5000,\"message\":\"服务暂时不可用\"}"));
                } catch (IOException ignored) {
                }
                emitter.complete();
            } finally {
                if (previousIdentity == null) UserContext.clear(); else UserContext.set(previousIdentity);
            }
        });

        return emitter;
    }

    /**
     * P2 调度层委托路径（{@code zhiyu.dialogue.commander=true} 时启用；默认关闭走原路径）。
     *
     * <p><b>协议零破坏</b>：事件形状与原路径一致——legacy 模式回复走无名 data 事件、
     * live 模式走 chunk/revoke 命名事件、done 载荷恒为 {sessionId, crisis, revoked}
     * （不新增 capability 字段，前端不改也能跑）。
     *
     * <p>委托路径恒路由到 LISTENING（entryContext=LISTENING_PAGE、无 intentHint /
     * practiceSessionId）；路由与三道闸由 commander 执行并记 ai_trace（scene=dialogue-route）。
     *
     * <p>危机路径差异（交付中声明）：commander 安检在能力执行前短路，用户消息与关怀话术
     * 不落 emotion_message（care_reminder 与 ai_audit_log 仍由过滤器照常写库）；
     * 会话 id 由本方法预建，保证 done.sessionId 非空。
     */
    private SseEmitter chatViaCommander(ChatDTO dto, Long userId, LoginUser identity) {
        boolean live = "live".equals(dto.getMode());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseExecutor.execute(() -> {
            var previousIdentity = UserContext.get();
            UserContext.set(identity);
            try {
                Long sessionId = dto.getSessionId();
                if (sessionId == null) {
                    sessionId = emotionService.createSession(userId, dto.getMood());
                }
                DialogueTurnResult result = dialogueCommander.chat(new DialogueTurnRequest(
                        userId, sessionId, null, dto.getContent(),
                        "LISTENING_PAGE", null, live,
                        live ? sentence -> sendChunkEvent(emitter, sentence)
                                : sentence -> sendRawEvent(emitter, sentence),
                        null));

                if (!live && !result.crisis()) {
                    // legacy：整段安全回复走无名 data 事件（危机关怀话术已由 sink 下发）
                    emitter.send(SseEmitter.event().data(result.reply()));
                } else if (live && result.revoked() && !result.crisis()) {
                    emitter.send(SseEmitter.event()
                            .name("revoke")
                            .data(objectMapper.writeValueAsString(
                                    Map.of("text", result.reply(), "reason", "safety"))));
                }
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(donePayload(result.sessionId() != null ? result.sessionId() : sessionId,
                                result.crisis(), result.revoked(), result.provider())));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 对话异常（委托路径）: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"code\":5000,\"message\":\"服务暂时不可用\"}"));
                } catch (IOException ignored) {
                }
                emitter.complete();
            } finally {
                if (previousIdentity == null) UserContext.clear(); else UserContext.set(previousIdentity);
            }
        });
        return emitter;
    }

    /** live 委托路径的 chunk 事件（与 handleLiveStream 的 chunk 形状一致）。 */
    private void sendChunkEvent(SseEmitter emitter, String sentence) {
        try {
            emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(objectMapper.writeValueAsString(Map.of("text", sentence))));
        } catch (IOException e) {
            throw new IllegalStateException("chunk 发送失败", e);
        }
    }

    /** legacy 委托路径的无名 data 事件（危机关怀话术沿用旧形状）。 */
    private void sendRawEvent(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().data(text));
        } catch (IOException e) {
            throw new IllegalStateException("data 发送失败", e);
        }
    }

    /**
     * 句级真流式分支（SSE A 方案，mode=live）。
     *
     * <p>事件协议（见 openapi.yaml）：
     * chunk（句级增量）→ 可选 revoke（整体替换）→ done（含 crisis/revoked）。
     * 输入侧高危：只发 chunk（关怀话术）+ done(crisis=true, revoked=true)，不调 LLM。
     */
    private void handleLiveStream(SseEmitter emitter, Long userId, Long sessionId,
                                  String content, List<ChatMessage> history) throws IOException {
        AiClient.LiveChatResult result = aiClient.streamChatLive(
                sessionId, content, history,
                sentence -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(objectMapper.writeValueAsString(Map.of("text", sentence))));
                    } catch (IOException e) {
                        throw new IllegalStateException("chunk 发送失败", e);
                    }
                });

        // 保存最终安全回复（危机路径与普通路径统一落库）
        access.consentedUser();
        access.emotionSession(sessionId, userId);
        emotionService.appendMessage(sessionId, "assistant", result.reply());
        emotionService.updateSummary(sessionId, truncate(result.reply()));
        // P1b：滚动摘要触发点（失败内部消化，绝不影响本轮回包）
        if (rollingSummaryEnabled) {
            dialogueContext.maybeRoll(userId, sessionId);
        }

        if (result.blocked()) {
            // 输入侧高危：关怀话术以 chunk 下发（此时门面未发任何 chunk）
            emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(objectMapper.writeValueAsString(Map.of("text", result.reply()))));
        } else if (result.revoked()) {
            emitter.send(SseEmitter.event()
                    .name("revoke")
                    .data(objectMapper.writeValueAsString(
                            Map.of("text", result.reply(), "reason", "safety"))));
        }
        emitter.send(SseEmitter.event()
                .name("done")
                .data(donePayload(sessionId, result.crisis(), result.revoked(), result.provider())));
        emitter.complete();
    }

    /**
     * 构造 done 事件的 JSON payload（M3-B1：加 crisis 字段；A 方案加 revoked）。
     *
     * <p>用 {@link ObjectMapper} 序列化 {@link Map}，避免手工拼 JSON 的转义问题。
     * 输出示例：{@code {"sessionId":123,"crisis":false,"revoked":false}}
     */
    private String donePayload(Long sessionId, boolean crisis, boolean revoked) {
        return donePayload(sessionId, crisis, revoked, null);
    }

    /**
     * 带归属的 done payload（2026-11-08 装配层批）：{@code provider} 非空时才写入——
     * 与 ai_trace 同源同词表（deepseek/ollama/mock），前端据此做归属标注；
     * 危机话术/安全改写等非模型产出的回包为 null，前端不得显示任何归属字样。
     */
    private String donePayload(Long sessionId, boolean crisis, boolean revoked, String provider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("crisis", crisis);
        payload.put("revoked", revoked);
        if (provider != null && !provider.isBlank()) {
            payload.put("provider", provider);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 兜底：保证 done 事件一定能发出
            return "{\"sessionId\":\"" + sessionId + "\",\"crisis\":" + crisis
                    + ",\"revoked\":" + revoked + "}";
        }
    }

    /**
     * 截取字符串前 {@value #SUMMARY_MAX_LENGTH} 个字符作为会话 summary。
     *
     * @param text 原始文本（可为 null）
     * @return 截断后的文本，null 输入返回 null
     */
    private String truncate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= SUMMARY_MAX_LENGTH
                ? text
                : text.substring(0, SUMMARY_MAX_LENGTH);
    }
}
