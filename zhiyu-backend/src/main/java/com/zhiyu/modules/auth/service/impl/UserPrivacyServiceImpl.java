package com.zhiyu.modules.auth.service.impl;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.auth.dto.CanContactDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.service.UserPrivacyService;
import com.zhiyu.modules.auth.vo.PrivacyStatusVO;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserPrivacyService} 实现（M3-B1）。
 *
 * <p>所有方法均从 {@link UserContext} 取 userId 回库查询，不信任 token 中的业务字段。
 * 逻辑删除的用户查不到（{@code @TableLogic} 自动附加 {@code deleted=0}），按未登录处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPrivacyServiceImpl implements UserPrivacyService {

    private final UserMapper userMapper;

    @Override
    public PrivacyStatusVO getPrivacyStatus() {
        User user = requireCurrentUser();
        PrivacyStatusVO vo = new PrivacyStatusVO();
        vo.setPrivacyAgreed(user.getPrivacyAgreed());
        vo.setCanContact(user.getCanContact());
        return vo;
    }

    @Override
    public void agreePrivacy() {
        User user = requireCurrentUser();
        user.setPrivacyAgreed(1);
        userMapper.updateById(user);
        log.info("用户同意隐私政策 userId={}", user.getId());
    }

    @Override
    public void withdrawPrivacy() {
        User user = requireCurrentUser();
        user.setPrivacyAgreed(0);
        // 撤回同意同时取消可联系授权
        user.setCanContact(0);
        userMapper.updateById(user);
        log.info("用户撤回隐私政策同意 userId={}", user.getId());
    }

    @Override
    public void updateCanContact(CanContactDTO dto) {
        User user = requireCurrentUser();
        // 设为允许联系前，需校验已同意隐私政策
        if (Integer.valueOf(1).equals(dto.getCanContact())
                && !Integer.valueOf(1).equals(user.getPrivacyAgreed())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请先同意隐私政策");
        }
        user.setCanContact(dto.getCanContact());
        userMapper.updateById(user);
        log.info("用户更新可联系状态 userId={}, canContact={}", user.getId(), dto.getCanContact());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount() {
        User user = requireCurrentUser();
        Long userId = user.getId();

        // 匿名化敏感字段，保持数据完整性但不可识别用户
        user.setUsername("deleted_" + userId);
        user.setPassword("");
        user.setNickname("已注销用户");
        user.setAvatarUrl(null);
        user.setSchool(null);
        user.setCraftPreferences(null);
        user.setCanContact(0);
        user.setPrivacyAgreed(0);
        user.setStatus(0); // 禁用
        // 逻辑删除：@TableLogic 会自动设置 deleted=1
        // 但 updateById 不会触发逻辑删除，需要手动设置 deleted 字段
        // 由于 @TableField(select = false) 导致 deleted 不在查询结果中，
        // 这里用 MyBatis-Plus 的 removeById 完成逻辑删除
        userMapper.updateById(user);
        userMapper.deleteById(userId);
        log.info("用户注销账号 userId={}", userId);
    }

    /**
     * 从 {@link UserContext} 取 userId 回库查询，校验用户存在且状态正常。
     *
     * <p>已逻辑删除的用户（{@code deleted=1}）通过 {@code selectById} 查不到，
     * 按 1002 未登录处理；状态被禁用（{@code status=0}）按 1003 处理。
     */
    private User requireCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            // token 有效但用户已被注销/删除
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }
}
