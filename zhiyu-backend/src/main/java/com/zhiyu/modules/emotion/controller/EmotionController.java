package com.zhiyu.modules.emotion.controller;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.emotion.dto.ChatDTO;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.security.UserContext;
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
import java.util.List;

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
 *   <li>流结束后 <b>checkOutput</b> 检查完整回复（不阻塞流，事后审计；
 *       命中 REWRITE 仅记审计，已推送内容不改）</li>
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

    private final AiClient aiClient;
    private final EmotionService emotionService;
    private final ContentSafetyFilter safetyFilter;
    @Qualifier("sseExecutor")
    private final ThreadPoolTaskExecutor sseExecutor;

    @PostMapping(value = "/api/emotion/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatDTO dto) {
        Long userId = UserContext.getUserId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseExecutor.execute(() -> {
            try {
                // 1. 新建或复用会话
                Long sessionId = dto.getSessionId();
                if (sessionId == null) {
                    sessionId = emotionService.createSession(userId, dto.getMood());
                }

                // 2. 输入侧安全检查（T1.3b）
                SafetyResult inputResult = safetyFilter.checkInput(userId, sessionId, "chat", dto.getContent());

                // 2a. 命中高危 → 危机干预流程：不调 LLM
                if (inputResult.getAction() == SafetyAction.BLOCK) {
                    // 保存用户消息（加密）
                    emotionService.appendMessage(sessionId, "user", dto.getContent());
                    // 推送关怀话术
                    String careMessage = inputResult.getCareMessage();
                    if (careMessage != null) {
                        emitter.send(SseEmitter.event().data(careMessage));
                    }
                    // 推送 done
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("{\"sessionId\":\"" + sessionId + "\"}"));
                    emitter.complete();
                    log.warn("危机干预触发: userId={}, sessionId={}, 不调 LLM", userId, sessionId);
                    return;
                }

                // 3. PASS → 保存用户消息（加密）
                emotionService.appendMessage(sessionId, "user", dto.getContent());

                // 4. 加载历史
                List<ChatMessage> history = emotionService.loadRecentHistory(sessionId, 10);

                // 5. 流式对话
                StringBuilder fullReply = new StringBuilder();
                aiClient.streamChat(history, token -> {
                    try {
                        fullReply.append(token);
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        log.warn("SSE 发送 token 失败: {}", e.getMessage());
                    }
                });

                // 6. 输出侧安全检查（事后审计；流式无法拦截，命中 REWRITE 仅记审计不改已推送内容）
                SafetyResult outputResult = safetyFilter.checkOutput(userId, "chat", fullReply.toString());
                if (outputResult.getAction() == SafetyAction.REWRITE) {
                    log.warn("AI 输出越界已记审计（已推送内容不改）: userId={}, sessionId={}",
                            userId, sessionId);
                }

                // 7. 保存 AI 回复（加密）
                emotionService.appendMessage(sessionId, "assistant", fullReply.toString());

                // 8. 发送 done 事件
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"sessionId\":\"" + sessionId + "\"}"));
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
            }
        });

        return emitter;
    }
}
