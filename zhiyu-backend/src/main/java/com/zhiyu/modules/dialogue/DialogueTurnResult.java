package com.zhiyu.modules.dialogue;

/**
 * 一轮对话的执行结果（P2 调度层）。
 *
 * @param sessionId  实际会话 id（倾听链可能本轮新建）
 * @param capability 实际执行/接管的能力（CRISIS/被闸口锁定后的最终值）
 * @param reply      最终安全文本（crisis/锁定时为确定性文案）
 * @param crisis     done 事件的危机标志（入口安检或输出侧命中高危）
 * @param revoked    是否需要前端撤回已流出内容并整体替换
 * @param toolUsed   本轮是否调用过只读工具（陪伴能力）
 * @param provider   实际产出回复的 provider（2026-11-08 装配层批：deepseek/ollama/mock；
 *                   非模型产出——危机话术/闸口锁定/确定性文案——为 null，不冒充归属；
 *                   与 ai_trace 同源同词表）
 */
public record DialogueTurnResult(Long sessionId, String capability, String reply,
                                 boolean crisis, boolean revoked, boolean toolUsed,
                                 String provider) {
    /** 旧 6 参构造（既有调用点兼容）：provider=null（未知/非模型产出）。 */
    public DialogueTurnResult(Long sessionId, String capability, String reply,
                              boolean crisis, boolean revoked, boolean toolUsed) {
        this(sessionId, capability, reply, crisis, revoked, toolUsed, null);
    }
}
