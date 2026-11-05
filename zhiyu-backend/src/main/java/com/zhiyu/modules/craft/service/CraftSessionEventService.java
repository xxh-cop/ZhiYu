package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.craft.dto.SessionEventBatchReq;
import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.vo.SessionEventBatchResult;

import java.util.Map;

/**
 * 创作会话行为事件服务（2026-11-05 心流度量配套）。
 *
 * <p>两类写入路径：
 * <ul>
 *   <li>{@link #recordServerEvent} —— 服务端派生（心跳驱动的步骤/焦躁/呼吸/离席标记、
 *       会话结束钩子）；fire-and-forget，失败只记日志，不影响业务主流程；</li>
 *   <li>{@link #ingest} —— 客户端批量上报（生命周期、音频播放等沉浸强信号）；
 *       按 (session_id, client_seq) 幂等去重。</li>
 * </ul>
 */
public interface CraftSessionEventService {

    /**
     * 写入服务端派生事件。失败（如库不可用）只记 warn 日志，不抛异常。
     *
     * @param sessionId    会话 ID
     * @param userId       归属用户
     * @param projectId    所属项目
     * @param type         事件类型
     * @param stepNo       相关步骤（会话级事件传 null）
     * @param context      脱敏上下文（标量键值，可空）
     * @param dwellSeconds 步骤停留秒数（step_leave 等，可空）
     */
    void recordServerEvent(Long sessionId, Long userId, Long projectId,
                           CraftSessionEventType type, Integer stepNo,
                           Map<String, Object> context, Integer dwellSeconds);

    /**
     * 独立事务（REQUIRES_NEW）写入服务端派生事件。
     *
     * <p>用于「业务以异常收尾」的证据事件：如下架闸口拒绝（3001 触发外层事务回滚），
     * 证据事件必须独立提交才能存活。与 {@link #recordServerEvent} 同样失败只记日志。
     */
    void recordServerEventStandalone(Long sessionId, Long userId, Long projectId,
                                     CraftSessionEventType type, Integer stepNo,
                                     Map<String, Object> context, Integer dwellSeconds);

    /**
     * 客户端批量上报事件（幂等）。
     *
     * @param userId    当前用户
     * @param sessionId 会话 ID
     * @param req       事件批次（1-100 条）
     * @return 受理/去重/丢弃计数
     */
    SessionEventBatchResult ingest(Long userId, Long sessionId, SessionEventBatchReq req);
}
