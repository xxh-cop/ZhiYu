package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.vo.CraftSessionVO;
import com.zhiyu.modules.craft.vo.HeartbeatResult;

/**
 * 创作会话服务（T1.6 / T1.7）。
 *
 * <p>状态机：创建 → DOING(1) → PAUSED(2) ↔ DOING(1) → FINISHED(3) / ABANDONED(4)。
 * 没有 start 动作，创建即为进行中。
 */
public interface CraftSessionService {

    /**
     * 创建会话，状态直接为进行中（DOING=1）。
     *
     * @param userId    当前用户 ID
     * @param projectId 手作项目 ID
     * @return 会话信息
     */
    CraftSessionVO create(Long userId, Long projectId);

    /**
     * 推进会话状态机。
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 ID
     * @param action    操作：pause / resume / finish / abandon
     * @return 更新后的会话信息
     */
    CraftSessionVO changeStatus(Long userId, Long sessionId, String action);

    /**
     * 查询会话进度。
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 ID
     * @return 会话信息
     */
    CraftSessionVO getProgress(Long userId, Long sessionId);

    /**
     * 上报步骤心跳，驱动心流/焦躁判定（T1.7）。
     *
     * <p>不改主状态机（status 不变），只更新 current_step 和 flow_seconds。
     *
     * @param userId    当前用户 ID
     * @param sessionId 会话 ID
     * @param req       心跳请求（stepNo / dwellSeconds / isExited / isBreathingResponded）
     * @return 心流判定信号（inFlow / needBreathing）
     */
    HeartbeatResult heartbeat(Long userId, Long sessionId, HeartbeatReq req);
}
