package com.zhiyu.modules.dialogue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.dialogue.DialogueCommander;
import com.zhiyu.modules.dialogue.DialogueTurnRequest;
import com.zhiyu.modules.dialogue.DialogueTurnResult;
import com.zhiyu.modules.dialogue.dto.DialogueChatDTO;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一对话入口（C1，/api/dialogue/chat）。
 *
 * <p><b>开关语义</b>：仅在 {@code zhiyu.dialogue.commander=true} 时注册；
 * 未启用时访问按未注册路径返回既有 1002（匿名）/1004（已认证）语义
 * （契约 x-status 从 planned 转 implemented (gated)，见 openapi.yaml）。
 *
 * <p>事件协议：chunk（句级/整段文本）→ 可选 revoke → done
 * （done 载荷含 sessionId/capability/crisis/revoked/toolUsed）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dialogue")
@ConditionalOnProperty(name = "zhiyu.dialogue.commander", havingValue = "true")
public class DialogueChatController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final DialogueCommander commander;
    private final BusinessAccess access;
    private final ObjectMapper objectMapper;
    @Qualifier("sseExecutor")
    private final ThreadPoolTaskExecutor sseExecutor;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody DialogueChatDTO dto) {
        // 请求线程：身份复核（注销/禁用 → 1002）+ 隐私同意门（未同意 → 1003，不进 SSE）
        Long userId = access.consentedUser();
        LoginUser identity = UserContext.get();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        sseExecutor.execute(() -> {
            var previous = UserContext.get();
            UserContext.set(identity);
            try {
                Long sessionId = dto.sessionId() == null ? null : Long.valueOf(dto.sessionId());
                Long practiceSessionId = dto.practiceSessionId() == null ? null : Long.valueOf(dto.practiceSessionId());
                DialogueTurnResult result = commander.chat(new DialogueTurnRequest(
                        userId, sessionId, practiceSessionId, dto.text(),
                        dto.entryContext() == null ? "LISTENING_PAGE" : dto.entryContext(),
                        dto.intentHint(), true,
                        sentence -> sendChunk(emitter, sentence), null));

                if (result.revoked() && !result.crisis()) {
                    emitter.send(SseEmitter.event().name("revoke").data(objectMapper.writeValueAsString(
                            Map.of("text", result.reply(), "reason", "safety"))));
                }
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("sessionId", result.sessionId());
                done.put("capability", result.capability());
                done.put("crisis", result.crisis());
                done.put("revoked", result.revoked());
                done.put("toolUsed", result.toolUsed());
                // 2026-11-08 装配层批：可选归属字段（与 ai_trace.provider 同源同词表）；
                // 非模型产出（危机话术/安全改写/闸口锁定文案）为 null 不下发
                if (result.provider() != null && !result.provider().isBlank()) {
                    done.put("provider", result.provider());
                }
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 对话异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("{\"code\":5000,\"message\":\"服务暂时不可用\"}"));
                } catch (IOException ignored) {
                }
                emitter.complete();
            } finally {
                if (previous == null) UserContext.clear(); else UserContext.set(previous);
            }
        });
        return emitter;
    }

    private void sendChunk(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().name("chunk")
                    .data(objectMapper.writeValueAsString(Map.of("text", text))));
        } catch (IOException e) {
            throw new IllegalStateException("chunk 发送失败", e);
        }
    }
}
