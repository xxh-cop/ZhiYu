package com.zhiyu.modules.craft.service.impl;

import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.mapper.CraftSessionEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 疑似离席（挂机）判定（2026-11-05 心流度量修复）。
 *
 * <p>口径（产品拍板）：
 * <ul>
 *   <li><b>不要求输入事件</b>作为活跃门槛——「手在忙、手机没动」正是要捕捉的沉浸；</li>
 *   <li>强信号走生命周期：可见性变化、应用前后台切换、音频播放；</li>
 *   <li>前台静置<b>默认算沉浸</b>；仅当「同一步骤停留远超同类步骤 P95」
 *       且「页面可见但窗口内无任何生命周期事件」时判定疑似离席；</li>
 *   <li><b>只标记，不扣除</b>：命中只写 suspected_away 事件，flow_seconds 不动，
 *       分子偏差留给后续自评校准兜。</li>
 * </ul>
 *
 * <p>判定前置条件：会话须已具备生命周期上报能力（收到过 visibility_change /
 * app_lifecycle 事件）。老客户端无该能力时一律不标记，避免把「未上报」误读为「静置」。
 *
 * <p>P95 数据源：{@code craft_session_event} 中同项目同步序号的 step_leave.dwell_seconds
 * 样本；样本不足（&lt; min-samples）时不判定（冷启动期不标记）。
 * 阈值均为新增配置（zhiyu.flow.away.*），不改既有心流阈值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwayDetectionService {

    private final CraftSessionEventMapper eventMapper;

    /** 停留须超过 P95 × 该倍数才算「远超」 */
    @Value("${zhiyu.flow.away.p95-multiplier:2.0}")
    private double p95Multiplier;

    /** P95 估计的最小样本量，不足不判定 */
    @Value("${zhiyu.flow.away.min-samples:30}")
    private int minSamples;

    /** 停留低于该秒数一律不判定（绝对地板，避免短步骤误标） */
    @Value("${zhiyu.flow.away.floor-seconds:300}")
    private int floorSeconds;

    /** P95 样本截取上限（按发生时刻倒序） */
    @Value("${zhiyu.flow.away.sample-limit:500}")
    private int sampleLimit;

    /**
     * 判定当前心跳是否命中疑似离席。
     *
     * <p>纯查询，无副作用；命中由调用方写 suspected_away 事件（标记幂等由
     * {@link #countMarkedInWindow} 配合窗口查询保证）。
     *
     * @param session      会话（取 id / projectId）
     * @param stepNo       当前步骤
     * @param dwellSeconds 当前步骤停留秒数
     * @return true = 应标记 suspected_away
     */
    public boolean isSuspectedAway(CraftSession session, Integer stepNo, Integer dwellSeconds) {
        if (dwellSeconds == null || dwellSeconds < floorSeconds) {
            return false;
        }
        Double p95 = estimateP95(session.getProjectId(), stepNo);
        if (p95 == null) {
            return false;
        }
        if (dwellSeconds <= p95 * p95Multiplier) {
            return false;
        }
        // 老客户端无生命周期上报能力 → 不标记（默认算沉浸）
        if (eventMapper.countLifecycleEvents(session.getId()) == 0) {
            return false;
        }
        // 窗口内有任何生命周期/音频强信号 → 算沉浸
        LocalDateTime windowStart = LocalDateTime.now().minusSeconds(dwellSeconds);
        if (eventMapper.countActivityInWindow(session.getId(), windowStart) > 0) {
            return false;
        }
        // 同一停留窗口只标一次
        return countMarkedInWindow(session.getId(), stepNo, windowStart) == 0;
    }

    /** 窗口内当前步骤是否已标记过疑似离席。 */
    public long countMarkedInWindow(Long sessionId, Integer stepNo, LocalDateTime windowStart) {
        return eventMapper.countAwayMarkedInWindow(sessionId, stepNo, windowStart);
    }

    /**
     * 估计同类步骤停留 P95（最近样本，nearest-rank）。
     *
     * @return P95 秒数；样本不足返回 null
     */
    public Double estimateP95(Long projectId, Integer stepNo) {
        List<Integer> dwells = eventMapper.selectStepLeaveDwells(projectId, stepNo, sampleLimit);
        if (dwells.size() < minSamples) {
            return null;
        }
        Collections.sort(dwells);
        int rank = (int) Math.ceil(0.95 * dwells.size()) - 1;
        return dwells.get(Math.max(0, rank)).doubleValue();
    }
}
