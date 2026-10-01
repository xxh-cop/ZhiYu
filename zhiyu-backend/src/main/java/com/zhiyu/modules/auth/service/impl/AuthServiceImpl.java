package com.zhiyu.modules.auth.service.impl;

import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.enums.RoleEnum;
import com.zhiyu.modules.auth.service.AuthService;
import com.zhiyu.modules.auth.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link AuthService} 的 <b>T0.1c 阶段实现</b>：不接数据库、不签真 JWT，
 * 只用于验证 Controller → Service → Result 链路能跑通。
 *
 * <p>本类中所有返回值都是硬编码的，<b>不得用于真实环境</b>。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** T0.1c 占位 token。 */
    private static final String MOCK_TOKEN = "mock-token-for-t0.1c";

    @Override
    public void register(RegisterDTO dto) {
        // TODO(T0.2): 接数据库 —— 校验用户名是否已存在（重复抛 BusinessException(2002)）、
        //             BCrypt 加密密码、写入 user 表，返回落库后的主键。
        // TODO(T0.3): 确认注册成功后是否需要直接签发 JWT（要以 openapi.yaml 的契约为准）。
        log.info("注册请求（T0.1c 硬编码，未持久化）username={}, nickname={}, school={}",
                dto.getUsername(), dto.getNickname(), dto.getSchool());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // TODO(T0.2): 接数据库 —— 按 username 查 user 表、用 BCrypt.matches 校验密码，
        //             失败一律抛 BusinessException(2001)，不区分“用户不存在/密码错”。
        // TODO(T0.3): 换真 JWT —— 签发 token，并把 userId 与 role 写进 claims。
        log.info("登录请求（T0.1c 硬编码，未校验密码）username={}", dto.getUsername());

        LoginVO vo = new LoginVO();
        vo.setToken(MOCK_TOKEN);
        vo.setUserId(1L);
        vo.setNickname("小织");
        // 角色码数值（0），与 sql/schema.sql 的 user.role 一致；避免裸魔法值
        vo.setRole(RoleEnum.STUDENT.getCode());
        return vo;
    }
}
