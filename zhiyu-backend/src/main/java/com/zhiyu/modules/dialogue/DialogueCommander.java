package com.zhiyu.modules.dialogue;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyAction;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.companion.CompanionTraceService;
import com.zhiyu.modules.dialogue.capability.CompanionCapability;
import com.zhiyu.modules.dialogue.guard.RouteGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话调度层（P2，docs/dialogue-unification-20261104.md；开关 zhiyu.dialogue.commander 默认关闭）。
 *
 * <p><b>只做四件事</b>：入口安检 → 规则路由 → 调能力并转写 chunk → 闸口执行与 trace 记账。
 * <b>不生成文本、不持久化消息、不持有跨请求业务状态</b>（闸口状态在 RouteGuard 的
 * Redis/内存存储中，不在本类字段）。
 *
 * <p>危机红线（AA2）：路由之前直接 {@link ContentSafetyFilter#checkInput}；命中即返回
 * 预置关怀话术，LLM 与编排完全不启动（care_reminder 由过滤器落库），crisis=true。
 *
 * <p>深度上限=1（AA4.1）：结构上每轮恰好「1 次路由 + 1 次能力执行」——本类没有
 * 任何在能力返回后再次路由的代码路径；能力接口也不暴露转交入口（见
 * {@link DialogueCapability} 的隔离约束）。
 */
@Slf4j
@Service
public class DialogueCommander {

    /** 重复意图锁定的温和提示（确定性文案，不进 LLM）。 */
    public static final String REPEAT_LOCK_REPLY =
            "我注意到我们一直在聊同一件事。要不先停一下，我们先把眼下这件事做完？如果你愿意，也可以换个说法讲给我听。";
    /** 会话预算耗尽的收尾文案（明确但不吓人）。 */
    public static final String BUDGET_EXHAUSTED_REPLY =
            "我们今天已经聊了挺多了，先到这儿休息一下。想继续的时候，开一个新对话我就在。";

    private static final String SCENE_ROUTE = "dialogue-route";

    private final ContentSafetyFilter safetyFilter;
    private final RouteGuard guard;
    private final CompanionTraceService trace;
    private final AiClient aiClient;
    private final CompanionCapability companionCapability;
    private final Map<String, DialogueCapability> capabilities;
    private final int sessionTurnBudget;
    private final long turnWallClockMs;

    public DialogueCommander(ContentSafetyFilter safetyFilter, RouteGuard guard,
                             CompanionTraceService trace, AiClient aiClient,
                             CompanionCapability companionCapability,
                             List<DialogueCapability> capabilityList,
                             @Value("${zhiyu.dialogue.session-turn-budget:30}") int sessionTurnBudget,
                             @Value("${zhiyu.dialogue.turn-wall-clock-ms:30000}") long turnWallClockMs) {
        this.safetyFilter = safetyFilter;
        this.guard = guard;
        this.trace = trace;
        this.aiClient = aiClient;
        this.companionCapability = companionCapability;
        Map<String, DialogueCapability> map = new LinkedHashMap<>();
        for (DialogueCapability c : capabilityList) {
            map.put(c.id(), c);
        }
        this.capabilities = Map.copyOf(map);
        this.sessionTurnBudget = sessionTurnBudget;
        this.turnWallClockMs = turnWallClockMs;
    }

    /** 路由结果（供测试与 trace）。 */
    public record Decision(String capability, String ruleId, String note) {
    }

    /** 纯路由（不含闸口）：供评测与排障。 */
    public Decision route(Long userId, DialogueTurnRequest req) {
        boolean practiceActive = companionCapability.sessionActive(req.userId(), req.practiceSessionId());
        DialogueRouter.Route r = DialogueRouter.route(req.entryContext(), req.intentHint(), practiceActive);
        return new Decision(r.capability(), r.ruleId(), r.note());
    }

    /**
     * 执行一轮对话。调用方（控制器）负责 SSE 事件转写与异常兜底；本方法抛出的异常
     * 视为本轮失败，由控制器发 error 事件（与旧路径一致）。
     */
    public DialogueTurnResult chat(DialogueTurnRequest req) {
        long turnStart = System.currentTimeMillis();

        // 1) 入口安检（AA2 危机红线：路由之前；BLOCK 时 LLM 与编排完全不启动）
        SafetyResult input = safetyFilter.checkInput(req.userId(), req.sessionId(), "chat", req.text());
        if (input.getAction() == SafetyAction.BLOCK) {
            trace(req.userId(), "ROUTE_CRISIS", "CRISIS", 0);
            String care = input.getCareMessage();
            if (care != null && !care.isBlank()) {
                req.chunkSink().accept(care);
            }
            log.warn("调度层危机干预: userId={}, 不调 LLM", req.userId());
            return new DialogueTurnResult(req.sessionId(), "CRISIS", care, true, true, false);
        }

        // 2) 规则路由（模型分类器不进上线路径）；规则引擎异常回落 LISTENING
        Decision decision;
        try {
            decision = route(req.userId(), req);
        } catch (Exception e) {
            log.warn("路由异常，回落倾听: userId={}, err={}", req.userId(), e.getMessage());
            decision = new Decision("LISTENING", "R4", "规则引擎异常回落");
            trace(req.userId(), "ROUTE_FALLBACK", "LISTENING", (int) (System.currentTimeMillis() - turnStart));
        }
        trace(req.userId(), null, decision.capability(), (int) (System.currentTimeMillis() - turnStart), decision.ruleId());

        // 3) 闸口（全部显式降级 + trace，不静默）
        DialogueCapability capability = capabilities.get(decision.capability());
        Long sessionKey = capability.ensureSession(req);
        String guardKey = sessionKey != null ? String.valueOf(sessionKey) : "u:" + req.userId();

        // 3a) 振荡锁定（可能改变最终能力）
        String finalCapability = guard.applyOscillationLock(guardKey, decision.capability());
        if (!finalCapability.equals(decision.capability())) {
            trace(req.userId(), "REPEAT_INTENT_LOCK", finalCapability, 0, "oscillation:" + decision.capability());
            capability = capabilities.get(finalCapability);
            sessionKey = capability.ensureSession(req);
            guardKey = sessionKey != null ? String.valueOf(sessionKey) : guardKey;
        }

        // 3b) 会话预算
        if (guard.currentTurns(guardKey) >= sessionTurnBudget) {
            trace(req.userId(), "BUDGET_EXHAUSTED", finalCapability, 0);
            req.chunkSink().accept(BUDGET_EXHAUSTED_REPLY);
            return new DialogueTurnResult(sessionKey, finalCapability, BUDGET_EXHAUSTED_REPLY, false, false, false);
        }

        // 3c) 重复意图锁定
        int intentCount = guard.registerIntent(req.userId(), guardKey, RouteGuard.normalizeHash(req.text()));
        if (guard.isRepeatLocked(intentCount)) {
            trace(req.userId(), "REPEAT_INTENT_LOCK", finalCapability, 0);
            req.chunkSink().accept(REPEAT_LOCK_REPLY);
            return new DialogueTurnResult(sessionKey, finalCapability, REPEAT_LOCK_REPLY, false, false, false);
        }

        // 4) 调能力（每轮恰好一次执行；入口安检结果随请求透传，能力内不再二次安检）
        DialogueTurnRequest withPrecheck = new DialogueTurnRequest(
                req.userId(), sessionKey, req.practiceSessionId(), req.text(), req.entryContext(),
                req.intentHint(), req.live(), req.chunkSink(), input);
        DialogueTurnResult result = capability.execute(withPrecheck);

        // 5) 墙钟与预算记账
        long elapsed = System.currentTimeMillis() - turnStart;
        if (elapsed > turnWallClockMs) {
            trace(req.userId(), "TURN_TIMEOUT", finalCapability, (int) elapsed);
            log.warn("单轮超墙钟预算: userId={}, elapsed={}ms > {}ms", req.userId(), elapsed, turnWallClockMs);
        }
        guard.incrementTurns(guardKey);
        return new DialogueTurnResult(result.sessionId() != null ? result.sessionId() : sessionKey,
                finalCapability, result.reply(), result.crisis(), result.revoked(), result.toolUsed(),
                result.provider());
    }

    private void trace(Long userId, String fallbackReason, String capability, int latencyMs) {
        trace(userId, fallbackReason, capability, latencyMs, null);
    }

    /** ai_trace 记账：scene=dialogue-route；capability 写 tool_name 列、ruleId 写 fallback_reason（U2 已注记）。 */
    private void trace(Long userId, String fallbackReason, String capability, int latencyMs, String ruleId) {
        String reason = ruleId != null ? "rule=" + ruleId
                : fallbackReason == null ? null : fallbackReason;
        try {
            trace.record(userId, null, SCENE_ROUTE, aiClient.modelName(), null,
                    capability, latencyMs, fallbackReason == null ? "OK" : "FALLBACK", reason, null, null);
        } catch (Exception e) {
            log.warn("调度 trace 记账失败（已消化）: {}", e.getMessage());
        }
    }
}
