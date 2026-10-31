package com.zhiyu.modules.companion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.companion.memory.CompanionMemoryService;
import com.zhiyu.modules.companion.tool.CompanionToolRegistry;
import com.zhiyu.modules.companion.tool.ToolContext;
import com.zhiyu.modules.companion.tool.ToolResult;
import com.zhiyu.modules.practice.enums.PracticeSessionStatus;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanionOrchestratorTest {

    @Mock
    AiClient aiClient;

    @Mock
    CompanionToolRegistry registry;

    @Mock
    CompanionTraceService traceService;

    @Mock
    CompanionMemoryService memoryService;

    private CompanionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CompanionOrchestrator(aiClient, registry, traceService, memoryService,
                new ObjectMapper(), new ClassPathResource("prompts/companion-plan.st"));
    }

    @Test
    @DisplayName("合法 JSON 计划：执行允许工具并输出类型化事件")
    void validPlanExecutesAllowedTool() {
        when(aiClient.companionPlan(eq(91L), anyString(), eq("想找点事做")))
                .thenReturn(new AiClient.CompanionResult(
                        "```json\n" + plan("RECOMMEND", "recommendPractice", "{\"minutes\":20}") + "\n```",
                        true, false, null));
        when(registry.execute(any(ToolContext.class), eq("recommendPractice"), anyMap()))
                .thenReturn(ToolResult.success(Map.of("items", List.of())));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(2, 20), "想找点事做");

        assertTrue(turn.generated());
        assertFalse(turn.blocked());
        assertTrue(turn.toolUsed());
        assertEquals("recommendPractice", turn.toolName());
        assertEquals(CompanionStage.RECOMMEND, turn.stage());
        assertEquals(List.of("stage", "message", "tool", "done"), eventNames(turn));
        assertEquals(true, ((Map<?, ?>) turn.events().get(1).data()).get("generated"));
        verify(registry, times(1)).execute(any(ToolContext.class), eq("recommendPractice"), anyMap());
        verify(traceService).record(eq(7L), eq(91L), eq("companion"), nullable(String.class),
                eq("companion-plan-v1"), eq("recommendPractice"), anyInt(), eq("OK"), isNull(),
                isNull(), isNull());
    }

    @Test
    @DisplayName("已授权记忆写入编排提示词")
    void memoryPreferencesAreInjectedIntoPrompt() {
        when(memoryService.promptSummary(7L))
                .thenReturn("用户授权保存的练习偏好：常用练习时长 15 分钟。仅用于练习推荐，不作情绪或疗效判断。");
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult(
                        plan("LISTEN", null, "{}"), true, false, null));

        orchestrator.respond(new ToolContext(7L, 91L), session(1, 15), "有点累");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiClient).companionPlan(eq(91L), prompt.capture(), anyString());
        assertTrue(prompt.getValue().contains("常用练习时长 15 分钟"));
    }

    @Test
    @DisplayName("状态不允许的工具：拒绝执行并走确定性引导")
    void disallowedToolFallsBackWithoutExecuting() {
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult(
                        plan("RECOMMEND", "recommendPractice", "{\"minutes\":20}"), true, false, null));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(1, 20), "有点紧张");

        assertFalse(turn.generated());
        assertEquals(CompanionStage.LISTEN, turn.stage());
        assertFalse(turn.toolUsed());
        assertEquals(List.of("stage", "message", "done"), eventNames(turn));
        verify(registry, never()).execute(any(), anyString(), anyMap());
    }

    @Test
    @DisplayName("非法 JSON：走确定性引导，不执行工具")
    void invalidJsonFallsBack() {
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult("这不是 JSON", true, false, null));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(1, 20), "有点紧张");

        assertFalse(turn.generated());
        assertEquals(CompanionStage.LISTEN, turn.stage());
        assertFalse(turn.toolUsed());
        verify(registry, never()).execute(any(), anyString(), anyMap());
        verify(traceService).record(eq(7L), eq(91L), eq("companion"), nullable(String.class),
                eq("companion-plan-v1"), isNull(), anyInt(), eq("FALLBACK"), eq("INVALID_PLAN"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("输出被安全改写：保留阶段但丢弃工具")
    void outputRewriteUsesSafeReplyWithoutTool() {
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult(
                        plan("RECOMMEND", "recommendPractice", "{\"minutes\":20}"),
                        true, false, "我在，先慢慢说。"));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(2, 20), "我是不是焦虑症");

        assertTrue(turn.generated());
        assertFalse(turn.toolUsed());
        assertEquals("我在，先慢慢说。", turn.reply());
        assertEquals(CompanionStage.RECOMMEND, turn.stage());
        verify(registry, never()).execute(any(), anyString(), anyMap());
        verify(traceService).record(eq(7L), eq(91L), eq("companion"), nullable(String.class),
                eq("companion-plan-v1"), isNull(), anyInt(), eq("FALLBACK"), eq("OUTPUT_REWRITE"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("输出整体被阻断：返回安全话术并记录 OUTPUT_BLOCK")
    void outputBlockReturnsSafeReply() {
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult(null, false, true, "安全提示"));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(1, 20), "危险输出");

        assertTrue(turn.blocked());
        assertEquals("安全提示", turn.reply());
        verify(registry, never()).execute(any(), anyString(), anyMap());
        verify(traceService).record(eq(7L), eq(91L), eq("companion"), nullable(String.class),
                eq("companion-plan-v1"), isNull(), anyInt(), eq("BLOCKED"), eq("OUTPUT_BLOCK"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("工具执行失败：降级为无工具引导")
    void toolFailureFallsBackWithoutRetry() {
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult(
                        plan("RECOMMEND", "recommendPractice", "{\"minutes\":20}"), true, false, null));
        when(registry.execute(any(ToolContext.class), eq("recommendPractice"), anyMap()))
                .thenReturn(ToolResult.failure("5000", "工具暂时不可用"));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(2, 20), "想找点事做");

        assertFalse(turn.generated());
        assertFalse(turn.toolUsed());
        assertEquals(CompanionStage.RECOMMEND, turn.stage());
        assertEquals(List.of("stage", "message", "done"), eventNames(turn));
        verify(registry, times(1)).execute(any(ToolContext.class), eq("recommendPractice"), anyMap());
        verify(traceService).record(eq(7L), eq(91L), eq("companion"), nullable(String.class),
                eq("companion-plan-v1"), eq("recommendPractice"), anyInt(), eq("FALLBACK"), eq("TOOL_FAILED"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("输入高危：只返回支持话术，不执行工具")
    void blockedInputReturnsSupportOnly() {
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult("请联系学校心理中心", false, true, null));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(1, 20), "我不想活了");

        assertTrue(turn.blocked());
        assertFalse(turn.generated());
        assertFalse(turn.toolUsed());
        assertEquals(CompanionStage.DONE, turn.stage());
        assertEquals("请联系学校心理中心", turn.reply());
        assertEquals(List.of("stage", "message", "done"), eventNames(turn));
        verify(registry, never()).execute(any(), anyString(), anyMap());
        verify(traceService).record(eq(7L), eq(91L), eq("companion"), nullable(String.class),
                eq("companion-plan-v1"), isNull(), anyInt(), eq("BLOCKED"), eq("INPUT_BLOCK"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("检索工具：只输出已核验来源的 citation 事件")
    void searchHeritageEmitsCitationEvents() {
        GraphHit pattern = new GraphHit("pattern-1", "PATTERN", "竹编纹样", "纹样摘要",
                null, null, null, List.of("src-1"), "竹编 → 纹样", "2026.1");
        GraphHit source = new GraphHit("src-1", "SOURCE", "瑞昌竹编来源", "来源摘要",
                "https://example.com/source", "瑞昌市文化馆", "仅学习引用",
                List.of("src-1"), "竹编 → 来源", "2026.1");
        when(aiClient.companionPlan(eq(91L), anyString(), anyString()))
                .thenReturn(new AiClient.CompanionResult(
                        plan("LISTEN", "searchHeritage", "{\"question\":\"瑞昌竹编\"}"), true, false, null));
        when(registry.execute(any(ToolContext.class), eq("searchHeritage"), anyMap()))
                .thenReturn(ToolResult.success(Map.of(
                        "retrievalMode", "STRUCTURED_GRAPH",
                        "hits", List.of(pattern, source),
                        "note", "只展示已审核目录")));

        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(1, 20), "想了解竹编");

        assertTrue(turn.generated());
        assertTrue(turn.toolUsed());
        assertEquals(List.of("stage", "message", "tool", "citation", "done"), eventNames(turn));
        RagSourceVO citation = (RagSourceVO) turn.events().get(3).data();
        assertEquals("src-1", citation.getId());
        assertEquals("VERIFIED_SOURCE", citation.getCitationType());
    }

    @Test
    @DisplayName("终态会话：不调用模型与工具")
    void terminalSessionSkipsModelAndTools() {
        CompanionTurn turn = orchestrator.respond(new ToolContext(7L, 91L), session(5, 20), "完成了");

        assertFalse(turn.generated());
        assertFalse(turn.toolUsed());
        assertEquals(CompanionStage.DONE, turn.stage());
        assertEquals(List.of("stage", "message", "done"), eventNames(turn));
        verifyNoInteractions(aiClient, registry, traceService);
    }

    @Test
    @DisplayName("空输入：参数错误，不调用模型")
    void blankInputRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orchestrator.respond(new ToolContext(7L, 91L), session(1, 20), " "));

        assertEquals(1001, ex.getCode().intValue());
        verifyNoInteractions(aiClient, registry, traceService);
    }

    private PracticeSessionVO session(int statusCode, int minutes) {
        PracticeSessionVO session = new PracticeSessionVO();
        session.setPracticeSessionId(91L);
        session.setKind("HERITAGE");
        session.setStatus(statusCode);
        PracticeSessionStatus status = PracticeSessionStatus.fromCode(statusCode);
        session.setStatusLabel(status == null ? "未知" : status.getLabel());
        session.setMinutesBudget(minutes);
        session.setCurrentStep(1);
        return session;
    }

    private String plan(String stage, String tool, String toolInputJson) {
        return "{\"reply\":\"我在，先看看这个练习。\",\"stage\":\"" + stage + "\",\"tool\":"
                + (tool == null ? "null" : "\"" + tool + "\"")
                + ",\"toolInput\":" + toolInputJson + "}";
    }

    private List<String> eventNames(CompanionTurn turn) {
        return turn.events().stream().map(CompanionEvent::event).toList();
    }
}
