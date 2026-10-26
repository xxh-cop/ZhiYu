package com.zhiyu.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.admin.dto.UserCreateDTO;
import com.zhiyu.modules.admin.dto.UserUpdateDTO;
import com.zhiyu.modules.admin.service.AdminUserService;
import com.zhiyu.modules.admin.vo.UserDetailVO;
import com.zhiyu.modules.admin.vo.UserListItemVO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AdminUserService} 实现（P1-A1）。
 *
 * <p>复用 {@link UserMapper}（BaseMapper），不新建 Mapper。
 * 权限校验（role=3）由 {@link com.zhiyu.config.SecurityConfig} 在 Security 链完成，
 * 「不允许操作自己」的校验在 Service 层完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final com.zhiyu.modules.auth.service.AccountLifecycle lifecycle;
    private final UserMapper userMapper;
    private final InheritorProfileMapper inheritorProfileMapper;

    @Override
    public PageVO<UserListItemVO> listUsers(Integer page, Integer pageSize, Integer role, Integer status, String keyword) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        wrapper.orderByDesc(User::getCreateTime);

        IPage<User> result = userMapper.selectPage(new Page<>(pageNum, size), wrapper);

        List<UserListItemVO> records = result.getRecords().stream()
                .map(this::toListItemVO)
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), pageNum, size, records);
    }

    @Override
    public UserDetailVO getUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return toDetailVO(user);
    }

    @Override
    public UserDetailVO updateUser(Long id, UserUpdateDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getSchool() != null) {
            user.setSchool(dto.getSchool());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        userMapper.updateById(user);
        log.info("管理员编辑用户: userId={}, operator={}", id, UserContext.getUserId());

        return toDetailVO(user);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不允许操作自己的账号");
        }

        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "status 只能为 0 或 1");
        }

        if(status==0)lifecycle.suspend(id);
        user.setStatus(status);
        userMapper.updateById(user);
        log.info("管理员切换用户状态: userId={}, status={}, operator={}", id, status, currentUserId);
    }

    @Override
    public void updateRole(Long id, Integer role) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不允许修改自己的角色");
        }

        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        if (role == null || role < 0 || role > 3) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "role 只能为 0/1/2/3");
        }

        user.setRole(role);
        userMapper.updateById(user);
        log.info("管理员变更用户角色: userId={}, role={}, operator={}", id, role, currentUserId);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不允许删除自己的账号");
        }

        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        lifecycle.erase(id);
        userMapper.deleteById(id);
        log.info("管理员逻辑删除用户: userId={}, operator={}", id, currentUserId);
    }

    // ── 创建用户（批次 4）──

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateDTO dto) {
        // 1. 用户名查重
        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        // 2. 创建 User
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRole(dto.getRole());
        user.setStatus(1);
        user.setCanContact(0);
        user.setPrivacyAgreed(0);
        if (dto.getSchool() != null) {
            user.setSchool(dto.getSchool());
        }
        if (dto.getIntro() != null) {
            user.setIntro(dto.getIntro());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }

        userMapper.insert(user);
        Long newUserId = user.getId();

        // 3. role=1（传承人）→ 创建 InheritorProfile
        if (dto.getRole() == 1) {
            InheritorProfile profile = new InheritorProfile();
            profile.setUserId(newUserId);
            profile.setSkill(dto.getSkill() != null ? dto.getSkill() : "");
            profile.setIntro(dto.getIntro() != null ? dto.getIntro() : "");
            profile.setHonor(dto.getHonor() != null ? dto.getHonor() : "");
            profile.setVerified(0);
            inheritorProfileMapper.insert(profile);
        }

        log.info("管理员创建用户: userId={}, username={}, role={}, operator={}",
                newUserId, dto.getUsername(), dto.getRole(), UserContext.getUserId());

        return newUserId;
    }

    // ── 私有方法 ──

    private UserListItemVO toListItemVO(User user) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setSchool(user.getSchool());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCanContact(user.getCanContact());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    private UserDetailVO toDetailVO(User user) {
        UserDetailVO vo = new UserDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setSchool(user.getSchool());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCanContact(user.getCanContact());
        vo.setCreateTime(user.getCreateTime());
        vo.setCraftPreferences(user.getCraftPreferences());
        return vo;
    }
}
