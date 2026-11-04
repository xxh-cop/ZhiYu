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
 *   <li>{@code endSession} 时对 summary 加密后落库</li>
 *   <li>{@code getSession} 时解密 summary 返回明文</li>
 *   <li>历史按 create_time 升序、截断到 maxTurns 条</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionServiceImpl implements EmotionService {

    private static final String DECRYPT_FAILED = "[解密失败]";

    private final EmotionSessionMapper sessionMapper;
    private final EmotionMessageMapper messageMapper;
    private final AesEncryptor encryptor;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private void lockOwner(Long uid){var users=db.queryForList("SELECT id FROM user WHERE id=? AND deleted=0 AND status=1 AND privacy_agreed=1 FOR UPDATE",uid);if(users.isEmpty())throw new com.zhiyu.common.exception.BusinessException(1003,"账号或隐私授权已失效");}
    private void lockSession(Long sid){var rows=db.queryForList("SELECT user_id FROM emotion_session WHERE id=? AND deleted=0",Long.class,sid);if(rows.isEmpty())throw new com.zhiyu.common.exception.BusinessException(1004,"会话不存在");lockOwner(rows.get(0));if(db.queryForList("SELECT id FROM emotion_session WHERE id=? AND deleted=0 AND status=0 FOR UPDATE",sid).isEmpty())throw new com.zhiyu.common.exception.BusinessException(3001,"会话已结束，请从历史记录恢复后继续");}

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Long createSession(Long userId, String mood) {
        lockOwner(userId);
        EmotionSession session = new EmotionSession();
        session.setUserId(userId);
        session.setMood(mood);
        session.setStatus(0);
        sessionMapper.insert(session);
        log.info("创建情绪会话: sessionId={}, userId={}, mood={}", session.getId(), userId, mood);
        return session.getId();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void appendMessage(Long sessionId, String role, String content) {
        lockSession(sessionId);
        // 幂等（SSE A 方案）：断线重发同一 content 时，若末条同角色消息明文相同则跳过。
        // 仅对用户消息去重——assistant 回复内容可能恰好重复（如降级固定话术），不应去重。
        if ("user".equals(role)) {
            EmotionMessage last = messageMapper.selectOne(new LambdaQueryWrapper<EmotionMessage>()
                    .eq(EmotionMessage::getSessionId, sessionId)
                    .eq(EmotionMessage::getRole, "user")
                    .orderByDesc(EmotionMessage::getId)
                    .last("LIMIT 1"));
            if (last != null && content.equals(safeDecrypt(last.getContent()))) {
                log.info("幂等跳过重复用户消息: sessionId={}", sessionId);
                return;
            }
        }
        EmotionMessage msg = new EmotionMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(encryptor.encrypt(content));
        messageMapper.insert(msg);
        log.debug("追加消息: sessionId={}, role={}", sessionId, role);
    }

    private String safeDecrypt(String encrypted) {
        try {
            return encryptor.decrypt(encrypted);
        } catch (Exception e) {
            return null;
        }
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
    @org.springframework.transaction.annotation.Transactional
    public void endSession(Long sessionId, String summary) {
        lockSession(sessionId);
        EmotionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("结束会话失败：会话不存在, sessionId={}", sessionId);
            return;
        }
        session.setStatus(1);
        if (summary != null && !summary.isBlank()) {
            session.setSummary(encryptor.encrypt(summary));
        }
        sessionMapper.updateById(session);
        log.info("结束情绪会话: sessionId={}, hasSummary={}", sessionId, summary != null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void updateSummary(Long sessionId, String summary) {
        lockSession(sessionId);
        if (summary == null || summary.isBlank()) {
            return;
        }
        EmotionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("更新会话总结失败：会话不存在, sessionId={}", sessionId);
            return;
        }
        session.setSummary(encryptor.encrypt(summary));
        sessionMapper.updateById(session);
        log.debug("更新会话总结: sessionId={}, summaryLength={}", sessionId, summary.length());
    }

    @Override
    public SessionVO getSession(Long sessionId) {
        EmotionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return null;
        }
        return new SessionVO(
                session.getId(),
                session.getMood(),
                session.getCreateTime(),
                decryptSafely(session.getSummary()),
                session.getStatus()
        );
    }

    /**
     * 解密 summary，失败时返回 {@value #DECRYPT_FAILED} 而非抛异常。
     */
    private String decryptSafely(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }
        try {
            return encryptor.decrypt(ciphertext);
        } catch (Exception e) {
            log.warn("会话总结解密失败: {}", e.getMessage());
            return DECRYPT_FAILED;
        }
    }
}
