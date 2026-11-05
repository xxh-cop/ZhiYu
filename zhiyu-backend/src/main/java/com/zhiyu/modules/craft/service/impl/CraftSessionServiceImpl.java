package com.zhiyu.modules.craft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ai.flow.FlowDetectionResult;
import com.zhiyu.ai.flow.FlowDetectionService;
import com.zhiyu.ai.flow.FlowSignal;
import com.zhiyu.ai.flow.FlowState;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.enums.CraftSessionEventType;
import com.zhiyu.modules.craft.enums.SessionStatus;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.CraftSessionVO;
import com.zhiyu.modules.craft.vo.HeartbeatResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 创作会话服务实现（T1.6）。
 *
 * <p>状态机流转见 {@link SessionStatus#canTransitTo}。
 * 非法流转抛 {@link BusinessException}（code=3001，HTTP 200）。
 * 所有权校验返回 1003（FORBIDDEN/403），不是 1004。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraftSessionServiceImpl implements CraftSessionService {

    private final CraftSessionMapper craftSessionMapper;
    private final CraftProjectMapper craftProjectMapper;
    private final CraftStepMapper craftStepMapper;
    private final FlowDetectionService flowDetectionService;
    private final com.zhiyu.modules.craft.takedown.TakedownProperties takedownProperties;
    private final CraftSessionEventService craftSessionEventService;
    private final AwayDetectionService awayDetectionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CraftSessionVO create(Long userId, Long projectId, Long emotionSessionId) {
        craftSessionMapper.lockProject(projectId);
        // 1. 校验项目存在且已上架
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (project.getStatus() == null || project.getStatus() != 1) {
            // AY1（2026-11-05）：非上架（待审/下架）统一 1004，不向学生泄露项目状态
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 检查是否已有未完成的同项目会话
        LambdaQueryWrapper<CraftSession> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(CraftSession::getUserId, userId)
                .eq(CraftSession::getProjectId, projectId)
                .in(CraftSession::getStatus, SessionStatus.DOING.getCode(), SessionStatus.PAUSED.getCode());
        Long activeCount = craftSessionMapper.selectCount(activeWrapper);
        if (activeCount > 0) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "已有进行中的会话");
        }

        // 3. 新建会话，直接 DOING
        CraftSession session = new CraftSession();
        session.setUserId(userId);
        session.setProjectId(projectId);
        session.setEmotionSessionId(emotionSessionId);
        session.setStatus(SessionStatus.DOING.getCode());
        session.setCurrentStep(1);
        session.setStartTime(LocalDateTime.now());
        session.setFlowSeconds(0);
        craftSessionMapper.insert(session);
        log.info("创建会话: sessionId={}, userId={}, projectId={}, emotionSessionId={}",
                session.getId(), userId, projectId, emotionSessionId);

        return buildVO(session, project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CraftSessionVO changeStatus(Long userId, Long sessionId, String action) {
        // 1. 查会话
        CraftSession session = craftSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 所有权校验 → 403
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 3. 解析 action → target status
        SessionStatus target = parseAction(action);
        if (target == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的操作: " + action);
        }

        // 4. 状态机校验
        SessionStatus current = SessionStatus.fromCode(session.getStatus());
        if (current == null || !current.canTransitTo(target)) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(),
                    "非法状态流转: " + (current == null ? "未知" : current.getLabel())
                            + " → " + target.getLabel());
        }

        // 5. 更新
        session.setStatus(target.getCode());
        if (target == SessionStatus.FINISHED || target == SessionStatus.ABANDONED) {
            session.setEndTime(LocalDateTime.now());
        }
        craftSessionMapper.updateById(session);
        log.info("状态推进: sessionId={}, {} → {}", sessionId, current.getLabel(), target.getLabel());

        // 5b. 会话结束事件钩子（2026-11-05）：完成/放弃写结束事件，
        // context 携带时长/心流/步骤，供后续「20% 采样单题自评校准」挂接（本批只留钩子）
        if (target == SessionStatus.FINISHED || target == SessionStatus.ABANDONED) {
            Map<String, Object> context = new HashMap<>();
            if (session.getStartTime() != null && session.getEndTime() != null) {
                context.put("durationSeconds", Duration.between(session.getStartTime(), session.getEndTime()).getSeconds());
            }
            context.put("flowSeconds", session.getFlowSeconds() != null ? session.getFlowSeconds() : 0);
            if (session.getCurrentStep() != null) {
                context.put("currentStep", session.getCurrentStep());
            }
            craftSessionEventService.recordServerEvent(sessionId, userId, session.getProjectId(),
                    target == SessionStatus.FINISHED
                            ? CraftSessionEventType.SESSION_FINISH
                            : CraftSessionEventType.SESSION_ABANDON,
                    null, context, null);
        }

        // 重新查 project（构建 VO 用）
        CraftProject project = craftProjectMapper.selectById(session.getProjectId());
        return buildVO(session, project);
    }

    @Override
    public CraftSessionVO getProgress(Long userId, Long sessionId) {
        CraftSession session = craftSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        CraftProject project = craftProjectMapper.selectById(session.getProjectId());
        return buildVO(session, project);
    }

    // ──────────────────────────────────────────────────────
    //  T1.7 心跳
    // ──────────────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HeartbeatResult heartbeat(Long userId, Long sessionId, HeartbeatReq req) {
        // 1. 查 session
        CraftSession session = craftSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 所有权校验 → 403
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 2. 状态校验：只允许 DOING 状态的心跳
        SessionStatus current = SessionStatus.fromCode(session.getStatus());
        if (current != SessionStatus.DOING) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(),
                    "会话当前状态不允许心跳: " + (current != null ? current.getLabel() : "未知"));
        }

        // 2b. 分层下架闸口（2026-11-05）：合规下架/已归档项目的会话转只读，不可推进步骤；
        // 提示语与截止时间经 GET /api/craft/projects/{id}/steps 结构化下发
        CraftProject project = craftProjectMapper.selectById(session.getProjectId());
        if (project != null) {
            var view = com.zhiyu.modules.craft.takedown.TakedownStateResolver.resolve(
                    project, takedownProperties, LocalDateTime.now());
            if (view.readOnly()) {
                throw new BusinessException(ResultCode.BAD_STATE.getCode(),
                        "项目已下架，会话已转为只读，可回看历史步骤但无法继续推进");
            }
        }

        // 3. 调 FlowDetectionService 做心流检测
        FlowDetectionResult detection = flowDetectionService.evaluate(
                sessionId,
                req.getStepNo(),
                req.getDwellSeconds(),
                req.getIsExited(),
                req.getIsBreathingResponded());

        // 3b. 行为事件派生（2026-11-05）：步骤切换/焦躁双信号/呼吸信号落事件表。
        // 观测数据失败不影响主流程（recordServerEvent 内部已吞异常，外层兜底检测异常）
        try {
            recordHeartbeatEvents(session, req, detection);
        } catch (Exception e) {
            log.warn("心跳事件派生失败（已忽略）: sessionId={}, err={}", sessionId, e.getMessage());
        }

        // 3c. 疑似离席判定（2026-11-05）：只标记，不从 flow_seconds 扣除。
        // 不要求输入事件；强信号走生命周期；前台静置默认算沉浸。
        try {
            if (awayDetectionService.isSuspectedAway(session, req.getStepNo(), req.getDwellSeconds())) {
                craftSessionEventService.recordServerEvent(sessionId, userId, session.getProjectId(),
                        CraftSessionEventType.SUSPECTED_AWAY, req.getStepNo(),
                        Map.of("dwellSeconds", req.getDwellSeconds()), req.getDwellSeconds());
                log.info("疑似离席标记: sessionId={}, stepNo={}, dwell={}", sessionId, req.getStepNo(), req.getDwellSeconds());
            }
        } catch (Exception e) {
            log.warn("疑似离席判定失败（已忽略）: sessionId={}, err={}", sessionId, e.getMessage());
        }

        // 4. 更新 current_step（如果 stepNo 变了）——只更新这一个字段，避免覆盖 flow_seconds
        if (!req.getStepNo().equals(session.getCurrentStep())) {
            craftSessionMapper.updateCurrentStep(sessionId, req.getStepNo());
            log.info("心跳推进步骤: sessionId={}, currentStep={}", sessionId, req.getStepNo());
        }

        // 5. flowDelta > 0 时原子累加 flow_seconds
        if (detection.flowDelta() > 0) {
            craftSessionMapper.addFlowSeconds(sessionId, detection.flowDelta());
            log.info("心流累加: sessionId={}, delta={}, state={}", sessionId, detection.flowDelta(), detection.state());
        }

        // 6. 返回心流判定信号
        boolean inFlow = detection.state() == FlowState.FLOW;
        boolean needBreathing = detection.state() == FlowState.ANXIOUS;
        return new HeartbeatResult(inFlow, needBreathing);
    }

    // ──────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────

    /**
     * 把一次心跳的心流检测结果派生为行为事件（2026-11-05）。
     *
     * <ul>
     *   <li>步骤切换 → step_leave（携带原步骤最终停留秒数，供 P95 统计）+ step_enter；</li>
     *   <li>焦躁双信号分开记：step_backtrack（回退）与 dwell_stagnation（同步骤反复停留）；</li>
     *   <li>呼吸引导触发/响应 → breathing_prompt / breathing_responded。</li>
     * </ul>
     */
    private void recordHeartbeatEvents(CraftSession session, HeartbeatReq req, FlowDetectionResult detection) {
        Long sessionId = session.getId();
        Long userId = session.getUserId();
        Long projectId = session.getProjectId();

        // 步骤切换：先记原步骤离开（带最终停留），再记新步骤进入
        if (detection.prevStepNo() != null) {
            Integer prevDwell = detection.prevDwellSeconds();
            craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                    CraftSessionEventType.STEP_LEAVE, detection.prevStepNo(),
                    prevDwell != null ? Map.of("dwellSeconds", prevDwell) : null, prevDwell);
            craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                    CraftSessionEventType.STEP_ENTER, req.getStepNo(), null, null);
        }

        for (FlowSignal signal : detection.signals()) {
            switch (signal) {
                case STEP_ADVANCE -> craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                        CraftSessionEventType.STEP_ADVANCE, req.getStepNo(), null, null);
                case STEP_BACKTRACK -> craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                        CraftSessionEventType.STEP_BACKTRACK, req.getStepNo(), null, null);
                case DWELL_STAGNATION -> craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                        CraftSessionEventType.DWELL_STAGNATION, req.getStepNo(),
                        Map.of("dwellSeconds", req.getDwellSeconds()), req.getDwellSeconds());
                case BREATHING_PROMPT -> craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                        CraftSessionEventType.BREATHING_PROMPT, req.getStepNo(), null, null);
                case BREATHING_RESPONDED -> craftSessionEventService.recordServerEvent(sessionId, userId, projectId,
                        CraftSessionEventType.BREATHING_RESPONDED, req.getStepNo(), null, null);
                default -> {
                    // 未映射信号不落事件
                }
            }
        }
    }

    private SessionStatus parseAction(String action) {
        return switch (action) {
            case "pause" -> SessionStatus.PAUSED;
            case "resume" -> SessionStatus.DOING;
            case "finish" -> SessionStatus.FINISHED;
            case "abandon" -> SessionStatus.ABANDONED;
            default -> null;
        };
    }

    /**
     * 组装 VO，含 projectTitle 和 totalSteps。
     */
    private CraftSessionVO buildVO(CraftSession session, CraftProject project) {
        CraftSessionVO vo = new CraftSessionVO();
        vo.setSessionId(session.getId());
        vo.setProjectId(session.getProjectId());
        vo.setProjectTitle(project != null ? project.getTitle() : null);
        vo.setStatus(session.getStatus());
        SessionStatus ss = SessionStatus.fromCode(session.getStatus());
        vo.setStatusLabel(ss != null ? ss.getLabel() : "未知");
        vo.setCurrentStep(session.getCurrentStep());
        vo.setStartTime(session.getStartTime());
        vo.setEndTime(session.getEndTime());

        // totalSteps：count craft_step where project_id = ?
        LambdaQueryWrapper<CraftStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(CraftStep::getProjectId, session.getProjectId());
        Long stepCount = craftStepMapper.selectCount(stepWrapper);
        vo.setTotalSteps(stepCount.intValue());

        return vo;
    }
}
