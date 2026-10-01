package com.zhiyu.modules.auth.vo;

import lombok.Data;

/**
 * 登录成功返回体。
 *
 * <p>{@link #role} 是<b>角色码数值</b>，取值见 {@code AGENTS.md} §3.1
 * （0 学生 / 1 传承人 / 2 咨询师 / 3 管理员）。
 */
@Data
public class LoginVO {

    /** JWT；T0.1c 为 mock 值，T0.3 换成真实 token */
    private String token;

    /** 用户主键 */
    private Long userId;

    /** 展示用昵称 */
    private String nickname;

    /** 角色码：0 学生 / 1 传承人 / 2 咨询师 / 3 管理员 */
    private Integer role;
}
