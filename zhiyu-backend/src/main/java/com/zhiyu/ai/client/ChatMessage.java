package com.zhiyu.ai.client;

/**
 * LLM 对话消息的统一抽象（与 Spring AI 解耦）。
 *
 * <p>业务层只感知 {@code role + content}，由 {@link LlmClient} 实现负责
 * 转换成具体框架（Spring AI / Mock）的消息类型。
 *
 * @param role    消息角色：{@code system} / {@code user} / {@code assistant}
 * @param content 消息正文
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
