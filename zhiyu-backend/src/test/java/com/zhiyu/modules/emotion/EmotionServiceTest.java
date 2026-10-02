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
}
