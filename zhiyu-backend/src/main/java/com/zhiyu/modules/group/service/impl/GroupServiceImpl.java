package com.zhiyu.modules.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.group.dto.GroupCreateReq;
import com.zhiyu.modules.group.entity.GroupMember;
import com.zhiyu.modules.group.entity.SupportGroup;
import com.zhiyu.modules.group.mapper.GroupMemberMapper;
import com.zhiyu.modules.group.mapper.SupportGroupMapper;
import com.zhiyu.modules.group.service.GroupService;
import com.zhiyu.modules.group.vo.GroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 互助小组服务实现（T1.8b）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final SupportGroupMapper supportGroupMapper;
    private final GroupMemberMapper groupMemberMapper;

    private static final int DEFAULT_MAX_COUNT = 50;

    @Override
    public List<GroupVO> listGroups(Long userId) {
        // 1. 查所有 status=1 AND deleted=0 的小组，按 member_count desc
        LambdaQueryWrapper<SupportGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SupportGroup::getStatus, 1)
                .orderByDesc(SupportGroup::getMemberCount)
                .orderByAsc(SupportGroup::getId);
        List<SupportGroup> groups = supportGroupMapper.selectList(wrapper);

        if (groups.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 批量查当前用户在这些小组里的 group_member（一次查完，避免 N+1）
        Set<Long> groupIds = groups.stream().map(SupportGroup::getId).collect(Collectors.toSet());
        LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(GroupMember::getUserId, userId)
                .in(GroupMember::getGroupId, groupIds);
        List<GroupMember> myMemberships = groupMemberMapper.selectList(memberWrapper);
        Map<Long, GroupMember> myMemberMap = myMemberships.stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, m -> m));

        // 3. 组装 VO
        return groups.stream()
                .map(g -> {
                    GroupVO vo = buildVO(g);
                    GroupMember membership = myMemberMap.get(g.getId());
                    if (membership != null) {
                        vo.setJoined(true);
                        vo.setMyRole(membership.getRole());
                    } else {
                        vo.setJoined(false);
                        vo.setMyRole(null);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupVO createGroup(Long userId, GroupCreateReq req) {
        int maxCount = (req.getMaxCount() != null) ? req.getMaxCount() : DEFAULT_MAX_COUNT;

        // 1. 构造 SupportGroup
        SupportGroup group = new SupportGroup();
        group.setName(req.getName());
        group.setDescription(req.getDescription());
        group.setMaxCount(maxCount);
        group.setMemberCount(1); // 创建者算 1 个成员
        group.setStatus(1);
        supportGroupMapper.insert(group);
        log.info("创建小组: groupId={}, userId={}, name={}", group.getId(), userId, req.getName());

        // 2. 插入组长成员记录
        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole("组长");
        groupMemberMapper.insert(member);

        // 3. 返回 VO
        GroupVO vo = buildVO(group);
        vo.setJoined(true);
        vo.setMyRole("组长");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupVO joinGroup(Long userId, Long groupId) {
        // 1. 查小组 → 不存在 → 1004
        SupportGroup group = supportGroupMapper.lockById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. status != 1 → 3001（小组已关闭）
        if (group.getStatus() == null || group.getStatus() != 1) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "小组已关闭");
        }

        // 3. 查是否已加入 → 3001
        LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        Long existing = groupMemberMapper.selectCount(memberWrapper);
        if (existing > 0) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "已加入该小组");
        }

        // 4. 原子递增（并发保护）
        int rows = supportGroupMapper.incrementMemberCount(groupId);
        if (rows == 0) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "小组已满");
        }

        // 5. 插入成员记录
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole("成员");
        if(groupMemberMapper.restore(groupId,userId)==0)groupMemberMapper.insert(member);
        log.info("加入小组: groupId={}, userId={}", groupId, userId);

        // 6. 重新查小组获取最新 memberCount
        group = supportGroupMapper.selectById(groupId);

        // 7. 返回 VO
        GroupVO vo = buildVO(group);
        vo.setJoined(true);
        vo.setMyRole("成员");
        return vo;
    }

    // ──────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────

    private GroupVO buildVO(SupportGroup group) {
        GroupVO vo = new GroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setMaxCount(group.getMaxCount());
        vo.setMemberCount(group.getMemberCount());
        vo.setStatus(group.getStatus());
        return vo;
    }
}
