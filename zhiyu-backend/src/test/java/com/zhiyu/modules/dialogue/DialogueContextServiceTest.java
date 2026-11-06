package com.zhiyu.modules.dialogue;

import com.zhiyu.ai.AiClient;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.companion.CompanionTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P1b 对话上下文滚动摘要单测（2026-11-04）。
 *
 * <p>覆盖：触发边界（第 10/11/15/16/20 轮）、开关关闭零副作用、
 * 密文落库与摘要前缀、TTL 到期、AI 失败降级（不阻断、记 FALLBACK trace）。
 * 摘要生成统一走 mock AiClient（真实模型验证见 dev MySQL 冒烟）。
 */
class DialogueContextServiceTest {

    private static final long UID = 881001L;
    private static final long SESSION = 881010L;
    private static final String SUMMARY_TEXT = "第1-5轮摘要：用户因考试周压力感到焦虑，尝试过深呼吸放松。";

    private JdbcTemplate db;
    private AesEncryptor crypto;
    private AiClient aiClient;
    private CompanionTraceService trace;
    private String template;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:dcs" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
                "sa", "");
        try (var c = source.getConnection()) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource("src/test/resources/schema-h2.sql"));
        }
        db = new JdbcTemplate(source);
        crypto = new AesEncryptor("dGhpcy1pcy1hLXRlc3Qtb25seS1rZXktMzJieXRlcyE=", true);
        aiClient = mock(AiClient.class);
        trace = mock(CompanionTraceService.class);
        when(aiClient.modelName()).thenReturn("deepseek-chat");
        when(aiClient.contextSummary(anyString())).thenReturn(SUMMARY_TEXT);
        template = Files.readString(Path.of("src/main/resources/prompts/context-summary.st"), StandardCharsets.UTF_8);

        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                + "VALUES(?,'dcs_user','hash','摘要用户',0,1,1,0)", UID);
        db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted) "
                + "VALUES(?,?,'anxious',0,NOW(),NOW(),0)", SESSION, UID);
    }

    private DialogueContextService service(boolean enabled) {
        return new DialogueContextService(db, aiClient, crypto, trace, template, enabled, 10, 11, 5, 90);
    }

    /** 追加 n 条消息（user/assistant 交替，content 加密落库）。返回最后一条的序号。 */
    private int appendTurns(int from, int count) {
        long base = 890000L;
        int turn = from;
        for (int i = 0; i < count; i++) {
            String role = turn % 2 == 1 ? "user" : "assistant";
            db.update("INSERT INTO emotion_message(id,session_id,role,content,create_time,update_time,deleted) "
                            + "VALUES(?,?,?,?,DATEADD('SECOND', ?, NOW()),NOW(),0)",
                    base + turn, SESSION, role, crypto.encrypt("第" + turn + "条消息内容"), turn);
            turn++;
        }
        return turn - 1;
    }

    @Test
    @DisplayName("触发边界：10/11 轮不滚动，15 轮首滚（覆盖 1-5），16 轮不滚，20 轮二滚（覆盖 1-10）")
    void rollBoundaries() {
        var service = service(true);

        appendTurns(1, 10);
        service.maybeRoll(UID, SESSION);
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dialogue_context_summary", Integer.class),
                "第 10 轮（窗口内）不得生成摘要");
        verify(aiClient, never()).contextSummary(anyString());

        appendTurns(11, 1);
        service.maybeRoll(UID, SESSION);
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dialogue_context_summary", Integer.class),
                "第 11 轮滑出 1 条，不足 increment=5，不滚动");

        appendTurns(12, 4);
        service.maybeRoll(UID, SESSION);
        var row = db.queryForMap("SELECT covered_start_turn, covered_end_turn, summary_cipher, model FROM dialogue_context_summary WHERE session_id=?", SESSION);
        assertEquals(1, ((Number) row.get("covered_start_turn")).intValue());
        assertEquals(5, ((Number) row.get("covered_end_turn")).intValue(), "第 15 轮首滚覆盖 1-5");
        verify(aiClient, times(1)).contextSummary(anyString());

        appendTurns(16, 1);
        service.maybeRoll(UID, SESSION);
        assertEquals(5, db.queryForObject("SELECT covered_end_turn FROM dialogue_context_summary WHERE session_id=?", Integer.class, SESSION),
                "第 16 轮只新滑出 1 条，不再滚动");
        verify(aiClient, times(1)).contextSummary(anyString());

        appendTurns(17, 4);
        service.maybeRoll(UID, SESSION);
        assertEquals(10, db.queryForObject("SELECT covered_end_turn FROM dialogue_context_summary WHERE session_id=?", Integer.class, SESSION),
                "第 20 轮二滚覆盖 1-10（更新而非新增行）");
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM dialogue_context_summary WHERE session_id=?", Integer.class, SESSION),
                "同一会话只保留一行滚动摘要");
        verify(aiClient, times(2)).contextSummary(anyString());
    }

    @Test
    @DisplayName("开关关闭时零副作用：不调用 AI、不写表、前缀为 null（行为与旧版一致）")
    void switchOffNoSideEffects() {
        var service = service(false);
        appendTurns(1, 16);
        service.maybeRoll(UID, SESSION);
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dialogue_context_summary", Integer.class));
        verify(aiClient, never()).contextSummary(anyString());
        assertNull(service.summaryPrefix(UID, SESSION));
        assertFalse(service.enabled());
    }

    @Test
    @DisplayName("密文落库：summary_cipher 不是明文；前缀解密含轮次标注；TTL 到期按无摘要处理")
    void ciphertextAtRestPrefixAndTtl() {
        var service = service(true);
        appendTurns(1, 15);
        service.maybeRoll(UID, SESSION);

        String cipher = db.queryForObject("SELECT summary_cipher FROM dialogue_context_summary WHERE session_id=?", String.class, SESSION);
        assertNotNull(cipher);
        assertFalse(cipher.contains("考试周"), "落库必须是密文，不得出现明文内容");
        assertEquals(SUMMARY_TEXT, crypto.decrypt(cipher));

        String prefix = service.summaryPrefix(UID, SESSION);
        assertNotNull(prefix);
        assertTrue(prefix.contains("第1-5轮"), "前缀应保留可追溯轮次范围");
        assertTrue(prefix.contains("不代表用户原话"));
        assertTrue(prefix.contains("考试周"), "前缀应含解密后的摘要内容");

        // TTL：把 update_time 拨回 91 天前 → 按过期处理
        db.update("UPDATE dialogue_context_summary SET update_time=? WHERE session_id=?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(91)), SESSION);
        assertNull(service.summaryPrefix(UID, SESSION), "超过 90 天 TTL 的摘要按无摘要处理");
    }

    @Test
    @DisplayName("AI 返回 null 时降级：不写摘要行、记 FALLBACK trace、不抛异常")
    void degradeWhenAiReturnsNull() {
        when(aiClient.contextSummary(anyString())).thenReturn(null);
        var service = service(true);
        appendTurns(1, 15);
        assertDoesNotThrow(() -> service.maybeRoll(UID, SESSION));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM dialogue_context_summary", Integer.class),
                "降级时不落摘要行，保留原文窗口");
        verify(trace).record(eq(UID), isNull(), eq("dialogue-summary"), eq("deepseek-chat"),
                anyString(), isNull(), anyInt(), eq("FALLBACK"), eq("SUMMARY_FAILED"), isNull(), isNull());
    }

    @Test
    @DisplayName("滚动成功后记 OK trace；提示词不含诊断措辞要求（模板约束原文存在）")
    void okTraceAndPromptConstraints() {
        var service = service(true);
        appendTurns(1, 15);
        service.maybeRoll(UID, SESSION);
        verify(trace).record(eq(UID), isNull(), eq("dialogue-summary"), eq("deepseek-chat"),
                anyString(), isNull(), anyInt(), eq("OK"), isNull(), isNull(), isNull());
        assertTrue(template.contains("严禁任何诊断性、结论性措辞"));
        assertTrue(template.contains("不得虚构"));
    }
}
