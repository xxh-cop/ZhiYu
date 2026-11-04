package com.zhiyu.modules.auth.service;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.PasswordForgotDTO;
import com.zhiyu.modules.auth.dto.PasswordResetDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.enums.RoleEnum;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.service.impl.AuthServiceImpl;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 账号安全单元测试（H2 + MySQL 模式）：登录失败锁定与忘记密码。
 *
 * <p>每个用例手工构造独立的 {@link LoginAttemptStore} / {@link PasswordResetTokenStore}
 * （内存模式，不依赖 Redis）与捕获通道，互不污染；数据库写入随事务回滚。
 * 覆盖 features/44-auth-account-security.feature：锁定、解锁、成功清零、
 * 不泄露账号存在性、令牌一次性、令牌过期、明文不落库、通道未配置。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("账号安全：登录失败锁定与忘记密码")
class AuthAccountSecurityTest {

    private static final String LOCKED_MESSAGE = "尝试次数过多，请稍后再试";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private LoginAttemptStore attemptStore;
    private PasswordResetTokenStore tokenStore;
    private CapturingChannel channel;
    private AuthServiceImpl service;

    @BeforeEach
    void setup() {
        attemptStore = new LoginAttemptStore(null, 5, 15, 15, "memory");
        tokenStore = new PasswordResetTokenStore(null, "memory");
        channel = new CapturingChannel();
        service = newService(1800);
    }

    private AuthServiceImpl newService(long tokenExpireSeconds) {
        return new AuthServiceImpl(userMapper, jwtUtil, attemptStore, tokenStore,
                channel, tokenExpireSeconds);
    }

    private User insertUser(String username, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(rawPassword));
        user.setNickname("安全测试");
        user.setRole(RoleEnum.STUDENT.getCode());
        user.setStatus(1);
        user.setCanContact(0);
        userMapper.insert(user);
        return user;
    }

    private LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private PasswordForgotDTO forgotDTO(String username) {
        PasswordForgotDTO dto = new PasswordForgotDTO();
        dto.setUsername(username);
        return dto;
    }

    private PasswordResetDTO resetDTO(String token, String newPassword) {
        PasswordResetDTO dto = new PasswordResetDTO();
        dto.setToken(token);
        dto.setNewPassword(newPassword);
        return dto;
    }

    /** 捕获通道：记录最后一次发送的令牌，等价于开发环境 log 通道的可测版本。 */
    private static final class CapturingChannel implements PasswordResetChannel {
        private String lastUsername;
        private String lastToken;
        private int sendCount;

        @Override
        public void sendResetToken(String username, String token) {
            this.lastUsername = username;
            this.lastToken = token;
            this.sendCount++;
        }
    }

    @Test
    @DisplayName("login：连续 5 次失败锁定，锁定期间正确密码也拒绝，解锁后正常登录")
    void login_连续失败锁定与解锁() {
        insertUser("u_lock", "secret123");

        // 前 4 次失败：默认文案
        for (int i = 0; i < 4; i++) {
            final String wrong = "wrong" + i;
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.login(loginDTO("u_lock", wrong)));
            assertThat(ex.getCode()).isEqualTo(2001);
            assertThat(ex.getMessage()).isEqualTo(ResultCode.BAD_CREDENTIAL.getMessage());
        }
        // 第 5 次失败：触发锁定，改提示
        BusinessException fifth = assertThrows(BusinessException.class,
                () -> service.login(loginDTO("u_lock", "wrong-final")));
        assertThat(fifth.getCode()).isEqualTo(2001);
        assertThat(fifth.getMessage()).isEqualTo(LOCKED_MESSAGE);
        assertThat(attemptStore.lockedRemainingSeconds("u_lock")).isGreaterThan(0);

        // 锁定期间：正确密码同样拒绝（同一错误码与文案，不泄露账号状态）
        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.login(loginDTO("u_lock", "secret123")));
        assertThat(locked.getCode()).isEqualTo(2001);
        assertThat(locked.getMessage()).isEqualTo(LOCKED_MESSAGE);

        // 锁定解除（到期等价于 store 清空）后可正常登录
        attemptStore.reset("u_lock");
        assertThat(service.login(loginDTO("u_lock", "secret123")).getToken()).isNotBlank();
    }

    @Test
    @DisplayName("login：不存在的账号同样计数与锁定，不泄露账号是否存在")
    void login_锁定不区分账号是否存在() {
        for (int i = 0; i < 5; i++) {
            final String wrong = "wrong" + i;
            assertThrows(BusinessException.class,
                    () -> service.login(loginDTO("u_nobody", wrong)));
        }
        // 不存在的账号也被锁定，且错误码与存在账号一致
        assertThat(attemptStore.lockedRemainingSeconds("u_nobody")).isGreaterThan(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginDTO("u_nobody", "whatever")));
        assertThat(ex.getCode()).isEqualTo(2001);
        assertThat(ex.getMessage()).isEqualTo(LOCKED_MESSAGE);
    }

    @Test
    @DisplayName("login：成功登录清空失败计数")
    void login_成功清空计数() {
        insertUser("u_clear", "secret123");

        for (int i = 0; i < 4; i++) {
            final String wrong = "wrong" + i;
            assertThrows(BusinessException.class,
                    () -> service.login(loginDTO("u_clear", wrong)));
        }
        // 成功一次后计数清零
        assertThat(service.login(loginDTO("u_clear", "secret123")).getToken()).isNotBlank();

        // 再失败 4 次仍未锁定；第 5 次才锁定，证明计数确实被清过
        for (int i = 0; i < 4; i++) {
            final String wrong = "again" + i;
            assertThrows(BusinessException.class,
                    () -> service.login(loginDTO("u_clear", wrong)));
        }
        assertThat(attemptStore.lockedRemainingSeconds("u_clear")).isZero();
        assertThrows(BusinessException.class,
                () -> service.login(loginDTO("u_clear", "again-final")));
        assertThat(attemptStore.lockedRemainingSeconds("u_clear")).isGreaterThan(0);
    }

    @Test
    @DisplayName("forgot+reset：happy path —— 申请令牌、重置成功、旧密码失效、新密码可登录")
    void reset_happyPath() {
        User user = insertUser("u_reset", "old-pass-1");

        service.requestPasswordReset(forgotDTO("u_reset"));
        assertThat(channel.sendCount).isEqualTo(1);
        assertThat(channel.lastUsername).isEqualTo("u_reset");
        assertThat(channel.lastToken).isNotBlank();

        // 明文不落库：用令牌明文直接查存储查不到（存的是哈希）
        assertThat(tokenStore.consume(channel.lastToken)).isNull();
        // 哈希能查到（重新申请前先把这条哈希也消费掉会干扰流程，改在重置后再验证一次性）

        service.resetPassword(resetDTO(channel.lastToken, "new-pass-2"));

        // 旧密码失效，新密码可登录
        BusinessException oldLogin = assertThrows(BusinessException.class,
                () -> service.login(loginDTO("u_reset", "old-pass-1")));
        assertThat(oldLogin.getCode()).isEqualTo(2001);
        assertThat(service.login(loginDTO("u_reset", "new-pass-2")).getToken()).isNotBlank();
        assertThat(user.getId()).isNotNull();
    }

    @Test
    @DisplayName("reset：令牌用后失效，复用返回 3001")
    void reset_令牌一次性() {
        insertUser("u_once", "old-pass-1");
        service.requestPasswordReset(forgotDTO("u_once"));
        String token = channel.lastToken;

        service.resetPassword(resetDTO(token, "new-pass-2"));

        BusinessException reuse = assertThrows(BusinessException.class,
                () -> service.resetPassword(resetDTO(token, "new-pass-3")));
        assertThat(reuse.getCode()).isEqualTo(3001);
        // 第二次重置没有生效
        assertThat(service.login(loginDTO("u_once", "new-pass-2")).getToken()).isNotBlank();
    }

    @Test
    @DisplayName("reset：令牌过期返回 3001")
    void reset_令牌过期() throws InterruptedException {
        AuthServiceImpl shortLived = newService(1);
        insertUser("u_expire", "old-pass-1");

        shortLived.requestPasswordReset(forgotDTO("u_expire"));
        String token = channel.lastToken;

        Thread.sleep(1200);

        BusinessException expired = assertThrows(BusinessException.class,
                () -> shortLived.resetPassword(resetDTO(token, "new-pass-2")));
        assertThat(expired.getCode()).isEqualTo(3001);
        // 密码未被改动
        assertThat(service.login(loginDTO("u_expire", "old-pass-1")).getToken()).isNotBlank();
    }

    @Test
    @DisplayName("reset：篡改的令牌返回 3001")
    void reset_伪造令牌() {
        insertUser("u_fake", "old-pass-1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resetPassword(resetDTO("f".repeat(48), "new-pass-2")));
        assertThat(ex.getCode()).isEqualTo(3001);
    }

    @Test
    @DisplayName("forgot：不存在的账号返回相同成功路径，不发放令牌")
    void forgot_不泄露账号是否存在() {
        service.requestPasswordReset(forgotDTO("u_nobody"));
        // 不发放令牌（通道零调用），调用方拿到的响应与存在账号一致（code=0）
        assertThat(channel.sendCount).isZero();
    }

    @Test
    @DisplayName("forgot：未配置发送通道时明确返回「该功能暂未开放」")
    void forgot_通道未配置() {
        AuthServiceImpl noChannel = new AuthServiceImpl(userMapper, jwtUtil,
                attemptStore, tokenStore, null, 1800);
        insertUser("u_nochannel", "old-pass-1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> noChannel.requestPasswordReset(forgotDTO("u_nochannel")));
        assertThat(ex.getCode()).isEqualTo(3001);
        assertThat(ex.getMessage()).contains("暂未开放");
    }

    @Test
    @DisplayName("reset：成功后清零该账号的登录失败锁定")
    void reset_清零登录锁定() {
        insertUser("u_relief", "old-pass-1");
        for (int i = 0; i < 5; i++) {
            final String wrong = "wrong" + i;
            assertThrows(BusinessException.class,
                    () -> service.login(loginDTO("u_relief", wrong)));
        }
        assertThat(attemptStore.lockedRemainingSeconds("u_relief")).isGreaterThan(0);

        service.requestPasswordReset(forgotDTO("u_relief"));
        service.resetPassword(resetDTO(channel.lastToken, "new-pass-2"));

        // 锁定已清，新密码直接可登录
        assertThat(attemptStore.lockedRemainingSeconds("u_relief")).isZero();
        assertThat(service.login(loginDTO("u_relief", "new-pass-2")).getToken()).isNotBlank();
    }
}
