package com.zhiyu.modules.emotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.emotion.entity.EmotionMessage;
import com.zhiyu.modules.emotion.entity.EmotionSession;
import com.zhiyu.modules.emotion.mapper.EmotionMessageMapper;
import com.zhiyu.modules.emotion.mapper.EmotionSessionMapper;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.modules.emotion.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link EmotionService} 实现（T1.3a）。
 *
 * <p>关键逻辑：
 * <ul>
 *   <li>{@code appendMessage} 时对 content 加密（{@link AesEncryptor#encrypt}）</li>
 *   <li>{@code loadRecentHistory} 时解密（{@link AesEncryptor#decrypt}）</li>
 *   <li>历史按 create_time 升序、截断到 maxTurns 条</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionServiceImpl implements EmotionService {

    private final EmotionSessionMapper sessionMapper;
    private final EmotionMessageMapper messageMapper;
    private final AesEncryptor encryptor;

    @Override
    public Long createSession(Long userId, String mood) {
        EmotionSession session = new EmotionSession();
        session.setUserId(userId);
        session.setMood(mood);
        session.setStatus(0);
        sessionMapper.insert(session);
        log.info("创建情绪会话: sessionId={}, userId={}, mood={}", session.getId(), userId, mood);
        return session.getId();
    }

    @Override
    public void appendMessage(Long sessionId, String role, String content) {
        EmotionMessage msg = new EmotionMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(encryptor.encrypt(content));
        messageMapper.insert(msg);
        log.debug("追加消息: sessionId={}, role={}", sessionId, role);
    }

    @Override
    public List<ChatMessage> loadRecentHistory(Long sessionId, int maxTurns) {
        // 按 create_time 降序取最近 maxTurns 条，再反转为升序
        LambdaQueryWrapper<EmotionMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmotionMessage::getSessionId, sessionId)
               .orderByDesc(EmotionMessage::getCreateTime)
               .orderByDesc(EmotionMessage::getId);

        Page<EmotionMessage> page = new Page<>(1, maxTurns);
        Page<EmotionMessage> result = messageMapper.selectPage(page, wrapper);

        List<EmotionMessage> messages = result.getRecords();
        // 反转为升序
        java.util.Collections.reverse(messages);

        return messages.stream()
                .map(msg -> new ChatMessage(msg.getRole(), encryptor.decrypt(msg.getContent())))
                .collect(Collectors.toList());
    }

    @Override
    public SessionVO getSession(Long sessionId) {
        EmotionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return null;
        }
        return new SessionVO(session.getId(), session.getMood(), session.getCreateTime());
    }
}
