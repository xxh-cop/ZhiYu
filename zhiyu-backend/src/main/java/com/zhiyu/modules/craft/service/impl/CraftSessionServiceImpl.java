package com.zhiyu.modules.craft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ai.flow.FlowDetectionResult;
import com.zhiyu.ai.flow.FlowDetectionService;
import com.zhiyu.ai.flow.FlowState;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftSession;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.enums.SessionStatus;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftSessionMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.CraftSessionVO;
import com.zhiyu.modules.craft.vo.HeartbeatResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CraftSessionVO create(Long userId, Long projectId) {
        // 1. 校验项目存在且已上架
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (project.getStatus() == null || project.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "项目未上架");
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
        session.setStatus(SessionStatus.DOING.getCode());
        session.setCurrentStep(1);
        session.setStartTime(LocalDateTime.now());
        session.setFlowSeconds(0);
        craftSessionMapper.insert(session);
        log.info("创建会话: sessionId={}, userId={}, projectId={}", session.getId(), userId, projectId);

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

        // 3. 调 FlowDetectionService 做心流检测
        FlowDetectionResult detection = flowDetectionService.evaluate(
                sessionId,
                req.getStepNo(),
                req.getDwellSeconds(),
                req.getIsExited(),
                req.getIsBreathingResponded());

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
