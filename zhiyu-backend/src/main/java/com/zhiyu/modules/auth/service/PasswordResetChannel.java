package com.zhiyu.modules.auth.service;

/**
 * 忘记密码令牌发送通道（2026-11-03 账号安全）。
 *
 * <p>通道可配置（{@code zhiyu.auth.password-reset.channel}）：
 * <ul>
 *   <li>{@code log} —— 打印到服务端日志，仅供本地开发（{@link LogPasswordResetChannel}）</li>
 *   <li>其他任何值 / 未配置 —— 无通道 Bean，申请重置时明确返回「该功能暂未开放」，
 *       不静默成功</li>
 * </ul>
 *
 * <p>生产环境接入短信/邮件通道时新增一个 {@code @ConditionalOnProperty} 实现即可，
 * 业务代码不变。令牌明文只允许出现在通道实现内，禁止落库或写入审计日志。
 */
public interface PasswordResetChannel {

    /**
     * 把一次性重置令牌送达用户。
     *
     * @param username 账号（用于定位接收人，不含密码）
     * @param token    重置令牌明文（30 分钟有效、一次性）
     */
    void sendResetToken(String username, String token);
}
