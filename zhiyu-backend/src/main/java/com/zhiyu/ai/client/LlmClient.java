package com.zhiyu.ai.client;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 客户端抽象（T1.1a）。
 *
 * <p>定义三个能力：
 * <ul>
 *   <li>{@link #streamChat} —— 流式对话，逐 token 回调（SSE 用，T1.3 接）</li>
 *   <li>{@link #recommendReason} —— 一次性生成手作推荐理由</li>
 *   <li>{@link #workMessage} —— 一次性生成作品寄语</li>
 * </ul>
 *
 * <p>T1.1b 会由 {@code AiClient} 门面包裹本接口，叠加熔断 / 安全过滤 / 审计日志。
 * 本批次不写门面，业务可直接调 {@link LlmClient}（仅冒烟用）。
 */
public interface LlmClient {

    /**
     * 流式对话：把历史消息发给 LLM，逐块回调 {@code onToken}。
     *
     * @param history 对话历史（含 system / user / assistant）
     * @param onToken 每收到一个文本片段时回调
     */
    void streamChat(List<ChatMessage> history, Consumer<String> onToken);

    /**
     * 生成手作推荐理由（一次性调用，非流式）。
     *
     * @param mood       用户当前情绪关键词（如「焦虑」「疲惫」）
     * @param craftNames 候选手作名称列表（如「扎染」「赣绣」）
     * @return LLM 生成的推荐理由文本
     */
    String recommendReason(String mood, List<String> craftNames);

    /**
     * 生成作品寄语（一次性调用，非流式）。
     *
     * @param workTitle 作品标题
     * @param mood      创作时的心情
     * @return LLM 生成的寄语文本
     */
    String workMessage(String workTitle, String mood);
}
