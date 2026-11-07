package com.zhiyu.ai.client;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.metrics.AiCallMetrics;
import com.zhiyu.ai.resilience.InMemoryCircuitBreakerStore;
import com.zhiyu.ai.routing.ModelRouter;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.modules.admin.service.AdminModerationService;
import com.zhiyu.modules.admin.service.RuntimeSettings;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * moderation JSON 受控重试单测（2026-11-07，p37 转义故障修复）。
 *
 * <p>覆盖 {@link SpringAiLlmClient#chatWithUsageJson} 的重试策略：
 * <ul>
 *   <li>首次非法 → 同 prompt + 重试告知片段重试 1 次，成功则返回修正文本</li>
 *   <li>两次都非法 → 原文原样返回（绝不修补），业务层仍按 PARSE_FAIL 降级</li>
 *   <li>重试两次调用的 tokens 求和透传，最终输出仍走门面统一审计</li>
 *   <li>首次合法不重试；任一侧 usage 缺失则合计记 null（不估算）</li>
 * </ul>
 * 全程不发起真实模型调用（ChatClient 链全 mock）。
 */
class SpringAiLlmClientJsonRetryTest {

    /** 复刻 p37 故障形态：note 字段引用原文时混入未转义的英文双引号，JSON 字符串断裂。 */
    private static final String BROKEN_QUOTE =
            "{\"tendency\":\"HAS_CONCERNS\",\"riskPoints\":[{\"location\":\"content\",\"note\":\"原文出现\"死\"字，需人工核对\"}]}";
    private static final String VALID =
            "{\"tendency\":\"NO_OBVIOUS_ISSUE\",\"riskPoints\":[],\"uncertainty\":\"语境日常\"}";
    private static final Set<String> BASE_TENDENCIES =
            Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN");

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private ChatClient.CallResponseSpec callSpec;
    private SpringAiLlmClient client;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        doReturn(chatClient).when(builder).build();
        spec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        doReturn(spec).when(chatClient).prompt();
        doReturn(spec).when(spec).options(any());
        doReturn(spec).when(spec).system(anyString());
        doReturn(spec).when(spec).user(anyString());
        doReturn(callSpec).when(spec).call();

        RuntimeSettings settings = mock(RuntimeSettings.class);
        when(settings.number("model", "temperature")).thenReturn(0.7);
        when(settings.number("model", "maxTokens")).thenReturn(800.0);
        client = new SpringAiLlmClient(builder, settings, "deepseek-chat",
                "companion", "safety", "recommend", "rag");
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private static ChatResponse response(String text, Integer in, Integer out) {
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(
                in == null && out == null ? null : new DefaultUsage(in, out));
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

    @Test
    @DisplayName("首次非法 → 同 prompt+告知重试 1 次成功：返回修正文本，tokens 求和，两次同 JSON mode 同模型")
    void firstInvalid_retrySucceeds() {
        ChatResponse broken = response(BROKEN_QUOTE, 10, 5);
        ChatResponse valid = response(VALID, 20, 7);
        doReturn(broken).doReturn(valid).when(callSpec).chatResponse();

        LlmChatResult result = client.chatWithUsageJson("PROMPT", "deepseek-reasoner");

        assertEquals(VALID, result.text(), "重试成功应返回第二次的合法文本");
        assertEquals(30, result.inputTokens(), "两次调用 input tokens 应求和透传（不漏账）");
        assertEquals(12, result.outputTokens(), "两次调用 output tokens 应求和透传（不漏账）");

        // 恰好重试 1 次：同 JSON mode（response_format=json_object）、同模型
        ArgumentCaptor<ChatOptions> options = ArgumentCaptor.forClass(ChatOptions.class);
        verify(spec, times(2)).options(options.capture());
        for (ChatOptions o : options.getAllValues()) {
            assertEquals("deepseek-reasoner", o.getModel(), "重试不得切换模型");
            ResponseFormat format = ((OpenAiChatOptions) o).getResponseFormat();
            assertNotNull(format, "重试不得退出 JSON mode");
            assertEquals(ResponseFormat.Type.JSON_OBJECT, format.getType());
        }
        // 第二次 = 同 prompt + 重试告知片段
        ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
        verify(spec, times(2)).user(users.capture());
        assertEquals("PROMPT", users.getAllValues().get(0));
        assertTrue(users.getAllValues().get(1).startsWith("PROMPT"), "重试必须保留原 prompt");
        assertTrue(users.getAllValues().get(1).contains("未通过 JSON 语法解析"),
                "重试必须明确告知上次输出不是合法 JSON");
        assertTrue(users.getAllValues().get(1).contains("「」"), "重试片段应含引号形态约束");
    }

    @Test
    @DisplayName("两次都非法 → 原文原样返回（不修补），业务层仍按 PARSE_FAIL 降级（available=false）")
    void bothInvalid_verbatimReturn_businessStillParseFails() {
        String secondBroken = "{\"tendency\":\"UNCERTAIN\",\"riskPoints\":[{\"note\":\"还是\"坏\"的\"}]}";
        ChatResponse broken = response(BROKEN_QUOTE, 1, 1);
        ChatResponse stillBroken = response(secondBroken, 2, 2);
        doReturn(broken).doReturn(stillBroken).when(callSpec).chatResponse();

        LlmChatResult result = client.chatWithUsageJson("PROMPT", null);

        assertEquals(secondBroken, result.text(), "仍失败必须把原文原样交回，绝不用启发式修补");
        assertEquals(3, result.inputTokens());
        assertEquals(3, result.outputTokens());
        verify(spec, times(2)).call();
        // 降级语义不变：业务层同一解析口径下仍是格式不可用（PARSE_FAIL → 人工兜底）
        assertNull(AdminModerationService.parse(result.text(), BASE_TENDENCIES),
                "业务层解析口径不得被重试改变，仍应判为格式不可用");
    }

    @Test
    @DisplayName("重试不破坏门面审计与记账：最终输出恰好审计 1 次，metrics 记求和后的 tokens")
    void retry_keepsFacadeAuditAndUsageAccounting() {
        ChatResponse broken = response(BROKEN_QUOTE, 10, 5);
        ChatResponse valid = response(VALID, 20, 7);
        doReturn(broken).doReturn(valid).when(callSpec).chatResponse();
        ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
        when(filter.checkOutput(any(), anyString(), anyString())).thenReturn(SafetyResult.pass("ok"));
        AiCallMetrics metrics = mock(AiCallMetrics.class);
        when(metrics.active()).thenReturn(true);
        AiClient facade = new AiClient(client, new InMemoryCircuitBreakerStore(5, 60_000), filter,
                new ModelRouter("deepseek-chat", "deepseek-reasoner", true), metrics);

        String out = facade.moderationSuggest("PROMPT");

        assertEquals(VALID, out);
        // 审计：最终输出经门面安全过滤恰好 1 次（scene=moderation，由 checkOutput 内部落 ai_audit_log）
        verify(filter, times(1)).checkOutput(any(), eq("moderation"), eq(VALID));
        // 记账：llm-moderation 一条指标，tokens 为两次调用求和（10+20 / 5+7）
        verify(metrics, times(1)).record(any(), eq("llm-moderation"), eq("deepseek-reasoner"),
                anyLong(), eq(AiCallMetrics.OK), isNull(), eq(30), eq(12));
    }

    @Test
    @DisplayName("首次合法不重试：只调用 1 次，usage 原样透传")
    void firstValid_noRetry() {
        doReturn(response(VALID, 20, 7)).when(callSpec).chatResponse();

        LlmChatResult result = client.chatWithUsageJson("PROMPT", null);

        assertEquals(VALID, result.text());
        assertEquals(20, result.inputTokens());
        assertEquals(7, result.outputTokens());
        verify(spec, times(1)).call();
    }

    @Test
    @DisplayName("围栏包裹的合法 JSON 不算解析失败（容忍口径与业务层一致），不重试")
    void fencedValid_noRetry() {
        String fenced = "```json\n{\"tendency\":\"UNCERTAIN\",\"riskPoints\":[]}\n```";
        doReturn(response(fenced, 2, 3)).when(callSpec).chatResponse();

        LlmChatResult result = client.chatWithUsageJson("PROMPT", null);

        assertEquals(fenced, result.text());
        verify(spec, times(1)).call();
    }

    @Test
    @DisplayName("任一侧 usage 缺失：合计记 null（拿不到不估算）")
    void usageMissingOnOneSide_totalIsNull() {
        ChatResponse broken = response(BROKEN_QUOTE, 10, 5);
        ChatResponse valid = response(VALID, null, null);
        doReturn(broken).doReturn(valid).when(callSpec).chatResponse();

        LlmChatResult result = client.chatWithUsageJson("PROMPT", null);

        assertEquals(VALID, result.text());
        assertNull(result.inputTokens(), "任一侧缺失不估算，合计记 null");
        assertNull(result.outputTokens());
    }
}
