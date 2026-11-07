package com.zhiyu.modules.dialogue.capability;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.dialogue.DialogueCapability;
import com.zhiyu.modules.dialogue.DialogueTurnRequest;
import com.zhiyu.modules.dialogue.DialogueTurnResult;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.security.BusinessAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 倾听子能力（P2）：/api/emotion/chat 现有流程的参考实现（开关开启时经 commander 复用）。
 *
 * <p>流程与 EmotionController 一字对齐：同意门 → 会话归属 → 用户消息加密落库 →
 * 原文窗口（max-history-turns 配置）→（可选）滚动摘要前缀 → AiClient 流式/整段 →
 * 助手消息落库 + 会话小结 →（可选）滚动摘要触发。
 *
 * <p>入口安检已由 commander 完成：本类把 commander 的 {@code precheckedInput} 透传给
 * AiClient 重载，避免双审计/双 care_reminder。
 *
 * <p>隔离约束：不得 import 其他 capability 实现类（见 {@link DialogueCapability}）。
 */
@Component
public class ListeningCapability implements DialogueCapability {

    private final AiClient aiClient;
    private final EmotionService emotionService;
    private final BusinessAccess access;
    private final com.zhiyu.modules.dialogue.DialogueContextService dialogueContext;
    private final int maxHistoryTurns;
    private final boolean rollingSummaryEnabled;

    public ListeningCapability(AiClient aiClient, EmotionService emotionService, BusinessAccess access,
                               com.zhiyu.modules.dialogue.DialogueContextService dialogueContext,
                               @Value("${zhiyu.emotion.max-history-turns:10}") int maxHistoryTurns,
                               @Value("${zhiyu.dialogue.rolling-summary:false}") boolean rollingSummaryEnabled) {
        this.aiClient = aiClient;
        this.emotionService = emotionService;
        this.access = access;
        this.dialogueContext = dialogueContext;
        this.maxHistoryTurns = maxHistoryTurns;
        this.rollingSummaryEnabled = rollingSummaryEnabled;
    }

    @Override
    public String id() {
        return "LISTENING";
    }

    @Override
    public Long ensureSession(DialogueTurnRequest req) {
        return req.sessionId() != null ? req.sessionId()
                : emotionService.createSession(req.userId(), null);
    }

    @Override
    public DialogueTurnResult execute(DialogueTurnRequest req) {
        // commander 的入口安检结果随请求透传（req.precheckedInput()），跳过门面二次安检
        return doExecute(req, req.precheckedInput());
    }

    private DialogueTurnResult doExecute(DialogueTurnRequest req, SafetyResult precheckedInput) {
        long userId = req.userId();
        access.consentedUser();
        Long sessionId = ensureSession(req);
        access.emotionSession(sessionId, userId);

        emotionService.appendMessage(sessionId, "user", req.text());
        List<ChatMessage> history = emotionService.loadRecentHistory(sessionId, maxHistoryTurns);
        if (rollingSummaryEnabled) {
            String contextPrefix = dialogueContext.summaryPrefix(userId, sessionId);
            if (contextPrefix != null) {
                var withContext = new ArrayList<ChatMessage>(history.size() + 1);
                withContext.add(ChatMessage.system(contextPrefix));
                withContext.addAll(history);
                history = withContext;
            }
        }

        DialogueTurnResult result;
        if (req.live()) {
            AiClient.LiveChatResult r = aiClient.streamChatLive(
                    sessionId, req.text(), history, req.chunkSink(), precheckedInput);
            persistAssistant(sessionId, userId, r.reply());
            result = new DialogueTurnResult(sessionId, id(), r.reply(), r.crisis(), r.revoked(), false,
                    r.provider());
        } else {
            AiClient.StreamChatResult r = aiClient.streamChat(
                    sessionId, req.text(), history, ignored -> { }, precheckedInput);
            persistAssistant(sessionId, userId, r.reply());
            result = new DialogueTurnResult(sessionId, id(), r.reply(), r.crisis(), false, false,
                    r.provider());
        }
        return result;
    }

    private void persistAssistant(Long sessionId, long userId, String reply) {
        access.consentedUser();
        access.emotionSession(sessionId, userId);
        emotionService.appendMessage(sessionId, "assistant", reply);
        emotionService.updateSummary(sessionId, truncate(reply));
        if (rollingSummaryEnabled) {
            dialogueContext.maybeRoll(userId, sessionId);
        }
    }

    private static String truncate(String text) {
        // 与 EmotionController.SUMMARY_MAX_LENGTH(100) 对齐
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.substring(0, Math.min(100, text.length()));
    }
}
