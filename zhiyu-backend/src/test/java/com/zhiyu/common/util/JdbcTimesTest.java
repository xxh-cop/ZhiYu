package com.zhiyu.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JdbcTimes} 单测（2026-11-07，联调 F1）。
 *
 * <p>覆盖驱动分叉本身：MySQL Connector/J 返回 {@link LocalDateTime}、H2 返回 {@link Timestamp}——
 * 958 条 H2 集成测试全绿却漏掉 MySQL 下的 CCE，缺口就在这里：必须有测试直接断言
 * 「两种驱动形态都被接受」，而不只是「H2 形态不炸」。
 */
class JdbcTimesTest {

    @Test
    @DisplayName("MySQL 驱动形态（LocalDateTime）原样返回")
    void mysqlLocalDateTime() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 13, 30, 0);
        assertSame(now, JdbcTimes.toLocalDateTime(now));
    }

    @Test
    @DisplayName("H2 驱动形态（Timestamp）转换等值")
    void h2Timestamp() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 13, 30, 0);
        assertEquals(now, JdbcTimes.toLocalDateTime(Timestamp.valueOf(now)));
    }

    @Test
    @DisplayName("null 与 java.util.Date 兜底；未知类型显式拒绝（不静默吞驱动行为变化）")
    void edgeCases() {
        assertNull(JdbcTimes.toLocalDateTime(null));
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 13, 30, 0);
        assertEquals(now, JdbcTimes.toLocalDateTime(new java.util.Date(Timestamp.valueOf(now).getTime())));
        assertThrows(IllegalArgumentException.class, () -> JdbcTimes.toLocalDateTime("2026-11-07 13:30:00"));
    }
}
