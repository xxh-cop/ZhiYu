package com.zhiyu.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.enums.RoleEnum;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.vo.LoginVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AuthService} 单元测试（H2 + MySQL 模式）。
 *
 * <p>覆盖 T0.2b 落地的注册/登录行为：密码 BCrypt 落库、注册默认字段、
 * 用户名重复 2002、登录 2001（不区分用户不存在与密码错）与禁用账号 1003。
 *
 * <p>每个测试方法都在事务里执行并回滚，互不影响。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthService：注册与登录")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RegisterDTO registerDTO(String username, String password) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setNickname("小测");
        dto.setSchool("测试大学");
        return dto;
    }

    private LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    /** 直插一条已有用户，用于登录用例（绕过 AuthService，避免用例互相依赖）。 */
    private User insertUser(String username, String rawPassword, int status) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(rawPassword));
        user.setNickname("已有用户");
        user.setRole(RoleEnum.COUNSELOR.getCode());
        user.setStatus(status);
        user.setCanContact(0);
        userMapper.insert(user);
        return user;
    }

    @Test
    @DisplayName("register：成功落库 —— 存的是 BCrypt 哈希，role=0、status=1、can_contact=0")
    void register_成功落库且密码加密() {
        authService.register(registerDTO("u_reg_ok", "plain123"));

        User saved = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, "u_reg_ok"));
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNickname()).isEqualTo("小测");
        assertThat(saved.getSchool()).isEqualTo("测试大学");

        // 库里绝不能是明文密码
        assertThat(saved.getPassword()).isNotEqualTo("plain123");
        assertThat(saved.getPassword()).startsWith("$2a$");
        assertThat(PasswordUtil.matches("plain123", saved.getPassword())).isTrue();

        // 新注册用户一律是学生（role=0）、状态正常（status=1）
        assertThat(saved.getRole()).isEqualTo(RoleEnum.STUDENT.getCode());
        assertThat(saved.getStatus()).isEqualTo(1);
        // 咨询师联系用户须由用户单独授权，注册时默认关闭（AGENTS.md §5）
        assertThat(saved.getCanContact()).isZero();
    }

    @Test
    @DisplayName("register：用户名已存在 → BusinessException(2002)，且不会写入第二条记录")
    void register_用户名已存在_抛2002() {
        authService.register(registerDTO("u_reg_dup", "plain123"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(registerDTO("u_reg_dup", "other123")));
        assertThat(ex.getCode()).isEqualTo(2002);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE username = ?", Long.class, "u_reg_dup");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("login：凭据正确 → 返回数据库里的 userId / nickname / role")
    void login_凭据正确_返回用户信息() {
        User inserted = insertUser("u_login_ok", "secret123", 1);

        LoginVO vo = authService.login(loginDTO("u_login_ok", "secret123"));

        assertThat(vo.getUserId()).isEqualTo(inserted.getId());
        assertThat(vo.getNickname()).isEqualTo("已有用户");
        assertThat(vo.getRole()).isEqualTo(RoleEnum.COUNSELOR.getCode());
        // T0.2b 仍签发占位 token（真 JWT 在 T0.3 落地）；
        // 这里只断言非空，避免 T0.3 换实现时还要回来改测试
        assertThat(vo.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("login：密码错 / 用户不存在都抛 2001（不泄露账号是否存在）；账号禁用抛 1003")
    void login_凭据或状态异常_抛对应错误码() {
        insertUser("u_login_bad", "secret123", 1);
        insertUser("u_login_disabled", "secret123", 0);

        // 密码错
        BusinessException wrongPwd = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("u_login_bad", "wrong-password")));
        assertThat(wrongPwd.getCode()).isEqualTo(2001);

        // 用户不存在：错误码必须与「密码错」完全一致，否则会泄露账号是否注册过
        BusinessException noSuchUser = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("u_login_nobody", "secret123")));
        assertThat(noSuchUser.getCode()).isEqualTo(2001);
        assertThat(noSuchUser.getMessage()).isEqualTo(wrongPwd.getMessage());

        // 账号被禁用（status=0）：凭据正确也要拒绝
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("u_login_disabled", "secret123")));
        assertThat(disabled.getCode()).isEqualTo(1003);
    }
}
