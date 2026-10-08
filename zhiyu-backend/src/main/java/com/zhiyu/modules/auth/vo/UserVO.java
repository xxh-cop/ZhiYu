package com.zhiyu.modules.auth.vo;

import lombok.Data;

/**
 * 当前用户信息返回体（{@code GET /api/auth/me}，T0.3 JWT 就绪后启用）。
 *
 * <p>{@link #role} 是<b>角色码数值</b>，取值见 {@code AGENTS.md} §3.1
 * （0 学生 / 1 传承人 / 2 咨询师 / 3 管理员）。<b>不含密码字段</b>。
 */
@Data
public class UserVO {

    /** 用户主键 */
    private Long id;

    /** 登录名（学号或昵称） */
    private String username;

    /** 展示用昵称 */
    private String nickname;

    /** 学校名称 */
    private String school;

    /** 个人简介 */
    private String intro;

    /** 头像 URL */
    private String avatarUrl;

    /** 隐私政策是否同意：0 未同意 1 已同意 */
    private Integer privacyAgreed;

    /** 角色码：0 学生 / 1 传承人 / 2 咨询师 / 3 管理员 */
    private Integer role;
}
