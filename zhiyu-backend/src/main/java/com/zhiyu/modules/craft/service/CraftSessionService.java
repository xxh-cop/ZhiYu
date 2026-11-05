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
     * @param userId            当前用户 ID
     * @param projectId         手作项目 ID
     * @param emotionSessionId  关联的情绪会话 ID（可空）
     * @return 会话信息
     */
    CraftSessionVO create(Long userId, Long projectId, Long emotionSessionId);

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

    /**
     * 导出本人在合规下架窗口内的会话创作数据（2026-11-05 下架窗口导出）。
     *
     * <p>仅 {@code TakedownStateResolver} 判定 canExport（合规下架窗口内且导出开关开）
     * 可导出；窗口过期 / 不在窗口 / 开关关闭一律 3001 并给可读原因（不用 1004 伪装）。
     * 范围严格限定本人会话产物；成功导出写入 craft_session_event 的 export 事件。
     *
     * @param userId    当前用户 ID（须为会话归属者，否则 1003）
     * @param sessionId 会话 ID（不存在 1004）
     * @return 导出载荷（LinkedHashMap，键序稳定；雪花 ID 为字符串）
     */
    java.util.Map<String, Object> exportSession(Long userId, Long sessionId);
}
