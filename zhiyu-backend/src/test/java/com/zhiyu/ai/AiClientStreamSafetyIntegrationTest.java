package com.zhiyu.ai;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiClient#streamChat} 的端到端安全/审计验证（H2 + MockLlmClient，不连真实 API）。
 *
 * <p>覆盖：正常流式写两条带 userId 的审计；高危输入不调 LLM、不推送 token、
 * 只写 BLOCK 审计 + care_reminder；危机标志可用。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiClientStreamSafetyIntegrationTest {

    private static final long USER_ID = 986001L;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bindUser() {
        UserContext.set(new LoginUser(USER_ID, 0));
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    @DisplayName("正常流式：输入 + 输出各一条审计，且都带 userId")
    void normalStreamWritesAuditWithUserId() {
        StringBuilder tokens = new StringBuilder();
        AiClient.StreamChatResult result = aiClient.streamChat(
                986010L, "最近考试压力大",
                List.of(ChatMessage.user("最近考试压力大")), tokens::append);

        assertFalse(result.blocked());
        assertFalse(result.crisis());
        assertFalse(tokens.isEmpty(), "应收到流式 token");
        assertFalse(result.reply().isBlank());

        var rows = jdbc.queryForList(
                "SELECT action, scene, user_id FROM ai_audit_log WHERE user_id = ? ORDER BY id",
                USER_ID);
        assertEquals(2, rows.size(), "每次 AI 调用恰好输入/输出两条审计");
        assertEquals("PASS", rows.get(0).get("action"));
        assertEquals("PASS", rows.get(1).get("action"));
        assertEquals("chat", rows.get(0).get("scene"));
        assertEquals(USER_ID, ((Number) rows.get(0).get("user_id")).longValue());
    }

    @Test
    @DisplayName("高危输入：不调 LLM、不推 token，写 BLOCK 审计 + care_reminder，危机标志为 true")
    void blockedStreamSkipsLlm() {
        StringBuilder tokens = new StringBuilder();
        AiClient.StreamChatResult result = aiClient.streamChat(
                986011L, "我不想活了，我想自杀",
                List.of(ChatMessage.user("我不想活了，我想自杀")), tokens::append);

        assertTrue(result.blocked());
        assertTrue(result.crisis(), "危机标志应保留，供 SSE done 事件使用");
        assertTrue(tokens.isEmpty(), "BLOCK 时不得推送任何 token");
        assertNotNull(result.reply(), "应返回关怀话术");

        Integer blockAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE user_id = ? AND action = 'BLOCK'",
                Integer.class, USER_ID);
        assertEquals(1, blockAudits);

        Integer outputAudits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE user_id = ? AND ai_output IS NOT NULL",
                Integer.class, USER_ID);
        assertEquals(0, outputAudits, "未调用 LLM，不应有输出审计");

        Integer reminders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM care_reminder WHERE user_id = ? AND risk_level = 2",
                Integer.class, USER_ID);
        assertEquals(1, reminders);
    }
}
