package com.zhiyu.modules.user.service.impl;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.vo.UserVO;
import com.zhiyu.modules.user.dto.PasswordChangeDTO;
import com.zhiyu.modules.user.dto.UserProfileUpdateDTO;
import com.zhiyu.modules.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户个人资料服务实现（批次 4.5）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 只更新非 null 字段
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        if (dto.getSchool() != null) {
            user.setSchool(dto.getSchool());
        }
        if (dto.getIntro() != null) {
            user.setIntro(dto.getIntro());
        }

        userMapper.updateById(user);
        log.info("更新个人资料 userId={}", userId);

        return toUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 校验旧密码
        if (!PasswordUtil.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL);
        }

        // 更新为新密码
        user.setPassword(PasswordUtil.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("修改密码 userId={}", userId);
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setSchool(user.getSchool());
        vo.setIntro(user.getIntro());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPrivacyAgreed(user.getPrivacyAgreed());
        vo.setRole(user.getRole());
        return vo;
    }
}
