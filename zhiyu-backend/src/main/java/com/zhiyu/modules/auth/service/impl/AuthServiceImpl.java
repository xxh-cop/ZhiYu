package com.zhiyu.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.enums.RoleEnum;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.service.AuthService;
import com.zhiyu.modules.auth.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link AuthService} 的 <b>T0.2b 阶段实现</b>：已接真实数据库与 BCrypt 校验，
 * 但 token 仍是占位值（真 JWT 在 T0.3）。
 *
 * <p>本类中的 token 字段<b>不得用于真实环境鉴权</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * T0.2b 占位 token，T0.3 换真 JWT 时删除。
     *
     * <p>刻意与 T0.1c 的取值不同，便于在冒烟测试里区分新旧实现。
     */
    private static final String T0_2B_MOCK_TOKEN = "mock-token-for-t0.2b";

    private final UserMapper userMapper;

    @Override
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setSchool(dto.getSchool());
        user.setRole(RoleEnum.STUDENT.getCode());
        user.setStatus(1);
        user.setCanContact(0);

        // 单表插入，依赖 MyBatis-Plus 自身的事务语义，暂不加 @Transactional；
        // 若后续扩展为多表写入（如同时建 group_member），再补事务注解。
        userMapper.insert(user);

        log.info("注册成功（T0.2b 已落库）userId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));

        // 刻意不区分「用户不存在」与「密码错误」，统一返回 2001，避免暴露用户是否存在的旁路信息。
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        LoginVO vo = new LoginVO();
        vo.setToken(T0_2B_MOCK_TOKEN);
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        // 角色码数值（Integer），与 sql/schema.sql 的 user.role 一致
        vo.setRole(user.getRole());

        log.info("登录成功（T0.2b 已校验 BCrypt）userId={}, username={}", user.getId(), user.getUsername());
        return vo;
    }
}
