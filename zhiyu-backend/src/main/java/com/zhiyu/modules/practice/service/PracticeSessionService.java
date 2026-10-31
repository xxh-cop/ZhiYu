package com.zhiyu.modules.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.practice.dto.CreatePracticeSessionDTO;
import com.zhiyu.modules.practice.entity.PracticeSession;
import com.zhiyu.modules.practice.enums.PracticeSessionStatus;
import com.zhiyu.modules.practice.mapper.PracticeSessionMapper;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeSessionService {

    private static final Set<String> KINDS = Set.of("HERITAGE", "CRAFT", "MINDFULNESS", "DRAWING");
    public static final int MAX_MINUTES = 180;

    private final PracticeSessionMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public PracticeSessionVO create(Long userId, CreatePracticeSessionDTO request) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (request == null) throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "练习请求不能为空");

        String kind = request.getKind() == null ? "" : request.getKind().trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的练习类型");
        }
        Integer minutes = request.getMinutesBudget();
        if (minutes == null || minutes < 1 || minutes > MAX_MINUTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "练习时间需在 1 到 180 分钟之间");
        }

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setKind(kind);
        session.setStatus(PracticeSessionStatus.CREATED.getCode());
        session.setMoodCode(trimToNull(request.getMoodCode(), 32));
        session.setMinutesBudget(minutes);
        session.setHeritagePatternId(trimToNull(request.getHeritagePatternId(), 32));
        session.setCurrentStep(1);
        mapper.insert(session);
        log.info("创建练习会话: practiceSessionId={}, userId={}, kind={}, minutes={}",
                session.getId(), userId, kind, minutes);
        return toVO(session);
    }

    public PracticeSessionVO get(Long userId, Long sessionId) {
        return toVO(requireOwned(userId, sessionId));
    }

    public List<PracticeSessionVO> list(Long userId, int limit) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        int size = limit < 1 ? 5 : Math.min(limit, 20);
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getUserId, userId)
                .orderByDesc(PracticeSession::getCreateTime)
                .last("LIMIT " + size);
        return mapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PracticeSessionVO transition(Long userId, Long sessionId, String action) {
        PracticeSession session = requireOwned(userId, sessionId);
        PracticeSessionStatus target = PracticeSessionStatus.targetForAction(action);
        if (target == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的练习操作");
        }
        PracticeSessionStatus current = PracticeSessionStatus.fromCode(session.getStatus());
        if (current == null || !current.canTransitTo(target)) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(),
                    "非法状态流转: " + (current == null ? "未知" : current.getLabel())
                            + " → " + target.getLabel());
        }

        LocalDateTime now = LocalDateTime.now();
        session.setStatus(target.getCode());
        if (target == PracticeSessionStatus.PRACTICING) {
            if (session.getStartedAt() == null) session.setStartedAt(now);
            session.setCompletedAt(null);
        }
        if (target == PracticeSessionStatus.COMPLETED || target == PracticeSessionStatus.ABANDONED) {
            session.setCompletedAt(now);
        }
        mapper.updateById(session);
        log.info("练习会话状态推进: practiceSessionId={}, {} → {}", sessionId, current.getLabel(), target.getLabel());
        return toVO(session);
    }

    public long countActive(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        LambdaQueryWrapper<PracticeSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSession::getUserId, userId)
                .in(PracticeSession::getStatus,
                        PracticeSessionStatus.CREATED.getCode(),
                        PracticeSessionStatus.LISTENING.getCode(),
                        PracticeSessionStatus.RECOMMENDED.getCode(),
                        PracticeSessionStatus.PRACTICING.getCode(),
                        PracticeSessionStatus.PAUSED.getCode());
        return mapper.selectCount(wrapper);
    }

    private PracticeSession requireOwned(Long userId, Long sessionId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (sessionId == null) throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        PracticeSession session = mapper.selectById(sessionId);
        if (session == null) throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        if (!userId.equals(session.getUserId())) throw new BusinessException(ResultCode.FORBIDDEN);
        return session;
    }

    private PracticeSessionVO toVO(PracticeSession session) {
        PracticeSessionVO vo = new PracticeSessionVO();
        vo.setPracticeSessionId(session.getId());
        vo.setKind(session.getKind());
        vo.setStatus(session.getStatus());
        PracticeSessionStatus status = PracticeSessionStatus.fromCode(session.getStatus());
        vo.setStatusLabel(status == null ? "未知" : status.getLabel());
        vo.setMoodCode(session.getMoodCode());
        vo.setMinutesBudget(session.getMinutesBudget());
        vo.setEmotionSessionId(session.getEmotionSessionId());
        vo.setCraftSessionId(session.getCraftSessionId());
        vo.setHeritagePatternId(session.getHeritagePatternId());
        vo.setCurrentStep(session.getCurrentStep());
        vo.setStartedAt(session.getStartedAt());
        vo.setCompletedAt(session.getCompletedAt());
        vo.setCreateTime(session.getCreateTime());
        return vo;
    }

    private String trimToNull(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isBlank()) return null;
        if (text.length() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "字段长度超过限制");
        }
        return text;
    }
}
