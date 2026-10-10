package com.zhiyu.modules.inheritor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.modules.inheritor.service.InheritorPublicService;
import com.zhiyu.modules.inheritor.vo.InheritorPublicDetailVO;
import com.zhiyu.modules.inheritor.vo.InheritorPublicItemVO;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 传承人公开查询 Service 实现（M2 批 6A）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InheritorPublicServiceImpl implements InheritorPublicService {

    private final InheritorProfileMapper inheritorProfileMapper;
    private final UserMapper userMapper;
    private final WorkMapper workMapper;

    @Override
    public PageVO<InheritorPublicItemVO> listInheritors(int page, int pageSize, String keyword) {
        // 1. 查询 verified=1 的传承人
        LambdaQueryWrapper<InheritorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InheritorProfile::getVerified, 1);

        // 2. 如果有 keyword，分两步：
        //    a) 先查 user 表中 nickname like keyword 的 user_id 集合
        //    b) 在 wrapper 中用 skill like OR user_id IN (...) 筛选
        boolean hasKeyword = StringUtils.hasText(keyword);
        Set<Long> matchedUserIds = null;
        if (hasKeyword) {
            // 查 skill like keyword 的传承人 + nickname like keyword 的用户
            wrapper.like(InheritorProfile::getSkill, keyword);

            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getNickname, keyword);
            List<User> matchedUsers = userMapper.selectList(userWrapper);
            matchedUserIds = matchedUsers.stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());

            if (!matchedUserIds.isEmpty()) {
                // skill LIKE OR user_id IN (...)
                wrapper.or().in(InheritorProfile::getUserId, matchedUserIds);
            }
        }

        // 按创建时间倒序
        wrapper.orderByDesc(InheritorProfile::getCreateTime);

        IPage<InheritorProfile> pageResult = inheritorProfileMapper.selectPage(
                new Page<>(page, pageSize), wrapper);

        List<InheritorProfile> profiles = pageResult.getRecords();
        if (profiles.isEmpty()) {
            return new PageVO<>(pageResult.getTotal(), page, pageSize, Collections.emptyList());
        }

        // 3. 批量查关联的 user 信息
        Set<Long> userIds = profiles.stream()
                .map(InheritorProfile::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 4. 组装 VO
        List<InheritorPublicItemVO> items = new ArrayList<>(profiles.size());
        for (InheritorProfile p : profiles) {
            User u = userMap.get(p.getUserId());
            InheritorPublicItemVO vo = new InheritorPublicItemVO();
            vo.setId(p.getId());
            vo.setUserId(p.getUserId());
            vo.setSkill(p.getSkill());
            vo.setHonor(p.getHonor());
            vo.setIntro(p.getIntro());
            if (u != null) {
                vo.setNickname(u.getNickname());
                vo.setAvatarUrl(u.getAvatarUrl());
            }
            items.add(vo);
        }

        return new PageVO<>(pageResult.getTotal(), page, pageSize, items);
    }

    @Override
    public InheritorPublicDetailVO getInheritor(Long id) {
        InheritorProfile profile = inheritorProfileMapper.selectById(id);
        if (profile == null || profile.getVerified() == null || profile.getVerified() != 1) {
            // 不存在或未认证 → 对学生不可见
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        User user = userMapper.selectById(profile.getUserId());

        InheritorPublicDetailVO vo = new InheritorPublicDetailVO();
        vo.setId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setSkill(profile.getSkill());
        vo.setHonor(profile.getHonor());
        vo.setIntro(profile.getIntro());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatarUrl(user.getAvatarUrl());
            vo.setSchool(user.getSchool());
        }

        // 统计数据（批次 6-E）
        Long workCount = workMapper.selectCount(
                new LambdaQueryWrapper<Work>()
                        .eq(Work::getUserId, profile.getUserId())
                        .eq(Work::getStatus, 1));
        vo.setWorkCount(workCount);

        // 粉丝数：当前无粉丝表，固定 0
        vo.setFanCount(0L);

        // 获赞数：SUM(like_count) WHERE user_id = 该传承人
        List<Work> works = workMapper.selectList(
                new LambdaQueryWrapper<Work>()
                        .eq(Work::getUserId, profile.getUserId())
                        .select(Work::getLikeCount));
        long totalLikes = works.stream()
                .filter(w -> w.getLikeCount() != null)
                .mapToLong(Work::getLikeCount)
                .sum();
        vo.setLikeCount(totalLikes);

        return vo;
    }
}
