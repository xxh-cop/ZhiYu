package com.zhiyu.modules.companion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.companion.memory.CompanionMemoryService;
import com.zhiyu.modules.companion.tool.CompanionToolRegistry;
import com.zhiyu.modules.companion.tool.ToolContext;
import com.zhiyu.modules.companion.tool.ToolResult;
import com.zhiyu.modules.practice.enums.PracticeSessionStatus;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 受控陪伴编排器：模型只提出 JSON 计划，本类负责状态白名单校验、
 * 工具执行与确定性降级。模型输出不能直接改会话状态，也不能越过工具白名单。
 */
@Slf4j
@Service
public class CompanionOrchestrator {

    private static final int MAX_USER_TEXT = 500;
    private static final int MAX_REPLY = 300;
    private static final int MAX_CITATIONS = 4;
    private static final String SAFE_SUPPORT =
            "我在这里陪着你。如果你正经历难以承受的时刻，请联系学校心理中心或心理援助热线。本系统不能替代专业心理咨询。";

    private final AiClient aiClient;
    private final CompanionToolRegistry registry;
    private final CompanionTraceService traceService;
    private final CompanionMemoryService memoryService;
    private final ObjectMapper json;
    private final String promptTemplate;

    public CompanionOrchestrator(AiClient aiClient,
                                 CompanionToolRegistry registry,
                                 CompanionTraceService traceService,
                                 CompanionMemoryService memoryService,
                                 ObjectMapper json,
                                 @Value("classpath:prompts/companion-plan.st") Resource promptResource) {
        this.aiClient = aiClient;
        this.registry = registry;
        this.traceService = traceService;
        this.memoryService = memoryService;
        this.json = json;
        try {
            this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("陪伴计划模板读取失败", e);
        }
    }

    public CompanionTurn respond(ToolContext context, PracticeSessionVO session, String userText) {
        if (context == null || context.userId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (session == null || session.getPracticeSessionId() == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        String text = userText == null ? "" : userText.trim();
        if (text.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请先说说此刻的想法");
        }
        if (text.length() > MAX_USER_TEXT) text = text.substring(0, MAX_USER_TEXT);

        PracticeSessionStatus status = PracticeSessionStatus.fromCode(session.getStatus());
        if (status == null) {
            return simpleTurn(session, CompanionStage.DONE,
                    "这次练习的状态暂时无法识别，可以重新进入练习页。", false);
        }
        if (CompanionPolicy.isTerminal(status)) {
            return simpleTurn(session, CompanionStage.DONE,
                    "这次练习已经收尾了。想继续聊，也可以回到小织的对话页。", false);
        }

        long started = System.nanoTime();
        AiClient.CompanionResult result = aiClient.companionPlan(
                session.getPracticeSessionId(), renderPrompt(context, session, status, text), text);
        if (result.blocked()) {
            String care = result.rewrittenReply() != null
                    ? result.rewrittenReply()
                    : (result.text() == null || result.text().isBlank() ? SAFE_SUPPORT : result.text());
            boolean outputBlocked = result.rewrittenReply() != null;
            log.warn("陪伴计划被安全拦截，跳过工具: practiceSessionId={}, outputBlocked={}",
                    session.getPracticeSessionId(), outputBlocked);
            trace(context, session, started, null, "BLOCKED",
                    outputBlocked ? "OUTPUT_BLOCK" : "INPUT_BLOCK",
                    result.inputTokens(), result.outputTokens());
            return simpleTurn(session, CompanionStage.DONE, care, true);
        }

        Plan plan = result.generated() ? parsePlan(result.text()) : null;
        if (result.rewrittenReply() != null) {
            CompanionStage stage = plan == null ? CompanionStage.DONE : plan.stage();
            trace(context, session, started, null, "FALLBACK", "OUTPUT_REWRITE",
                    result.inputTokens(), result.outputTokens());
            return buildTurn(session, stage, result.rewrittenReply(), true, null, null);
        }
        if (plan == null || !valid(plan, status)) {
            log.info("陪伴计划缺失或越界，使用确定性引导: practiceSessionId={}, status={}",
                    session.getPracticeSessionId(), status);
            CompanionTurn fallbackTurn = fallback(context, session, status, true);
            trace(context, session, started, fallbackTurn.toolName(), "FALLBACK",
                    result.generated() ? "INVALID_PLAN" : "MODEL_FALLBACK",
                    result.inputTokens(), result.outputTokens());
            return fallbackTurn;
        }

        ToolResult toolResult = null;
        if (plan.tool() != null) {
            toolResult = registry.execute(context, plan.tool(), plan.toolInput());
            if (toolResult == null || !toolResult.success()) {
                log.warn("陪伴工具执行失败，改用无工具引导: practiceSessionId={}, tool={}, code={}",
                        session.getPracticeSessionId(), plan.tool(),
                        toolResult == null ? "null" : toolResult.code());
                CompanionTurn fallbackTurn = fallback(context, session, status, false);
                trace(context, session, started, plan.tool(), "FALLBACK", "TOOL_FAILED",
                        result.inputTokens(), result.outputTokens());
                return fallbackTurn;
            }
        }
        log.info("陪伴计划执行: practiceSessionId={}, stage={}, tool={}, generated=true",
                session.getPracticeSessionId(), plan.stage(), plan.tool());
        trace(context, session, started, plan.tool(), "OK", null,
                result.inputTokens(), result.outputTokens());
        return buildTurn(session, plan.stage(), plan.reply(), true, plan.tool(), toolResult);
    }

    private void trace(ToolContext context, PracticeSessionVO session, long startedNanos,
                       String toolName, String status, String reason,
                       Integer inputTokens, Integer outputTokens) {
        int latencyMs = (int) Math.min(Integer.MAX_VALUE,
                (System.nanoTime() - startedNanos) / 1_000_000L);
        traceService.record(context.userId(), session.getPracticeSessionId(), "companion",
                aiClient.modelName(), "companion-plan-v1", toolName, latencyMs, status, reason,
                inputTokens, outputTokens);
    }

    private String renderPrompt(ToolContext context, PracticeSessionVO session,
                                PracticeSessionStatus status, String userText) {
        String allowed = CompanionPolicy.allowedTools(status).stream().sorted()
                .map(name -> name + "：" + registry.description(name))
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
        return promptTemplate
                .replace("{{practiceSessionId}}", String.valueOf(session.getPracticeSessionId()))
                .replace("{{kind}}", session.getKind() == null ? "未指定" : session.getKind())
                .replace("{{statusLabel}}", session.getStatusLabel() == null ? status.getLabel() : session.getStatusLabel())
                .replace("{{minutesBudget}}", String.valueOf(budget(session)))
                .replace("{{currentStep}}", String.valueOf(
                        session.getCurrentStep() == null ? 1 : session.getCurrentStep()))
                .replace("{{memory}}", Objects.toString(
                        memoryService.promptSummary(context.userId()), "无"))
                .replace("{{allowedTools}}", allowed)
                .replace("{{userText}}", userText);
    }

    private Plan parsePlan(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            JsonNode root = json.readTree(text.substring(start, end + 1));
            if (root == null || !root.isObject()) return null;
            String reply = root.path("reply").asText("").trim();
            CompanionStage stage = CompanionStage.from(root.path("stage").asText(""));
            if (reply.isBlank() || stage == null) return null;
            if (reply.length() > MAX_REPLY) reply = reply.substring(0, MAX_REPLY);

            String tool = root.path("tool").asText("").trim();
            if (tool.isBlank() || "null".equalsIgnoreCase(tool)) tool = null;
            if (tool != null && !CompanionPolicy.isToolStageConsistent(tool, stage)) return null;

            Map<String, Object> toolInput = new LinkedHashMap<>();
            JsonNode inputNode = root.get("toolInput");
            if (inputNode != null && inputNode.isObject()) {
                toolInput = json.convertValue(inputNode, new TypeReference<Map<String, Object>>() {
                });
            } else if (inputNode != null && !inputNode.isNull()) {
                return null;
            }
            return new Plan(reply, stage, tool, toolInput);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean valid(Plan plan, PracticeSessionStatus status) {
        return CompanionPolicy.allowsStage(status, plan.stage())
                && (plan.tool() == null || CompanionPolicy.allowsTool(status, plan.tool()));
    }

    private CompanionTurn fallback(ToolContext context, PracticeSessionVO session,
                                   PracticeSessionStatus status, boolean allowTool) {
        CompanionStage stage;
        String reply;
        String tool = null;
        Map<String, Object> input = Map.of();
        switch (status) {
            case CREATED, LISTENING -> {
                stage = CompanionStage.LISTEN;
                reply = "我在，慢慢说。也可以先跟着呼吸停一停，不用急着做什么。";
            }
            case RECOMMENDED -> {
                stage = CompanionStage.RECOMMEND;
                reply = "我先按你的时间预算找两个入门练习，你看看哪个更想试。";
                if (allowTool) {
                    tool = "recommendPractice";
                    input = Map.<String, Object>of("minutes", budget(session));
                }
            }
            case PRACTICING -> {
                stage = CompanionStage.PRACTICE;
                reply = "不用急，跟着自己的节奏。需要的话，我可以陪你做四轮呼吸。";
            }
            case PAUSED -> {
                stage = CompanionStage.BREATHE;
                reply = "先停一停也没关系。要不要一起做四轮呼吸？";
                if (allowTool) tool = "suggestBreathing";
            }
            default -> {
                stage = CompanionStage.DONE;
                reply = "我在。可以回到练习页，或去支持页看看。";
            }
        }
        ToolResult toolResult = null;
        if (tool != null) {
            toolResult = registry.execute(context, tool, input);
            if (toolResult == null || !toolResult.success()) {
                tool = null;
                toolResult = null;
            }
        }
        return buildTurn(session, stage, reply, false, tool, toolResult);
    }

    private CompanionTurn simpleTurn(PracticeSessionVO session, CompanionStage stage,
                                     String reply, boolean blocked) {
        List<CompanionEvent> events = List.of(
                CompanionEvent.stage(stage, session),
                CompanionEvent.message(reply, false),
                CompanionEvent.done(stage, session, false, blocked, false));
        return new CompanionTurn(events, reply, stage, false, blocked, false, null);
    }

    private CompanionTurn buildTurn(PracticeSessionVO session, CompanionStage stage, String reply,
                                    boolean generated, String toolName, ToolResult toolResult) {
        List<CompanionEvent> events = new ArrayList<>();
        events.add(CompanionEvent.stage(stage, session));
        events.add(CompanionEvent.message(reply, generated));
        boolean used = toolName != null && toolResult != null && toolResult.success();
        if (used) {
            events.add(CompanionEvent.tool(toolName, toolResult.data()));
            if ("searchHeritage".equals(toolName)) {
                events.addAll(citations(toolResult.data()));
            }
        }
        events.add(CompanionEvent.done(stage, session, generated, false, used));
        return new CompanionTurn(events, reply, stage, generated, false, used, used ? toolName : null);
    }

    private List<CompanionEvent> citations(Object data) {
        if (!(data instanceof Map<?, ?> map)) return List.of();
        if (!(map.get("hits") instanceof List<?> hits)) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<CompanionEvent> events = new ArrayList<>();
        for (Object item : hits) {
            if (!(item instanceof GraphHit hit) || !"SOURCE".equals(hit.nodeType())) continue;
            if (!seen.add(hit.nodeId())) continue;
            events.add(CompanionEvent.citation(new RagSourceVO(hit.nodeId(), hit.title(), hit.excerpt(),
                    hit.url(), hit.organization(), hit.mediaReuse(), hit.catalogVersion(),
                    hit.relationPath(), "VERIFIED_SOURCE")));
            if (events.size() >= MAX_CITATIONS) break;
        }
        return events;
    }

    private int budget(PracticeSessionVO session) {
        Integer minutes = session.getMinutesBudget();
        if (minutes == null || minutes < 1 || minutes > 180) return 15;
        return minutes;
    }

    private record Plan(String reply, CompanionStage stage, String tool, Map<String, Object> toolInput) {
    }
}
