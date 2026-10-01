package com.zhiyu.modules.auth.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色，取值严格对应 {@code sql/schema.sql} 的 {@code user.role}：
 * 0 学生 / 1 传承人 / 2 咨询师 / 3 管理员。
 *
 * <p><b>全局唯一，不得新增、不得改数值。</b>
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    /** 学生 */
    STUDENT(0, "学生"),

    /** 传承人 */
    INHERITOR(1, "传承人"),

    /** 咨询师 */
    COUNSELOR(2, "咨询师"),

    /** 管理员 */
    ADMIN(3, "管理员");

    private final Integer code;
    private final String label;

    /**
     * 按角色码反查枚举项。
     *
     * <p>用于把 {@code user.role} 转成枚举；<b>查不到返回 {@code null}</b>，
     * 由调用方决定兜底策略。
     */
    public static RoleEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (RoleEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
