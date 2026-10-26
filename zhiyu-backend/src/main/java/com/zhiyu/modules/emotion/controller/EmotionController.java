package com.zhiyu.modules.emotion.controller;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.emotion.dto.ChatDTO;
import com.zhiyu.modules.emotion.service.EmotionService;
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
 * <p>流程（T1.3b 增加内容安全过滤）：
 * <ol>
 *   <li>从 UserContext 取 userId</li>
 *   <li>sessionId 为 null → 新建会话</li>
 *   <li><b>checkInput 安全检查</b>：
 *     <ul>
 *       <li>BLOCK（命中高危）→ 保存用户消息（加密）→ 推送关怀话术 → event:done，
 *           <b>不调 LLM</b>，直接返回</li>
 *       <li>PASS → 继续</li>
 *     </ul>
 *   </li>
 *   <li>保存用户消息（加密）</li>
 *   <li>加载历史</li>
 *   <li>调 AiClient.streamChat，逐 token SSE 推送</li>
 *   <li>流结束后先检查完整回复，使用安全替换文本后再推送</li>
 *   <li>保存 AI 回复（加密）</li>
 *   <li>发 event: done</li>
 *   <li>异常发 event: error（HTTP 恒 200）</li>
 * </ol>
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
    private final ContentSafetyFilter safetyFilter;
    private final ObjectMapper objectMapper;
    @Qualifier("sseExecutor")
    private final ThreadPoolTaskExecutor sseExecutor;

    @PostMapping(value = "/api/emotion/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatDTO dto) {
        Long userId = access.consentedUser();
        if (dto.getSessionId() != null) access.emotionSession(dto.getSessionId(), userId);
        var identity = UserContext.get();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseExecutor.execute(() -> {
            var previousIdentity = UserContext.get();
            UserContext.set(identity);
            try {
                access.consentedUser();
                // 危机检测标志（M3-B1：done 事件携带，前端据此决定是否展示援助入口）
                boolean crisisDetected = false;

                // 1. 新建或复用会话
                Long sessionId = dto.getSessionId();
                if (sessionId == null) {
                    sessionId = emotionService.createSession(userId, dto.getMood());
                }

                // 2. 输入侧安全检查（T1.3b）
                SafetyResult inputResult = safetyFilter.checkInput(userId, sessionId, "chat", dto.getContent());

                // 2a. 命中高危 → 危机干预流程：不调 LLM
                if (inputResult.getAction() == SafetyAction.BLOCK) {
                    crisisDetected = true;
                    // 保存用户消息（加密）
                    emotionService.appendMessage(sessionId, "user", dto.getContent());
                    // 推送关怀话术
                    String careMessage = inputResult.getCareMessage();
                    if (careMessage != null) {
                        emitter.send(SseEmitter.event().data(careMessage));
                    }
                    // 更新会话总结（危机路径：用关怀话术作为 summary，不改 status）
                    emotionService.updateSummary(sessionId, truncate(careMessage));
                    // 推送 done（携带 crisis 标志）
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(donePayload(sessionId, crisisDetected)));
                    emitter.complete();
                    log.warn("危机干预触发: userId={}, sessionId={}, 不调 LLM", userId, sessionId);
                    return;
                }

                // 3. PASS → 保存用户消息（加密）
                emotionService.appendMessage(sessionId, "user", dto.getContent());

                // 4. 加载历史
                List<ChatMessage> history = emotionService.loadRecentHistory(sessionId, 10);

                // 完整检查后才向客户端发送，避免跨 token 的危险文本提前泄漏。
                StringBuilder fullReply = new StringBuilder();
                aiClient.streamChat(history, fullReply::append);
                SafetyResult outputResult = safetyFilter.checkOutput(userId, "chat", fullReply.toString());
                String safeReply = fullReply.toString();
                if (outputResult.getAction() != SafetyAction.PASS) {
                    safeReply = outputResult.getRewrittenText();
                    if (safeReply == null || safeReply.isBlank()) safeReply = "我在这里陪着你。我们可以先停一停，关注当下的感受。本系统不能替代专业心理咨询。";
                    crisisDetected = outputResult.isHighRisk();
                }
                access.consentedUser();
                access.emotionSession(sessionId, userId);
                emotionService.appendMessage(sessionId, "assistant", safeReply);
                emotionService.updateSummary(sessionId, truncate(safeReply));
                emitter.send(SseEmitter.event().data(safeReply));

                // 8. 发送 done 事件（携带 crisis 标志）
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(donePayload(sessionId, crisisDetected)));
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
     * 构造 done 事件的 JSON payload（M3-B1：加 crisis 字段）。
     *
     * <p>用 {@link ObjectMapper} 序列化 {@link Map}，避免手工拼 JSON 的转义问题。
     * 输出示例：{@code {"sessionId":123,"crisis":false}}
     */
    private String donePayload(Long sessionId, boolean crisis) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("crisis", crisis);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 兜底：保证 done 事件一定能发出
            return "{\"sessionId\":\"" + sessionId + "\",\"crisis\":" + crisis + "}";
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
