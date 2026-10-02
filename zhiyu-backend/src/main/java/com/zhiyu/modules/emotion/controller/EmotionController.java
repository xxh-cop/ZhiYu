package com.zhiyu.modules.emotion.controller;

import com.zhiyu.ai.AiClient;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 情绪倾听 SSE 控制器（T1.3a）。
 *
 * <p>POST /api/emotion/chat —— SSE 流式对话。
 *
 * <p>流程：
 * <ol>
 *   <li>从 UserContext 取 userId</li>
 *   <li>sessionId 为 null → 新建会话</li>
 *   <li>保存用户消息（加密）</li>
 *   <li>加载历史（解密）</li>
 *   <li>独立线程池调 AiClient.streamChat，逐 token SSE 推送</li>
 *   <li>流结束保存 AI 完整回复（加密）</li>
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

                // 2. 保存用户消息（加密）
                emotionService.appendMessage(sessionId, "user", dto.getContent());

                // 3. 加载历史
                List<com.zhiyu.ai.client.ChatMessage> history =
                        emotionService.loadRecentHistory(sessionId, 10);

                // 4. 流式对话
                StringBuilder fullReply = new StringBuilder();
                aiClient.streamChat(history, token -> {
                    try {
                        fullReply.append(token);
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        log.warn("SSE 发送 token 失败: {}", e.getMessage());
                    }
                });

                // 5. 保存 AI 回复（加密）
                emotionService.appendMessage(sessionId, "assistant", fullReply.toString());

                // 6. 发送 done 事件
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
