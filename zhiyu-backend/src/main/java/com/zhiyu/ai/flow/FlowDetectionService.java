package com.zhiyu.ai.flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 心流检测服务（T1.7）。
 *
 * <p>每次心跳上报后调用 {@link #evaluate}，读-改-写 {@link StepStat}，
 * 返回 {@link FlowDetectionResult}（心流状态 + 本次增量秒数）。
 *
 * <h3>算法（8 步）</h3>
 * <ol>
 *   <li>从 {@link FlowStatStore} 读取 StepStat，不存在则初始化</li>
 *   <li>首次心跳：初始化 stepNo / dwellSeconds=0 / repeatCount=0 / lastFlowSeconds=0</li>
 *   <li>步骤切换判定：
 *     <ul>
 *       <li>stepNo 减小（回退）→ repeatCount++</li>
 *       <li>stepNo 增大（前进）→ repeatCount <b>不变（不清零）</b></li>
 *       <li>stepNo 不变 → repeatCount 不变</li>
 *     </ul>
 *     新步骤时重置 dwellSeconds=0、lastFlowSeconds=0</li>
 *   <li>更新 dwellSeconds</li>
 *   <li>呼吸响应重置：isBreathingResponded=true → repeatCount=0</li>
 *   <li>心流判定：dwellSeconds ≥ focusIdleSeconds 且 isExited≠true → FLOW</li>
 *   <li>焦躁判定：repeatCount > stuckRepeatTimes 且 isBreathingResponded≠true → ANXIOUS</li>
 *   <li>心流增量计算：inFlow 且 dwellSeconds > lastFlowSeconds → delta = 差值，更新 lastFlowSeconds</li>
 * </ol>
 *
 * <p><b>repeatCount 清零规则</b>（A4 裁决 + 用户裁决）：
 * 只有用户主动响应呼吸提醒（isBreathingResponded=true）才清零。
 * 步骤前进<b>不清零</b>——反复横跳（1→2→1→2→1）必须累积计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDetectionService {

    private final FlowStatStore flowStatStore;

    @Value("${zhiyu.flow.focus-idle-seconds:300}")
    private int focusIdleSeconds;

    @Value("${zhiyu.flow.stuck-repeat-times:3}")
    private int stuckRepeatTimes;

    /**
     * 评估本次心跳的心流状态。
     *
     * @param sessionId           会话 ID
     * @param stepNo              当前步骤序号（非空）
     * @param dwellSeconds        在本步骤停留秒数（非空）
     * @param isExited            是否主动退出（可空，null 视为 false）
     * @param isBreathingResponded 是否响应了呼吸提醒（可空，null 视为 false）
     * @return 心流检测结果（状态 + 增量秒数）
     */
    public FlowDetectionResult evaluate(Long sessionId,
                                        Integer stepNo,
                                        Integer dwellSeconds,
                                        Boolean isExited,
                                        Boolean isBreathingResponded) {

        boolean exited = Boolean.TRUE.equals(isExited);
        boolean breathingResponded = Boolean.TRUE.equals(isBreathingResponded);

        // ── 步骤 1-2：读取或初始化 StepStat ──
        StepStat stat = flowStatStore.get(sessionId);
        if (stat == null) {
            stat = new StepStat();
            stat.setStepNo(stepNo);
            stat.setDwellSeconds(0);
            stat.setRepeatCount(0);
            stat.setLastFlowSeconds(0);
            log.debug("首次心跳，初始化 StepStat: sessionId={}, stepNo={}", sessionId, stepNo);
        }

        // ── 步骤 3：步骤切换判定 ──
        if (stepNo != stat.getStepNo()) {
            if (stepNo < stat.getStepNo()) {
                // 回退 → repeatCount++
                stat.setRepeatCount(stat.getRepeatCount() + 1);
                log.debug("步骤回退: {} → {}, repeatCount={}", stat.getStepNo(), stepNo, stat.getRepeatCount());
            }
            // 前进（stepNo > stat.stepNo）→ repeatCount 不变（不清零！）
            // 更新 stepNo，重置新步骤的计数
            stat.setStepNo(stepNo);
            stat.setDwellSeconds(0);
            stat.setLastFlowSeconds(0);
        }

        // ── 步骤 4：更新 dwellSeconds ──
        stat.setDwellSeconds(dwellSeconds);

        // ── 步骤 5：呼吸响应重置 ──
        if (breathingResponded) {
            stat.setRepeatCount(0);
            log.debug("用户响应了呼吸提醒，repeatCount 清零: sessionId={}", sessionId);
        }

        // ── 步骤 6：心流判定 ──
        boolean inFlow = stat.getDwellSeconds() >= focusIdleSeconds && !exited;

        // ── 步骤 7：焦躁判定 ──
        boolean needBreathing = stat.getRepeatCount() > stuckRepeatTimes && !breathingResponded;

        // ── 步骤 8：心流增量计算 ──
        int flowDelta = 0;
        if (inFlow && stat.getDwellSeconds() > stat.getLastFlowSeconds()) {
            flowDelta = stat.getDwellSeconds() - stat.getLastFlowSeconds();
            stat.setLastFlowSeconds(stat.getDwellSeconds());
        }

        // 保存
        flowStatStore.save(sessionId, stat);

        // 返回
        FlowState state = inFlow ? FlowState.FLOW : (needBreathing ? FlowState.ANXIOUS : FlowState.NORMAL);
        log.debug("心流检测: sessionId={}, state={}, flowDelta={}, dwellSeconds={}, repeatCount={}",
                sessionId, state, flowDelta, stat.getDwellSeconds(), stat.getRepeatCount());

        return new FlowDetectionResult(state, flowDelta);
    }
}
