package com.zhiyu.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户角色变更请求体（P1-A1）。
 *
 * <p>角色码：0 学生 / 1 传承人 / 2 咨询师 / 3 管理员（AGENTS.md §3.1）。
 */
@Data
public class UserRoleDTO {

    /** 角色码：0 学生 / 1 传承人 / 2 咨询师 / 3 管理员 */
    @NotNull(message = "role 不能为空")
    private Integer role;
}
