package com.zhiyu.modules.dialogue;

import com.zhiyu.ai.safety.SafetyResult;

import java.util.function.Consumer;

/**
 * 一轮对话的统一入参（P2 调度层，2026-11-04）。
 *
 * @param userId             当前用户（控制器已鉴权 + 同意门）
 * @param sessionId          倾听会话 id（可空，由倾听能力在首次创建）
 * @param practiceSessionId  练习会话 id（练习陪伴/知识能力用）
 * @param text               本轮用户输入（已经过 commander 入口安检）
 * @param entryContext       入口上下文：LISTENING_PAGE / PRACTICE_PAGE / KNOWLEDGE_PAGE
 * @param intentHint         显式意图提示（前端快捷动作），可空
 * @param live               true=句级流式协议（chunk/revoke/done）；false=整段单事件（兼容期旧协议）
 * @param chunkSink          句级/整段文本下发回调（文本已过安全链）；guard 拦截的确定性文案也经此下发
 * @param precheckedInput    commander 入口安检结果（能力内部据此跳过门面二次安检，避免双审计/双关怀）
 */
public record DialogueTurnRequest(Long userId, Long sessionId, Long practiceSessionId,
                                  String text, String entryContext, String intentHint,
                                  boolean live, Consumer<String> chunkSink,
                                  SafetyResult precheckedInput) {
}
