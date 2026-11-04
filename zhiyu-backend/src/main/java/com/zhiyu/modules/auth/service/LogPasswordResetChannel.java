package com.zhiyu.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发专用发送通道：把重置令牌打印到服务端日志。
 *
 * <p><b>严禁在生产开启</b>：令牌明文出现在日志里等同明文口令泄露面。
 * 仅当 {@code zhiyu.auth.password-reset.channel=log} 时装配；
 * 生产应接入短信/邮件通道（新增一个条件装配的 {@link PasswordResetChannel} 实现）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "zhiyu.auth.password-reset.channel", havingValue = "log")
public class LogPasswordResetChannel implements PasswordResetChannel {

    @Override
    public void sendResetToken(String username, String token) {
        log.warn("[DEV ONLY] 密码重置令牌 username={}, token={}（限时有效、一次性；生产禁止开启 log 通道）",
                username, token);
    }
}
