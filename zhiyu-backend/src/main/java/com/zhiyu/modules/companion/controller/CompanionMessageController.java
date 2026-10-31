package com.zhiyu.modules.companion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.companion.CompanionEvent;
import com.zhiyu.modules.companion.CompanionOrchestrator;
import com.zhiyu.modules.companion.CompanionTurn;
import com.zhiyu.modules.companion.dto.CompanionMessageDTO;
import com.zhiyu.modules.companion.tool.ToolContext;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小织受控陪伴消息（typed SSE）。
 *
 * <p>与 /api/emotion/chat 并存，不改旧对话。所有工具只读，断流后可用同一请求安全重试。
 * 事件名固定在 {@link CompanionEvent}；运行期错误以 event:error 发送，HTTP 保持 200。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CompanionMessageController {

    private static final long SSE_TIMEOUT = 120_000L;

    private final BusinessAccess access;
    private final PracticeSessionService practiceService;
    private final CompanionOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    @Qualifier("sseExecutor")
    private final ThreadPoolTaskExecutor sseExecutor;

    @PostMapping(value = "/api/companion/sessions/{id}/message",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@PathVariable Long id,
                              @Valid @RequestBody CompanionMessageDTO dto) {
        Long userId = access.consentedUser();
        // 同步校验归属，越权/不存在直接走统一 JSON 错误响应，不建立 SSE 连接。
        practiceService.get(userId, id);
        var identity = UserContext.get();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseExecutor.execute(() -> {
            var previousIdentity = UserContext.get();
            UserContext.set(identity);
            try {
                access.consentedUser();
                var session = practiceService.get(userId, id);
                CompanionTurn turn = orchestrator.respond(
                        new ToolContext(userId, id), session, dto.getContent());
                for (CompanionEvent event : turn.events()) {
                    emitter.send(SseEmitter.event()
                            .name(event.event())
                            .data(objectMapper.writeValueAsString(event.data())));
                }
                emitter.complete();
                log.info("陪伴消息完成: practiceSessionId={}, events={}, generated={}, toolUsed={}",
                        id, turn.events().size(), turn.generated(), turn.toolUsed());
            } catch (BusinessException e) {
                sendError(emitter, e.getCode(), e.getMessage());
            } catch (Exception e) {
                log.error("陪伴消息 SSE 异常: practiceSessionId={}", id, e);
                sendError(emitter, 5000, "小织暂时没有连上，可以继续手动练习。");
            } finally {
                if (previousIdentity == null) UserContext.clear();
                else UserContext.set(previousIdentity);
            }
        });

        return emitter;
    }

    private void sendError(SseEmitter emitter, Integer code, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code == null ? 5000 : code);
        data.put("message", message == null || message.isBlank() ? "服务暂时不可用" : message);
        try {
            emitter.send(SseEmitter.event()
                    .name(CompanionEvent.ERROR)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (IOException ignored) {
        } finally {
            emitter.complete();
        }
    }
}
