package com.zhiyu.modules.dialogue;

/**
 * 对话子能力接口（P2 调度层）。
 *
 * <p><b>物理隔离约束（AA1，编译期纪律）</b>：
 * {@code capability} 包下各实现类<b>不得互相 import</b>；
 * 任何跨能力的转交只能回到 {@code DialogueCommander} 重新路由（且受深度上限=1 约束，
 * 子能力返回值本就不进二次路由）。code review 与结构检查以此为准。
 *
 * <p>能力职责边界：消息持久化、会话归属校验、能力自身的业务调用由实现类负责；
 * 入口安检、路由、闸口、trace 记账由 commander 负责（不在能力内重复安检）。
 */
public interface DialogueCapability {

    /** 能力标识（路由目标）：LISTENING / PRACTICE_COMPANION / KNOWLEDGE。 */
    String id();

    /** 确保本轮有可用的会话上下文（倾听：sessionId 为空则新建；其余能力原样返回）。 */
    Long ensureSession(DialogueTurnRequest req);

    /** 执行一轮对话；chunk 经 req.chunkSink 下发；返回最终安全结果。 */
    DialogueTurnResult execute(DialogueTurnRequest req);
}
