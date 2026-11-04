package com.zhiyu.modules.dialogue;

/**
 * 规则路由器（P2，纯规则、无模型分类器——决策②）。
 *
 * <p>规则与 ai-config/ai-capabilities.yaml {@code dialogue.routing_rules} 段一一对应：
 * <ul>
 *   <li>R0 危机：输入安检命中 → CRISIS（在 commander 入口处理，不经本路由器）</li>
 *   <li>R1 PRACTICE_PAGE + 活跃练习会话 → PRACTICE_COMPANION</li>
 *   <li>R2 KNOWLEDGE_PAGE → KNOWLEDGE</li>
 *   <li>R3 显式 intentHint：knowledge/ask_heritage → KNOWLEDGE；
 *       有活跃练习会话 → PRACTICE_COMPANION；其余 → LISTENING（P2 仅注册三个能力）</li>
 *   <li>R4 默认 → LISTENING（最安全默认：无工具、无执行副作用）</li>
 * </ul>
 * 纯函数、无状态、可单测；规则引擎自身异常由 commander 捕获并回落 LISTENING（trace ROUTE_FALLBACK）。
 */
public final class DialogueRouter {

    /** 路由结果。ruleId 供 ai_trace 记账（scene=dialogue-route）。 */
    public record Route(String capability, String ruleId, String note) {
    }

    private DialogueRouter() {
    }

    public static Route route(String entryContext, String intentHint, boolean practiceSessionActive) {
        if ("PRACTICE_PAGE".equals(entryContext) && practiceSessionActive) {
            return new Route("PRACTICE_COMPANION", "R1", "练习页+活跃练习会话");
        }
        if ("KNOWLEDGE_PAGE".equals(entryContext)) {
            return new Route("KNOWLEDGE", "R2", "知识页入口");
        }
        if (intentHint != null && !intentHint.isBlank()) {
            if (intentHint.startsWith("knowledge") || "ask_heritage".equals(intentHint)) {
                return new Route("KNOWLEDGE", "R3", "显式意图：非遗知识");
            }
            if (practiceSessionActive) {
                return new Route("PRACTICE_COMPANION", "R3", "显式意图+活跃练习会话");
            }
            return new Route("LISTENING", "R3",
                    "显式意图无对应已注册子能力（P2 仅注册倾听/练习陪伴/知识），回落倾听");
        }
        return new Route("LISTENING", "R4", "默认倾听");
    }
}
