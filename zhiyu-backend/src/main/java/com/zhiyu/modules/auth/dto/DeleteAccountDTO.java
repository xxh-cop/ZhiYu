package com.zhiyu.modules.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * 注销账号入参，对应 {@code POST /api/user/delete}。
 *
 * <p>需要二次确认（{@code confirm === true}），否则返回 1001。
 * 注销为逻辑删除 + 敏感字段匿名化，不物理删除数据。
 */
@Data
public class DeleteAccountDTO {

    /** 二次确认，必须为 true 才执行注销 */
    @AssertTrue(message = "请确认注销操作")
    private Boolean confirm;
}
