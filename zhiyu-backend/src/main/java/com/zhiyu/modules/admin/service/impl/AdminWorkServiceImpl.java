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

    private final WorkMapper workMapper;
    private final UserMapper userMapper;

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
                    vo.setCoverUrl(w.getCoverUrl());
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
        Work work = workMapper.selectById(id);
        if (work == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "status 只能为 1 或 2");
        }
        work.setStatus(status);
        workMapper.updateById(work);
        log.info("管理员变更作品状态: workId={}, status={}, operator={}", id, status, UserContext.getUserId());
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
        vo.setCoverUrl(work.getCoverUrl());
        vo.setVisible(work.getVisible());
        vo.setAnonymous(work.getAnonymous());
        vo.setLikeCount(work.getLikeCount());
        vo.setStatus(work.getStatus());
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
