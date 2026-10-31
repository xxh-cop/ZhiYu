package com.zhiyu.ai.client;

/** 一次非流式 LLM 调用的文本与 token 用量；供应商未返回时为 null。 */
public record LlmChatResult(String text, Integer inputTokens, Integer outputTokens) {
}
