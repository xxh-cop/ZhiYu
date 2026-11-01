package com.zhiyu.modules.emotion;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.modules.emotion.entity.EmotionMessage;
import com.zhiyu.modules.emotion.entity.EmotionSession;
import com.zhiyu.modules.emotion.mapper.EmotionMessageMapper;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.modules.emotion.service.impl.EmotionServiceImpl;
import com.zhiyu.modules.emotion.vo.SessionVO;
import com.zhiyu.common.crypto.AesEncryptor;
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
 * {@link EmotionServiceImpl} 集成单测（T1.3a）。
 *
 * <p>用 H2 内存库 + @SpringBootTest，验证加密落库 + 解密读取。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmotionServiceTest {

    @Autowired
    private EmotionService emotionService;

    @Autowired
    private EmotionMessageMapper messageMapper;

    @Autowired
    private EmotionSessionMapper sessionMapper;

    @Autowired
    private AesEncryptor encryptor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach void seedActiveOwners(){for(long id:new long[]{1001L,2002L})jdbcTemplate.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) VALUES(?,?,'unused','情绪测试',0,1,1,0)",id,"emotionowner"+id);}

    @Test
    @DisplayName("createSession：创建会话返回 ID")
    void testCreateSession() {
        Long sessionId = emotionService.createSession(1001L, "焦虑");
        assertNotNull(sessionId);

        EmotionSession session = sessionMapper.selectById(sessionId);
        assertNotNull(session);
        assertEquals(1001L, session.getUserId());
        assertEquals("焦虑", session.getMood());
        assertEquals(0, session.getStatus());
    }

    @Test
    @DisplayName("appendMessage：content 加密落库，原始列不是明文")
    void testAppendMessage_Encrypted() {
        Long sessionId = emotionService.createSession(1001L, "焦虑");
        String plaintext = "今天心情不太好";
        emotionService.appendMessage(sessionId, "user", plaintext);

        // 用 JdbcTemplate 读原始列，验证不是明文
        String rawContent = jdbcTemplate.queryForObject(
                "SELECT content FROM emotion_message WHERE session_id = ?",
                String.class, sessionId);
        assertNotNull(rawContent);
        assertNotEquals(plaintext, rawContent, "存储的应该是密文");
        // 解密后等于明文
        assertEquals(plaintext, encryptor.decrypt(rawContent));
    }

    @Test
    @DisplayName("loadRecentHistory：解密正确")
    void testLoadRecentHistory_Decrypt() {
        Long sessionId = emotionService.createSession(1001L, null);
        emotionService.appendMessage(sessionId, "user", "你好");
        emotionService.appendMessage(sessionId, "assistant", "我在这里陪着你");

        List<ChatMessage> history = emotionService.loadRecentHistory(sessionId, 10);
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).role());
        assertEquals("你好", history.get(0).content());
        assertEquals("assistant", history.get(1).role());
        assertEquals("我在这里陪着你", history.get(1).content());
    }

    @Test
    @DisplayName("loadRecentHistory：按时间升序、截断到 maxTurns")
    void testLoadRecentHistory_Truncated() {
        Long sessionId = emotionService.createSession(1001L, null);
        for (int i = 0; i < 5; i++) {
            emotionService.appendMessage(sessionId, "user", "msg-" + i);
        }

        // 只取最近 3 条
        List<ChatMessage> history = emotionService.loadRecentHistory(sessionId, 3);
        assertEquals(3, history.size());
        // 应该是 msg-2, msg-3, msg-4（升序）
        assertEquals("msg-2", history.get(0).content());
        assertEquals("msg-3", history.get(1).content());
        assertEquals("msg-4", history.get(2).content());
    }

    @Test
    @DisplayName("会话隔离：用户 A 的历史不包含用户 B 的消息")
    void testSessionIsolation() {
        Long sessionA = emotionService.createSession(1001L, "焦虑");
        Long sessionB = emotionService.createSession(2002L, "疲惫");

        emotionService.appendMessage(sessionA, "user", "用户A的消息");
        emotionService.appendMessage(sessionB, "user", "用户B的消息");

        List<ChatMessage> historyA = emotionService.loadRecentHistory(sessionA, 10);
        List<ChatMessage> historyB = emotionService.loadRecentHistory(sessionB, 10);

        assertEquals(1, historyA.size());
        assertEquals("用户A的消息", historyA.get(0).content());
        assertEquals(1, historyB.size());
        assertEquals("用户B的消息", historyB.get(0).content());
    }

    @Test
    @DisplayName("updateSummary：summary 加密落库，原始列不是明文")
    void testUpdateSummary_Encrypted() {
        Long sessionId = emotionService.createSession(1001L, "焦虑");
        String summary = "本次对话围绕考试压力展开，建议深呼吸放松";
        emotionService.updateSummary(sessionId, summary);

        // 用 JdbcTemplate 读原始列，验证不是明文
        String rawSummary = jdbcTemplate.queryForObject(
                "SELECT summary FROM emotion_session WHERE id = ?",
                String.class, sessionId);
        assertNotNull(rawSummary);
        assertNotEquals(summary, rawSummary, "存储的应该是密文");
        assertEquals(summary, encryptor.decrypt(rawSummary));
    }

    @Test
    @DisplayName("updateSummary：100 字中文密文可落库（回归 Data too long）")
    void testUpdateSummary_LongChineseCipherFitsColumn() {
        Long sessionId = emotionService.createSession(1001L, "紧张");
        String summary = "想".repeat(100);
        emotionService.updateSummary(sessionId, summary);

        String rawSummary = jdbcTemplate.queryForObject(
                "SELECT summary FROM emotion_session WHERE id = ?",
                String.class, sessionId);
        assertEquals(summary, encryptor.decrypt(rawSummary));
    }

    @Test
    @DisplayName("updateSummary：不改变 status（保持 0 进行中）")
    void testUpdateSummary_DoesNotChangeStatus() {
        Long sessionId = emotionService.createSession(1001L, "疲惫");
        emotionService.updateSummary(sessionId, "会话总结内容");

        EmotionSession session = sessionMapper.selectById(sessionId);
        assertEquals(0, session.getStatus(), "status 应仍为 0（进行中）");
        assertNotNull(session.getSummary());
    }

    @Test
    @DisplayName("updateSummary + getSession：写入后 getSession 能解密返回")
    void testUpdateSummary_GetSession_Decrypt() {
        Long sessionId = emotionService.createSession(1001L, "焦虑");
        String summary = "对话总结：建议尝试正念呼吸";
        emotionService.updateSummary(sessionId, summary);

        SessionVO vo = emotionService.getSession(sessionId);
        assertNotNull(vo);
        assertEquals(summary, vo.getSummary());
        assertEquals(0, vo.getStatus(), "status 应仍为 0（进行中）");
    }

    @Test
    @DisplayName("endSession：改变 status=1 + summary 加密")
    void testEndSession_ChangesStatus() {
        Long sessionId = emotionService.createSession(1001L, "低落");
        String summary = "对话结束总结";
        emotionService.endSession(sessionId, summary);

        EmotionSession session = sessionMapper.selectById(sessionId);
        assertEquals(1, session.getStatus(), "status 应为 1（已结束）");
        assertNotNull(session.getSummary());
        assertEquals(summary, encryptor.decrypt(session.getSummary()));
    }
}
