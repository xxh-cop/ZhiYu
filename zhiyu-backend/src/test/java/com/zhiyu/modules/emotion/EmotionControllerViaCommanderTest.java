package com.zhiyu.modules.emotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.companion.CompanionTraceService;
import com.zhiyu.modules.dialogue.DialogueCapability;
import com.zhiyu.modules.dialogue.DialogueCommander;
import com.zhiyu.modules.dialogue.DialogueContextService;
import com.zhiyu.modules.dialogue.capability.CompanionCapability;
import com.zhiyu.modules.dialogue.capability.ListeningCapability;
import com.zhiyu.modules.dialogue.guard.RouteGuard;
import com.zhiyu.modules.emotion.controller.EmotionController;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 委托路径协议平价测试（2026-11-05 阶段 2）：{@code zhiyu.dialogue.listening-via-commander=true} 时，
 * /api/emotion/chat 走调度层，但 SSE 事件序列必须与旧路径一致（chunk/revoke/done/error、
 * done 载荷恒为 {sessionId, crisis, revoked}，不新增 capability 字段）。
 *
 * <p>standalone MockMvc + 同步 executor + asyncDispatch（成熟模式见 CompanionMessageControllerTest）。
 * 指挥官用<b>真实实例</b>（真实 RouteGuard 内存存储 + mock 安全过滤/AiClient），
 * 倾听能力用真实 {@link ListeningCapability}（mock EmotionService/AiClient）——
 * 验证的是「控制器 ⇄ commander ⇄ 能力」的事件转写，不是模型行为。
 *
 * <p>对照组：{@link EmotionControllerTest}（test profile 显式关开关）覆盖旧路径，
 * 含「关闭态零 dialogue-route 记账」断言。
 */
class EmotionControllerViaCommanderTest {

    private static final long UID = 7L;
    private static final long SESSION = 555L;

    private BusinessAccess access;
    private AiClient aiClient;
    private EmotionService emotionService;
    private ContentSafetyFilter safetyFilter;
    private CompanionTraceService trace;
    private CompanionCapability companionCapability;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        access = mock(BusinessAccess.class);
        aiClient = mock(AiClient.class);
        emotionService = mock(EmotionService.class);
        DialogueContextService dialogueContext = mock(DialogueContextService.class);
        safetyFilter = mock(ContentSafetyFilter.class);
        trace = mock(CompanionTraceService.class);
        companionCapability = mock(CompanionCapability.class);

        lenient().when(access.consentedUser()).thenReturn(UID);
        lenient().when(aiClient.modelName()).thenReturn("mock-llm");
        lenient().when(companionCapability.id()).thenReturn("PRACTICE_COMPANION");
        lenient().when(companionCapability.sessionActive(anyLong(), any())).thenReturn(false);
        lenient().when(companionCapability.ensureSession(any())).thenReturn(null);
        lenient().when(emotionService.loadRecentHistory(anyLong(), anyInt())).thenReturn(List.of());

        RouteGuard guard = new RouteGuard(null, "memory", 3, 600, true);
        DialogueCapability listening = new ListeningCapability(
                aiClient, emotionService, access, dialogueContext, 10, false);
        DialogueCommander commander = new DialogueCommander(safetyFilter, guard, trace, aiClient,
                companionCapability, List.of(listening, companionCapability), 30, 30_000);

        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        EmotionController controller = new EmotionController(access, aiClient, emotionService,
                new ObjectMapper(), dialogueContext, commander, executor);
        ReflectionTestUtils.setField(controller, "maxHistoryTurns", 10);
        ReflectionTestUtils.setField(controller, "rollingSummaryEnabled", false);
        ReflectionTestUtils.setField(controller, "listeningViaCommander", true);

        // standalone MockMvc 默认 StringHttpMessageConverter 写出编码会把中文 chunk 写成 '?'
        // （测试设施编码，非生产行为）；显式 UTF-8 字符串转换器 + Jackson（请求体反序列化）
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                                new ObjectMapper()))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
        UserContext.set(new LoginUser(UID, 0));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private MvcResult postChat(String body) throws Exception {
        return mockMvc.perform(post("/api/emotion/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    @DisplayName("委托路径 live：chunk 序列 + done{sessionId,crisis,revoked}，协议零破坏（无 capability 字段）")
    void liveDelegatedChunkSequenceMatchesLegacyShape() throws Exception {
        when(safetyFilter.checkInput(anyLong(), anyLong(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.pass("输入检查通过"));
        when(aiClient.streamChatLive(anyLong(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    var sink = (java.util.function.Consumer<String>) inv.getArgument(3);
                    sink.accept("我在这里陪着你。");
                    sink.accept("慢慢来，不用急。");
                    return new AiClient.LiveChatResult(SafetyResult.pass("ok"), SafetyResult.pass("ok"),
                            "我在这里陪着你。慢慢来，不用急。", false, false, false);
                });

        mockMvc.perform(asyncDispatch(postChat(
                        "{\"sessionId\":555,\"content\":\"最近有点焦虑\",\"mode\":\"live\"}")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:chunk")))
                .andExpect(content().string(containsString("我在这里陪着你。")))
                .andExpect(content().string(containsString("慢慢来，不用急。")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"sessionId\":555")))
                .andExpect(content().string(containsString("\"crisis\":false")))
                .andExpect(content().string(containsString("\"revoked\":false")))
                // 协议零破坏：旧端点 done 载荷不得新增 capability 等字段
                .andExpect(content().string(not(containsString("capability"))));

        // 委托证据：入口安检结果透传（5 参重载），能力内不再二次安检；消息落库照常被能力完成
        verify(aiClient).streamChatLive(eq(SESSION), anyString(), any(), any(), any(SafetyResult.class));
        verify(emotionService).appendMessage(SESSION, "user", "最近有点焦虑");
        verify(emotionService).appendMessage(eq(SESSION), eq("assistant"), anyString());
        verify(trace, atLeastOnce()).record(eq(UID), isNull(), eq("dialogue-route"),
                anyString(), isNull(), eq("LISTENING"), anyInt(), eq("OK"), eq("rule=R4"),
                isNull(), isNull());
    }

    @Test
    @DisplayName("委托路径 legacy（无 mode）：整段回复走无名 data 事件 + done，无 chunk")
    void legacyDelegatedSingleDataEvent() throws Exception {
        when(safetyFilter.checkInput(anyLong(), anyLong(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.pass("输入检查通过"));
        when(aiClient.streamChat(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(new AiClient.StreamChatResult(SafetyResult.pass("ok"),
                        SafetyResult.pass("ok"), "整段安全回复"));

        mockMvc.perform(asyncDispatch(postChat(
                        "{\"sessionId\":555,\"content\":\"今天有点累\"}")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:整段安全回复")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"crisis\":false")))
                .andExpect(content().string(not(containsString("event:chunk"))));

        verify(aiClient).streamChat(eq(SESSION), anyString(), any(), any(), any(SafetyResult.class));
    }

    @Test
    @DisplayName("委托路径危机：关怀话术走 chunk + done(crisis=true,revoked=true)，零 LLM、不落 emotion_message")
    void crisisDelegatedCareChunkNoLlmNoPersist() throws Exception {
        when(safetyFilter.checkInput(anyLong(), anyLong(), eq("chat"), anyString()))
                .thenReturn(SafetyResult.block("关键词命中", "请记得你并不孤单，可拨打热线 12356"));

        mockMvc.perform(asyncDispatch(postChat(
                        "{\"sessionId\":555,\"content\":\"我不想活了\",\"mode\":\"live\"}")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:chunk")))
                .andExpect(content().string(containsString("12356")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"crisis\":true")))
                .andExpect(content().string(containsString("\"revoked\":true")));

        // 危机红线：LLM 与能力编排完全不启动（两种重载都不得被调用）
        verify(aiClient, never()).streamChatLive(any(), any(), any(), any());
        verify(aiClient, never()).streamChatLive(any(), any(), any(), any(), any());
        verify(aiClient, never()).streamChat(any(), any(), any(), any());
        verify(aiClient, never()).streamChat(any(), any(), any(), any(), any());
        // 委托路径交付差异（已声明）：危机轮用户消息与关怀话术不落 emotion_message；
        // care_reminder 与 ai_audit_log 由过滤器照常写库（不在本测试断言范围）
        verify(emotionService, never()).appendMessage(anyLong(), anyString(), anyString());
    }
}
