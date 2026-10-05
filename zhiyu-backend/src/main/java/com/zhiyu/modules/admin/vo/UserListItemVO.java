package com.zhiyu.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表项 VO（P1-A1）。
 *
 * <p>不包含 password 字段。
 */
@Data
public class UserListItemVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String school;
    private Integer role;
    private Integer status;
    private Integer canContact;
    private LocalDateTime createTime;
}
