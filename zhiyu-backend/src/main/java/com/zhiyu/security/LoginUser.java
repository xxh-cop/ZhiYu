package com.zhiyu.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已通过 JWT 校验的当前登录用户（只承载鉴权必需的最小信息）。
 *
 * <p>刻意只放 {@code userId} 与 {@code role}：token 里的业务字段一律<b>不可信</b>，
 * 需要昵称 / 学校等展示字段时，必须按 {@code userId} 回库查询
 * （见 {@code AuthServiceImpl.currentUser()}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户主键（{@code user.id}，应用侧雪花生成） */
    private Long userId;

    /** 角色码：0 学生 1 传承人 2 咨询师 3 管理员，见 AGENTS.md §3.1 */
    private Integer role;
}
