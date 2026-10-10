package com.zhiyu.modules.emotion.service;

import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.modules.emotion.vo.SessionVO;

import java.util.List;

/**
 * 情绪会话服务接口（T1.3a）。
 *
 * <p>负责会话创建、消息持久化（加密）、历史加载（解密）。
 * SSE 流式调用由 Controller 编排，本接口只管数据层。
 */
public interface EmotionService {

    /**
     * 创建新会话。
     *
     * @param userId 学生 user.id
     * @param mood   触发情绪（可为 null）
     * @return 新建会话 ID
     */
    Long createSession(Long userId, String mood);

    /**
     * 追加一条消息（content 会被加密后落库）。
     *
     * @param sessionId 会话 ID
     * @param role      消息角色：{@code user} / {@code assistant}
     * @param content   明文消息内容
     */
    void appendMessage(Long sessionId, String role, String content);

    /**
     * 加载最近 N 轮对话历史（解密后返回），按时间升序。
     *
     * @param sessionId 会话 ID
     * @param maxTurns  最多加载的消息条数
     * @return 对话历史（role + content 明文）
     */
    List<ChatMessage> loadRecentHistory(Long sessionId, int maxTurns);

    /**
     * 结束会话，写入会话总结（加密后落库）。
     *
     * @param sessionId 会话 ID
     * @param summary   会话总结明文（可为 null）
     */
    void endSession(Long sessionId, String summary);

    /**
     * 更新会话总结（加密后落库），不改变会话状态。
     *
     * <p>用于 SSE 每轮对话结束时写入 summary，但保持 status=0（进行中），
     * 以支持多轮对话复用同一会话。
     *
     * @param sessionId 会话 ID
     * @param summary   会话总结明文（可为 null，null 时不写入）
     */
    void updateSummary(Long sessionId, String summary);

    /**
     * 获取会话信息（summary 解密后返回）。
     *
     * @param sessionId 会话 ID
     * @return 会话 VO，不存在返回 null
     */
    SessionVO getSession(Long sessionId);
}
