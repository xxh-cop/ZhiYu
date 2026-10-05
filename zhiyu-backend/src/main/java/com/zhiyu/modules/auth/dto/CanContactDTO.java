package com.zhiyu.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改可联系状态入参，对应 {@code PUT /api/user/can-contact}。
 *
 * <p>仅用户本人可调，管理员不可代调。
 * 设为 1（允许联系）时，后端会校验用户已同意隐私政策。
 */
@Data
public class CanContactDTO {

    /** 0 不允许 1 允许心理咨询师联系 */
    @NotNull(message = "canContact 不能为空")
    private Integer canContact;
}
