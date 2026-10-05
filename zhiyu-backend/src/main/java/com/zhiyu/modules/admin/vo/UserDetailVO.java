package com.zhiyu.modules.admin.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户详情 VO（P1-A1）。
 *
 * <p>在 {@link UserListItemVO} 基础上增加 craftPreferences。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDetailVO extends UserListItemVO {

    /** 手作偏好标签，逗号分隔 */
    private String craftPreferences;
}
