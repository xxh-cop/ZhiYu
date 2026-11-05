package com.zhiyu.modules.craft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyu.modules.craft.entity.CraftSessionEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作会话行为事件 Mapper（2026-11-05 心流度量配套）。
 *
 * <p>自定义查询均显式带 {@code deleted=0}（BaseMapper 之外的 SQL 不走逻辑删除拦截）。
 */
@Mapper
public interface CraftSessionEventMapper extends BaseMapper<CraftSessionEvent> {

    /**
     * 会话是否已具备生命周期上报能力（收到过可见性/前后台事件）。
     * 未具备时挂机判定不工作（默认算沉浸，不错标老客户端）。
     */
    @Select("SELECT COUNT(*) FROM craft_session_event WHERE deleted=0 AND session_id=#{sessionId}"
            + " AND event_type IN ('visibility_change','app_lifecycle')")
    long countLifecycleEvents(@Param("sessionId") Long sessionId);

    /**
     * 窗口内的沉浸强信号数（可见性变化 / 前后台切换 / 音频播放）。
     * 窗口起点按「当前 dwell 秒数」回溯。
     */
    @Select("SELECT COUNT(*) FROM craft_session_event WHERE deleted=0 AND session_id=#{sessionId}"
            + " AND event_type IN ('visibility_change','app_lifecycle','audio_play')"
            + " AND occurred_at >= #{since}")
    long countActivityInWindow(@Param("sessionId") Long sessionId, @Param("since") LocalDateTime since);

    /**
     * 窗口内当前步骤是否已标记疑似离席（标记幂等，同一停留窗口只标一次）。
     */
    @Select("SELECT COUNT(*) FROM craft_session_event WHERE deleted=0 AND session_id=#{sessionId}"
            + " AND event_type='suspected_away' AND step_no=#{stepNo} AND occurred_at >= #{since}")
    long countAwayMarkedInWindow(@Param("sessionId") Long sessionId,
                                 @Param("stepNo") Integer stepNo,
                                 @Param("since") LocalDateTime since);

    /**
     * 同类步骤（同项目同步序号）的历史停留样本（step_leave.dwell_seconds），
     * 供 P95 估计；按发生时刻倒序截断，样本量由调用方封顶。
     */
    @Select("SELECT dwell_seconds FROM craft_session_event WHERE deleted=0"
            + " AND craft_project_id=#{projectId} AND step_no=#{stepNo}"
            + " AND event_type='step_leave' AND dwell_seconds IS NOT NULL"
            + " ORDER BY occurred_at DESC LIMIT #{limit}")
    List<Integer> selectStepLeaveDwells(@Param("projectId") Long projectId,
                                        @Param("stepNo") Integer stepNo,
                                        @Param("limit") int limit);
}
