package com.zhiyu.ai.flow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 心流检测服务（T1.7；2026-11-05 心流度量修复）。
 *
 * <p>每次心跳上报后调用 {@link #evaluate}，读-改-写 {@link StepStat}，
 * 返回 {@link FlowDetectionResult}（心流状态 + 本次增量秒数 + 派生信号）。
 *
 * <h3>算法</h3>
 * <ol>
 *   <li>从 {@link FlowStatStore} 读取 StepStat，不存在则初始化</li>
 *   <li>首次心跳：初始化 stepNo / dwellSeconds=0 / repeatCount=0 / lastFlowSeconds=0</li>
 *   <li>步骤切换判定：
 *     <ul>
 *       <li>stepNo 减小（回退）→ repeatCount++，发 {@link FlowSignal#STEP_BACKTRACK}</li>
 *       <li>stepNo 增大（前进）→ repeatCount <b>不变（不清零）</b>，发 {@link FlowSignal#STEP_ADVANCE}</li>
 *     </ul>
 *     新步骤时重置 dwellSeconds=0、lastFlowSeconds=0、dwellStagnationCount=0、stagnationSignalled=false</li>
 *   <li>同步骤「反复停留」判定（焦躁信号之二，本批补齐）：dwell 不增反降 = 一次重新停留，
 *     dwellStagnationCount++；次数 &gt; stuckRepeatTimes 且本步骤未发过 → 发
 *     {@link FlowSignal#DWELL_STAGNATION}（与 STEP_BACKTRACK 分开记录，不合并）</li>
 *   <li>更新 dwellSeconds</li>
 *   <li>呼吸响应重置：isBreathingResponded=true → repeatCount=0、breathingPrompted=false；
 *     若此前有未完结提醒 → 发 {@link FlowSignal#BREATHING_RESPONDED}</li>
 *   <li>心流判定：dwellSeconds ≥ focusIdleSeconds 且 isExited≠true → FLOW</li>
 *   <li>焦躁判定：repeatCount &gt; stuckRepeatTimes 且 isBreathingResponded≠true → ANXIOUS；
 *     进入焦躁态且未提醒过 → 发 {@link FlowSignal#BREATHING_PROMPT}（每轮提醒只发一次）</li>
 *   <li>心流增量计算（2026-11-05 修复「300 秒灌水」）：<b>只计入跨过阈值之后的时长</b>，
 *     基准 = max(lastFlowSeconds, focusIdleSeconds)，按心跳切片累加；
 *     dwell 跌出阈值时基准清零，再次跨入重新切片，不重复累计</li>
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
     * @return 心流检测结果（状态 + 增量秒数 + 派生信号 + 步骤切换上下文）
     */
    public FlowDetectionResult evaluate(Long sessionId,
                                        Integer stepNo,
                                        Integer dwellSeconds,
                                        Boolean isExited,
                                        Boolean isBreathingResponded) {

        boolean exited = Boolean.TRUE.equals(isExited);
        boolean breathingResponded = Boolean.TRUE.equals(isBreathingResponded);
        List<FlowSignal> signals = new ArrayList<>();
        Integer prevStepNo = null;
        Integer prevDwellSeconds = null;

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
            prevStepNo = stat.getStepNo();
            prevDwellSeconds = stat.getDwellSeconds();
            if (stepNo < stat.getStepNo()) {
                // 回退 → repeatCount++
                stat.setRepeatCount(stat.getRepeatCount() + 1);
                signals.add(FlowSignal.STEP_BACKTRACK);
                log.debug("步骤回退: {} → {}, repeatCount={}", stat.getStepNo(), stepNo, stat.getRepeatCount());
            } else {
                // 前进（stepNo > stat.stepNo）→ repeatCount 不变（不清零！）
                signals.add(FlowSignal.STEP_ADVANCE);
            }
            // 更新 stepNo，重置新步骤的计数
            stat.setStepNo(stepNo);
            stat.setDwellSeconds(0);
            stat.setLastFlowSeconds(0);
            stat.setDwellStagnationCount(0);
            stat.setStagnationSignalled(false);
        } else if (dwellSeconds < stat.getDwellSeconds()) {
            // ── 步骤 4a：同步骤 dwell 回落 = 重新停留一次（dwell_stagnation 信号源）──
            stat.setDwellStagnationCount(stat.getDwellStagnationCount() + 1);
            if (stat.getDwellStagnationCount() > stuckRepeatTimes && !stat.isStagnationSignalled()) {
                signals.add(FlowSignal.DWELL_STAGNATION);
                stat.setStagnationSignalled(true);
                log.debug("同步骤反复停留超阈值: sessionId={}, stepNo={}, stagnationCount={}",
                        sessionId, stepNo, stat.getDwellStagnationCount());
            }
        }

        // ── 步骤 4b：更新 dwellSeconds ──
        stat.setDwellSeconds(dwellSeconds);

        // ── 步骤 5：呼吸响应重置 ──
        if (breathingResponded) {
            if (stat.isBreathingPrompted()) {
                signals.add(FlowSignal.BREATHING_RESPONDED);
            }
            stat.setRepeatCount(0);
            stat.setBreathingPrompted(false);
            log.debug("用户响应了呼吸提醒，repeatCount 清零: sessionId={}", sessionId);
        }

        // ── 步骤 6：心流判定 ──
        boolean inFlow = stat.getDwellSeconds() >= focusIdleSeconds && !exited;

        // ── 步骤 7：焦躁判定 ──
        boolean needBreathing = stat.getRepeatCount() > stuckRepeatTimes && !breathingResponded;
        if (needBreathing && !stat.isBreathingPrompted()) {
            signals.add(FlowSignal.BREATHING_PROMPT);
            stat.setBreathingPrompted(true);
        }

        // ── 步骤 8：心流增量计算（只计跨阈值后的时长，按切片累加）──
        int flowDelta = 0;
        if (inFlow) {
            int creditedBase = Math.max(stat.getLastFlowSeconds(), focusIdleSeconds);
            if (stat.getDwellSeconds() > creditedBase) {
                flowDelta = stat.getDwellSeconds() - creditedBase;
                // lastFlowSeconds 为高水位，只在真正计入时上调，dwell 回落不动基准
                stat.setLastFlowSeconds(stat.getDwellSeconds());
            }
        } else if (stat.getDwellSeconds() < focusIdleSeconds) {
            // 跌出阈值：清零基准，再次跨入时从阈值重新切片，不重复累计
            stat.setLastFlowSeconds(0);
        }

        // 保存
        flowStatStore.save(sessionId, stat);

        // 返回
        FlowState state = inFlow ? FlowState.FLOW : (needBreathing ? FlowState.ANXIOUS : FlowState.NORMAL);
        log.debug("心流检测: sessionId={}, state={}, flowDelta={}, dwellSeconds={}, repeatCount={}, signals={}",
                sessionId, state, flowDelta, stat.getDwellSeconds(), stat.getRepeatCount(), signals);

        return new FlowDetectionResult(state, flowDelta, List.copyOf(signals), prevStepNo, prevDwellSeconds);
    }
}
