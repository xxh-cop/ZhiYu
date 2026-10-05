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
        group.setStatus(status);
        supportGroupMapper.updateById(group);
        log.info("管理员变更小组状态: groupId={}, status={}, operator={}", id, status, UserContext.getUserId());
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
