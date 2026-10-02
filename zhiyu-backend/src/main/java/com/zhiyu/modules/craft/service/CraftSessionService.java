package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.craft.vo.CraftSessionVO;

/**
 * 创作会话服务（T1.6）。
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
}
