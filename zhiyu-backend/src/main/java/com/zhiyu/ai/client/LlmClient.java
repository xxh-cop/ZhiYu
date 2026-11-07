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

    /**
     * 生成创作回顾（一次性、非流式）。
     *
     * <p>调用方负责拼装完整的、已脱敏的创作回顾 prompt；本方法只负责生成文本。
     *
     * @param prompt 完整 prompt 文本
     * @return LLM 生成的回顾文本
     */
    String reflectionSummary(String prompt);

    /**
     * 生成独立倾听小结（一次性、非流式）。
     *
     * <p>调用方负责拼装只含本人用户发言的 prompt；本方法只负责生成文本。
     *
     * @param prompt 完整 prompt 文本
     * @return LLM 生成的小结文本
     */
    String listeningSummary(String prompt);

    /**
     * 一次性对话（非流式），用于 RAG 问答等场景（M3-3）。
     *
     * <p>调用方负责拼装完整的 prompt（含上下文 + 用户问题），
     * 本方法只负责发给 LLM 并返回完整回答。
     *
     * @param prompt 完整 prompt 文本
     * @return LLM 生成的回答文本
     */
    String chat(String prompt);

    /** 默认实现只返回文本；支持 usage 的实现应覆盖并返回 token 用量。 */
    default LlmChatResult chatWithUsage(String prompt) {
        return new LlmChatResult(chat(prompt), null, null);
    }

    /**
     * 带模型覆盖的一次性对话（B2 任务分档，2026-11-04）。
     *
     * <p>默认实现忽略 {@code model} 参数回退到默认模型——
     * 不支持多模型的实现（如 Mock）无需改动；调用方据此不得假定模型已切换，
     * 需要确认时应检查返回或实现类的能力说明。
     *
     * @param prompt 完整 prompt 文本
     * @param model  目标模型名（如 deepseek-reasoner）
     */
    default LlmChatResult chatWithUsage(String prompt, String model) {
        return chatWithUsage(prompt);
    }

    /**
     * JSON 结构化输出（2026-11-05 健壮性批）：{@code response_format=json_object}，
     * 由 provider 尽量保证返回合法 JSON（指令级约束，非受限解码）。
     * 仅 moderation 链路使用；不支持该能力的实现（如 Mock）默认回退普通对话。
     *
     * <p>受控重试契约（2026-11-07）：真实实现（{@code SpringAiLlmClient}）在首次输出
     * 未通过 JSON 语法解析时，以同 prompt + 重试告知片段重试 1 次；仍不合法则原样返回，
     * 由业务层按 PARSE_FAIL 降级。重试两次调用的 tokens 求和后透传（任一侧缺失记 null）。
     * 默认实现不重试（Mock 已被门面排除在 moderation 链路外）。
     *
     * @param model 目标模型名；{@code null} 表示默认模型
     */
    default LlmChatResult chatWithUsageJson(String prompt, String model) {
        return model == null ? chatWithUsage(prompt) : chatWithUsage(prompt, model);
    }

    /** 推荐理由（带 usage 变体；默认包装 String 版，usage 为 null——拿不到不估算）。 */
    default LlmChatResult recommendReasonWithUsage(String mood, List<String> craftNames) {
        return new LlmChatResult(recommendReason(mood, craftNames), null, null);
    }

    /** 作品寄语（带 usage 变体；同上）。 */
    default LlmChatResult workMessageWithUsage(String workTitle, String mood) {
        return new LlmChatResult(workMessage(workTitle, mood), null, null);
    }

    /** 创作回顾（带 usage 变体；同上）。 */
    default LlmChatResult reflectionSummaryWithUsage(String prompt) {
        return new LlmChatResult(reflectionSummary(prompt), null, null);
    }

    /** 当前模型名；未覆盖时返回 unknown，不得把 Mock 冒充真实模型。 */
    default String modelName() {
        return "unknown";
    }
}
