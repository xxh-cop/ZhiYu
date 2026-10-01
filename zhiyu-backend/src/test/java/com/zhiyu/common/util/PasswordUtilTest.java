package com.zhiyu.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PasswordUtil} 单元测试：不依赖 Spring 容器与数据库。
 *
 * <p>覆盖点：哈希格式（标准 BCrypt、随机盐）、校验通过、校验失败与空值防御。
 */
@DisplayName("PasswordUtil：BCrypt 密码哈希工具")
class PasswordUtilTest {

    private static final String RAW = "stu12345";

    @Test
    @DisplayName("encode：产出标准 BCrypt 串（$2a$10$ 前缀、长度 60），且同一明文两次结果不同（随机盐）")
    void encode_产出标准Bcrypt串且加盐随机() {
        String first = PasswordUtil.encode(RAW);
        String second = PasswordUtil.encode(RAW);

        // 标准 BCrypt 串固定 60 字符，前缀 $2a$10$ 表示 2a 版本 + strength 10
        assertThat(first).startsWith("$2a$10$").hasSize(60);
        assertThat(second).startsWith("$2a$10$").hasSize(60);
        // 盐随机：同一明文两次哈希必须不同，否则说明没加盐
        assertThat(first).isNotEqualTo(second);
        // 哈希串里绝不能出现明文
        assertThat(first).doesNotContain(RAW);
    }

    @Test
    @DisplayName("matches：正确明文返回 true（两次独立哈希都可校验）")
    void matches_正确明文返回true() {
        String encoded = PasswordUtil.encode(RAW);

        assertThat(PasswordUtil.matches(RAW, encoded)).isTrue();
        // 换一次哈希，仍应校验通过（证明校验不依赖某一次的具体盐）
        assertThat(PasswordUtil.matches(RAW, PasswordUtil.encode(RAW))).isTrue();
    }

    @Test
    @DisplayName("matches：错误明文、空串、null 参数一律返回 false（不抛异常）")
    void matches_错误或空值返回false() {
        String encoded = PasswordUtil.encode(RAW);

        assertThat(PasswordUtil.matches("stu12344", encoded)).isFalse();
        assertThat(PasswordUtil.matches("", encoded)).isFalse();
        assertThat(PasswordUtil.matches(RAW, "")).isFalse();
        // 空值防御：任一入参为 null 都必须安全返回 false
        assertThat(PasswordUtil.matches(null, encoded)).isFalse();
        assertThat(PasswordUtil.matches(RAW, null)).isFalse();
        assertThat(PasswordUtil.matches(null, null)).isFalse();
    }
}
