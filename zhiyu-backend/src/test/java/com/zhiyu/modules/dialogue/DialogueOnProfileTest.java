package com.zhiyu.modules.dialogue;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.client.MockLlmClient;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开启态守门测试（2026-11-05）：在「生产默认」剖面（test + dialogue-on-test，
 * commander/listening-via-commander/rolling-summary 三开关全开）下跑真实 HTTP + H2。
 *
 * <p>与关闭态测试（{@code application-test.yml} 钉住四开关 false，覆盖既有全量）并存、互不改写；
 * 与 EmotionControllerViaCommanderTest（standalone MockMvc + mock 依赖）的差异在于：
 * 本类走真实安全链、真实 commander、真实 RouteGuard(memory)、真实 AiClient(MockLlmClient)、
 * 真实 ai_trace 落库——<b>每条用例都断言「走的是哪条路」</b>（trace 行 / 路由 rule / capability），
 * 不接受「仅返回 200」。
 *
 * <p>不用 @Transactional：RANDOM_PORT 的服务线程看不到测试线程未提交的数据；
 * 每用例自建数据并按 ID 清理（ai_trace 只追加，属审计表，随 H2 上下文销毁）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dialogue-on-test"})
class DialogueOnProfileTest {

    private static final Pattern SESSION_ID = Pattern.compile("\\\"sessionId\\\":\\\"?(\\d+)\\\"?");
    private static final long U1 = 951001L;
    private static final long U2 = 951002L;
    private static final long U3 = 951003L;
    private static final long U4 = 951004L;

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate db;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AesEncryptor crypto;
    @Autowired
    private LlmClient llmClient;

    private record Sse(int status, String body) {
    }

    private String token(long userId) {
        com.zhiyu.support.AuthenticatedTestUser.ensure(db, userId, 0);
        return jwtUtil.createToken(userId, 0);
    }

    private Sse postSse(String path, String json, String jwt) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + jwt)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<java.io.InputStream> resp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build()
                .send(req, HttpResponse.BodyHandlers.ofInputStream());
        String body = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
        return new Sse(resp.statusCode(), body);
    }

    private long traceMark() {
        Long mark = db.queryForObject("SELECT COALESCE(MAX(id),0) FROM ai_trace", Long.class);
        return mark == null ? 0 : mark;
    }

    private java.util.Map<String, Object> latestTrace(long mark, String scene) {
        var rows = db.queryForList("""
                SELECT tool_name, result_status, fallback_reason, model, prompt_version
                FROM ai_trace WHERE id>? AND scene=? ORDER BY id DESC LIMIT 1
                """, mark, scene);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long extractSessionId(String body) {
        Matcher m = SESSION_ID.matcher(body);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    private void cleanupSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        db.update("DELETE FROM emotion_message WHERE session_id=?", sessionId);
        db.update("DELETE FROM emotion_session WHERE id=?", sessionId);
    }

    @Test
    @DisplayName("开启态：/api/emotion/chat 走委托路径——chunk→done 形状平价 + dialogue-route 记账 rule=R4 且无明文")
    void delegatedEmotionChatWritesRouteTrace() throws Exception {
        String jwt = token(U1);
        long mark = traceMark();

        Sse sse = postSse("/api/emotion/chat",
                "{\"content\":\"今天有点焦虑\",\"mode\":\"live\"}", jwt);

        assertEquals(200, sse.status());
        assertTrue(sse.body().contains("event:chunk"), "live 委托路径必须句级分块: " + sse.body());
        assertTrue(sse.body().contains("event:done"), "必须有 done 收尾: " + sse.body());
        assertTrue(sse.body().contains("\"crisis\":false"), sse.body());
        assertTrue(sse.body().contains("\"revoked\":false"), sse.body());
        // 区分路径的关键断言①：旧端点协议零破坏——done 不得出现 capability 字段
        assertFalse(sse.body().contains("\"capability\""),
                "旧端点 done 载荷不得新增字段（协议平价）: " + sse.body());
        // 区分路径的关键断言②：委托路径必然落 dialogue-route 记账；旧实现一行都没有
        var trace = latestTrace(mark, "dialogue-route");
        assertNotNull(trace, "开启态委托路径必须落 scene=dialogue-route 记账");
        assertEquals("LISTENING", trace.get("tool_name"));
        assertEquals("OK", trace.get("result_status"));
        assertEquals("rule=R4", trace.get("fallback_reason"));
        // 记账无明文：trace 各文本列不得出现用户输入
        String joined = String.valueOf(trace.get("tool_name")) + trace.get("model")
                + trace.get("prompt_version") + trace.get("fallback_reason");
        assertFalse(joined.contains("今天有点焦虑"), "ai_trace 不得写入对话明文");
        cleanupSession(extractSessionId(sse.body()));
    }

    @Test
    @DisplayName("开启态：/api/dialogue/chat 注册可用——done 带 capability=LISTENING 且路由 rule=R4")
    void dialogueEndpointRegisteredAndRoutes() throws Exception {
        String jwt = token(U2);
        long mark = traceMark();

        Sse sse = postSse("/api/dialogue/chat",
                "{\"text\":\"心里有点乱\",\"entryContext\":\"LISTENING_PAGE\"}", jwt);

        assertEquals(200, sse.status());
        assertTrue(sse.body().contains("event:chunk"), sse.body());
        assertTrue(sse.body().contains("\"capability\":\"LISTENING\""),
                "统一入口 done 必须带 capability（区分路由结果）: " + sse.body());
        var trace = latestTrace(mark, "dialogue-route");
        assertNotNull(trace);
        assertEquals("rule=R4", trace.get("fallback_reason"));
        cleanupSession(extractSessionId(sse.body()));
    }

    @Test
    @DisplayName("开启态：危机输入零 LLM 短路——done(crisis=true) + trace ROUTE_CRISIS，MockLlmClient 计数为 0")
    void crisisShortCircuitsWithoutLlm() throws Exception {
        String jwt = token(U3);
        assertTrue(llmClient instanceof MockLlmClient, "测试环境应为 MockLlmClient");
        ((MockLlmClient) llmClient).resetStreamChatCallCount();
        long mark = traceMark();

        Sse sse = postSse("/api/emotion/chat",
                "{\"content\":\"我不想活了\",\"mode\":\"live\"}", jwt);

        assertEquals(200, sse.status());
        assertTrue(sse.body().contains("event:chunk"), "关怀话术应以 chunk 下发: " + sse.body());
        assertTrue(sse.body().contains("\"crisis\":true"), sse.body());
        assertTrue(sse.body().contains("\"revoked\":true"), sse.body());
        // 危机红线：LLM 调用计数恒为 0（计数器为 AA 批专门加的观测口）
        assertEquals(0, ((MockLlmClient) llmClient).streamChatCallCount(),
                "危机轮不得有任何 LLM 流式调用");
        var trace = latestTrace(mark, "dialogue-route");
        assertNotNull(trace, "危机轮必须落 ROUTE_CRISIS 记账");
        assertEquals("CRISIS", trace.get("tool_name"));
        assertEquals("ROUTE_CRISIS", trace.get("fallback_reason"));
        assertEquals("FALLBACK", trace.get("result_status"));
        cleanupSession(extractSessionId(sse.body()));
    }

    @Test
    @DisplayName("开启态：滚动摘要超阈值被触发——Mock 下记 dialogue-summary FALLBACK，对话主链路不受影响")
    void rollingSummaryTriggeredOverThreshold() throws Exception {
        String jwt = token(U4);
        long sid = IdWorker.getId();
        db.update("INSERT INTO emotion_session(id,user_id,status,create_time,update_time,deleted) "
                        + "VALUES(?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", sid, U4);
        // 预置 14 条消息（加密），本轮 user+assistant 落库后 total=16 ≥ 触发线 11 且滑出窗口 6 ≥ 增量 5
        for (int i = 1; i <= 14; i++) {
            db.update("INSERT INTO emotion_message(id,session_id,role,content,create_time,update_time,deleted) "
                            + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,0)",
                    IdWorker.getId(), sid, i % 2 == 1 ? "user" : "assistant",
                    crypto.encrypt("预置第" + i + "条"),
                    java.sql.Timestamp.valueOf("2026-11-05 10:00:" + String.format("%02d", i)));
        }
        long mark = traceMark();

        Sse sse = postSse("/api/emotion/chat",
                "{\"sessionId\":" + sid + ",\"content\":\"接着说\",\"mode\":\"live\"}", jwt);

        assertEquals(200, sse.status());
        assertTrue(sse.body().contains("event:done"), "摘要失败不得影响对话主链路: " + sse.body());
        assertTrue(sse.body().contains("\"crisis\":false"), sse.body());
        // 触发证据：Mock 模型下 contextSummary 返回 null → FALLBACK(SUMMARY_FAILED) 记账；
        // 开关关闭时这一行不存在——它就是「滚动摘要确实被触发过」的区分断言
        var summaryTrace = latestTrace(mark, "dialogue-summary");
        assertNotNull(summaryTrace, "超阈值后必须触发滚动摘要（Mock 下降级记账）");
        assertEquals("FALLBACK", summaryTrace.get("result_status"));
        assertEquals("SUMMARY_FAILED", summaryTrace.get("fallback_reason"));
        // Mock 不产出摘要行（降级=保留原文窗口，不落库假摘要）
        Integer summaries = db.queryForObject(
                "SELECT COUNT(*) FROM dialogue_context_summary WHERE session_id=?", Integer.class, sid);
        assertEquals(0, summaries, "Mock 下不得写出摘要行");
        cleanupSession(sid);
    }
}
