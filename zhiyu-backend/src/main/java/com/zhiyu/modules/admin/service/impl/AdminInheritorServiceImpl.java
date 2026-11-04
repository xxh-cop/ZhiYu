package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.admin.dto.InheritorUpdateDTO;
import com.zhiyu.modules.admin.service.AdminInheritorService;
import com.zhiyu.modules.admin.vo.InheritorDetailVO;
import com.zhiyu.modules.admin.vo.InheritorListItemVO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link AdminInheritorService} 实现（P1-B1）。
 *
 * <p>复用 {@link InheritorProfileMapper} 和 {@link UserMapper}。
 * JOIN user 取 nickname 用先查 inheritor_profile 再批量查 user 的方式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInheritorServiceImpl implements AdminInheritorService {

    private final InheritorProfileMapper inheritorProfileMapper;
    private final UserMapper userMapper;
    private final com.zhiyu.modules.notification.service.NotificationService notificationService;

    @Override
    public PageVO<InheritorListItemVO> listInheritors(Integer page, Integer pageSize, Integer verified, String keyword) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

        LambdaQueryWrapper<InheritorProfile> wrapper = new LambdaQueryWrapper<>();
        if (verified != null) {
            wrapper.eq(InheritorProfile::getVerified, verified);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(InheritorProfile::getSkill, keyword.trim());
        }
        wrapper.orderByDesc(InheritorProfile::getCreateTime);

        IPage<InheritorProfile> result = inheritorProfileMapper.selectPage(new Page<>(pageNum, size), wrapper);

        // 批量查 user 取 nickname
        Set<Long> userIds = result.getRecords().stream()
                .map(InheritorProfile::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = Map.of();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            nicknameMap = users.stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
        }

        final Map<Long, String> finalNicknameMap = nicknameMap;
        List<InheritorListItemVO> records = result.getRecords().stream()
                .map(p -> {
                    InheritorListItemVO vo = new InheritorListItemVO();
                    vo.setId(p.getId());
                    vo.setUserId(p.getUserId());
                    vo.setNickname(finalNicknameMap.getOrDefault(p.getUserId(), ""));
                    vo.setSkill(p.getSkill());
                    vo.setHonor(p.getHonor());
                    vo.setVerified(p.getVerified());
                    vo.setCreateTime(p.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), pageNum, size, records);
    }

    @Override
    public InheritorDetailVO getInheritor(Long id) {
        InheritorProfile profile = inheritorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return toDetailVO(profile);
    }

    @Override
    public void verifyInheritor(Long id, Integer verified) {
        InheritorProfile profile = inheritorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (verified == null || (verified != 0 && verified != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "verified 只能为 0 或 1");
        }
        profile.setVerified(verified);
        inheritorProfileMapper.updateById(profile);
        log.info("管理员认证传承人: inheritorId={}, verified={}, operator={}", id, verified, UserContext.getUserId());
        // BG1（2026-11-05）：核验结果触达传承人本人；失败不阻断核验动作
        try {
            notificationService.notifyInheritorVerify(profile.getUserId(), id, verified == 1);
        } catch (Exception e) {
            log.warn("传承人核验通知写入失败（不阻断核验）: inheritorId={}, err={}", id, e.getMessage());
        }
    }

    @Override
    public InheritorDetailVO updateInheritor(Long id, InheritorUpdateDTO dto) {
        InheritorProfile profile = inheritorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (dto.getSkill() != null) {
            profile.setSkill(dto.getSkill());
        }
        if (dto.getIntro() != null) {
            profile.setIntro(dto.getIntro());
        }
        if (dto.getHonor() != null) {
            profile.setHonor(dto.getHonor());
        }
        inheritorProfileMapper.updateById(profile);
        log.info("管理员编辑传承人: inheritorId={}, operator={}", id, UserContext.getUserId());
        return toDetailVO(profile);
    }

    private InheritorDetailVO toDetailVO(InheritorProfile profile) {
        InheritorDetailVO vo = new InheritorDetailVO();
        vo.setId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setSkill(profile.getSkill());
        vo.setIntro(profile.getIntro());
        vo.setHonor(profile.getHonor());
        vo.setVerified(profile.getVerified());
        vo.setCreateTime(profile.getCreateTime());
        // 查关联 user
        User user = userMapper.selectById(profile.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setSchool(user.getSchool());
        } else {
            vo.setNickname("");
            vo.setSchool(null);
        }
        return vo;
    }
}
