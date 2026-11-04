package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.service.AdminGroupService;
import com.zhiyu.modules.admin.vo.GroupDetailVO;
import com.zhiyu.modules.admin.vo.GroupListItemVO;
import com.zhiyu.modules.group.entity.SupportGroup;
import com.zhiyu.modules.group.mapper.SupportGroupMapper;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AdminGroupService} 实现（P1-B1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGroupServiceImpl implements AdminGroupService {

    private final SupportGroupMapper supportGroupMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;

    @Override
    public PageVO<GroupListItemVO> listGroups(Integer page, Integer pageSize, Integer status, String keyword) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

        LambdaQueryWrapper<SupportGroup> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SupportGroup::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SupportGroup::getName, keyword.trim());
        }
        wrapper.orderByDesc(SupportGroup::getCreateTime);

        IPage<SupportGroup> result = supportGroupMapper.selectPage(new Page<>(pageNum, size), wrapper);

        List<GroupListItemVO> records = result.getRecords().stream()
                .map(this::toListItemVO)
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), pageNum, size, records);
    }

    @Override
    public GroupDetailVO getGroup(Long id) {
        SupportGroup group = supportGroupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return toDetailVO(group);
    }

    @Override
    public void updateGroupStatus(Long id, Integer status) {
        SupportGroup group = supportGroupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "status 只能为 0 或 1");
        }
        Integer oldStatus = group.getStatus();
        group.setStatus(status);
        supportGroupMapper.updateById(group);
        log.info("管理员变更小组状态: groupId={}, status={}, operator={}", id, status, UserContext.getUserId());
        // BG2（2026-11-05）：状态真实变化才通知组长（同状态重复操作不打扰）；失败不阻断
        if (!status.equals(oldStatus)) {
            try {
                var leaders = jdbc.queryForList(
                        "SELECT user_id FROM group_member WHERE group_id=? AND role='组长' AND deleted=0 LIMIT 1",
                        Long.class, id);
                if (!leaders.isEmpty()) {
                    notificationService.notifyGroupStatus(leaders.get(0), id, group.getName(), status == 1);
                }
            } catch (Exception e) {
                log.warn("小组状态通知写入失败（不阻断状态变更）: groupId={}, err={}", id, e.getMessage());
            }
        }
    }

    @Override
    public void deleteGroup(Long id) {
        SupportGroup group = supportGroupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        supportGroupMapper.deleteById(id);
        log.info("管理员逻辑删除小组: groupId={}, operator={}", id, UserContext.getUserId());
    }

    private GroupListItemVO toListItemVO(SupportGroup group) {
        GroupListItemVO vo = new GroupListItemVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setMemberCount(group.getMemberCount());
        vo.setMaxCount(group.getMaxCount());
        vo.setStatus(group.getStatus());
        vo.setCreateTime(group.getCreateTime());
        return vo;
    }

    private GroupDetailVO toDetailVO(SupportGroup group) {
        GroupDetailVO vo = new GroupDetailVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setMemberCount(group.getMemberCount());
        vo.setMaxCount(group.getMaxCount());
        vo.setStatus(group.getStatus());
        vo.setCreateTime(group.getCreateTime());
        return vo;
    }
}
