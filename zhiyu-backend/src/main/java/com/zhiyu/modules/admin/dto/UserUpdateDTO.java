package com.zhiyu.modules.admin.dto;

import lombok.Data;

/**
 * 用户编辑请求体（P1-A1）。
 *
 * <p>仅允许修改 nickname / school / avatarUrl，
 * 不允许通过此接口修改 role / status / password / canContact。
 * null 字段表示不修改（MyBatis-Plus updateById 默认忽略 null）。
 */
@Data
public class UserUpdateDTO {

    /** 昵称 */
    private String nickname;

    /** 所在学校 */
    private String school;

    /** 头像 URL */
    private String avatarUrl;
}
