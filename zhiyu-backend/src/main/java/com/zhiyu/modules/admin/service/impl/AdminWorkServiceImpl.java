package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.service.AdminWorkService;
import com.zhiyu.modules.admin.vo.WorkDetailVO;
import com.zhiyu.modules.admin.vo.WorkListItemVO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link AdminWorkService} 实现（P1-B1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWorkServiceImpl implements AdminWorkService {

    private final com.zhiyu.modules.file.MediaService media;
    private final WorkMapper workMapper;
    private final UserMapper userMapper;
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;
    private final com.zhiyu.modules.work.service.SoftTierRecordService softTierRecordService;

    @Override
    public PageVO<WorkListItemVO> listWorks(Integer page, Integer pageSize, Integer status, Integer visible, String keyword) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Work::getStatus, status);
        }
        if (visible != null) {
            wrapper.eq(Work::getVisible, visible);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Work::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(Work::getCreateTime);

        IPage<Work> result = workMapper.selectPage(new Page<>(pageNum, size), wrapper);

        // 批量查 user 取 nickname 作为 authorName
        Set<Long> userIds = result.getRecords().stream()
                .map(Work::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = Map.of();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            nicknameMap = users.stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
        }

        final Map<Long, String> finalNicknameMap = nicknameMap;
        List<WorkListItemVO> records = result.getRecords().stream()
                .map(w -> {
                    WorkListItemVO vo = new WorkListItemVO();
                    vo.setId(w.getId());
                    vo.setUserId(w.getUserId());
                    vo.setAuthorName(finalNicknameMap.getOrDefault(w.getUserId(), ""));
                    vo.setTitle(w.getTitle());
                    vo.setCoverUrl(media.imageUrl(com.zhiyu.security.UserContext.getUserId(),w.getCoverUrl()));
                    vo.setMediaType(w.getMediaType());
                    vo.setVideoUrl(w.getVideoUrl());
                    vo.setVisible(w.getVisible());
                    vo.setAnonymous(w.getAnonymous());
                    vo.setLikeCount(w.getLikeCount());
                    vo.setStatus(w.getStatus());
                    vo.setCreateTime(w.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), pageNum, size, records);
    }

    @Override
    public WorkDetailVO getWork(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return toDetailVO(work);
    }

    @Override
    public void updateWorkStatus(Long id, Integer status) {
        updateWorkStatus(id, status, null);
    }

    @Override
    public void updateWorkStatus(Long id, Integer status, String reason) {
        Work work = workMapper.selectById(id);
        if (work == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "status 只能为 1 或 2");
        }
        // I1（2026-11-04）：下架/驳回必须携带理由（落库并随通知送达作者）
        if (status == 2 && (reason == null || reason.isBlank())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "下架作品必须填写理由");
        }
        work.setStatus(status);
        work.setAuditReason(reason);
        workMapper.updateById(work);
        log.info("管理员变更作品状态: workId={}, status={}, operator={}", id, status, UserContext.getUserId());
        // 通知系统（2026-11-04）：审核结果（含理由）触达作品归属者；通知失败不阻断审核主流程
        try {
            notificationService.notifyWorkAudit(work.getUserId(), work.getId(), work.getTitle(), status, reason);
        } catch (Exception e) {
            log.warn("作品审核通知写入失败（不阻断审核）: workId={}, err={}", id, e.getMessage());
        }
    }

    /**
     * 软档标记重载（2026-11-07 处置链）：softTier=true 只能随 status=1（通过）使用。
     * 状态机与通知完全复用 {@link #updateWorkStatus(Long, Integer, String)}；
     * 通过后同事务落软档台账+抽样+降权+降权通知（SoftTierRecordService.record）。
     * 处置链开关关闭时 record 抛 1001，整个状态变更回滚——管理员侧表现与「链未开启不可标记」一致。
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void updateWorkStatus(Long id, Integer status, String reason, Boolean softTier,
                                 String narrative, Boolean riskPointsNonEmpty) {
        if (Boolean.TRUE.equals(softTier) && !java.util.Objects.equals(status, 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "软档标记只能随通过动作使用");
        }
        updateWorkStatus(id, status, reason);
        if (Boolean.TRUE.equals(softTier)) {
            Work work = workMapper.selectById(id);
            softTierRecordService.record("work", id, work.getUserId(),
                    work.getTitle() + "\n" + java.util.Objects.toString(work.getMessage(), ""),
                    narrative, Boolean.TRUE.equals(riskPointsNonEmpty), work.getTitle());
        }
    }

    @Override
    public void deleteWork(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        workMapper.deleteById(id);
        log.info("管理员逻辑删除作品: workId={}, operator={}", id, UserContext.getUserId());
    }

    private WorkDetailVO toDetailVO(Work work) {
        WorkDetailVO vo = new WorkDetailVO();
        vo.setId(work.getId());
        vo.setUserId(work.getUserId());
        vo.setTitle(work.getTitle());
        vo.setCoverUrl(media.imageUrl(com.zhiyu.security.UserContext.getUserId(),work.getCoverUrl()));
        vo.setMediaType(work.getMediaType());
        vo.setVideoUrl(work.getVideoUrl());
        vo.setVisible(work.getVisible());
        vo.setAnonymous(work.getAnonymous());
        vo.setLikeCount(work.getLikeCount());
        vo.setStatus(work.getStatus());
        vo.setAuditReason(work.getAuditReason());
        vo.setCreateTime(work.getCreateTime());
        vo.setMessage(work.getMessage());
        vo.setProjectId(work.getProjectId());
        vo.setSessionId(work.getSessionId());
        // 查关联 user 取 nickname
        User user = userMapper.selectById(work.getUserId());
        if (user != null) {
            vo.setAuthorName(user.getNickname());
        } else {
            vo.setAuthorName("");
        }
        return vo;
    }
}
