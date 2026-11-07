package com.zhiyu.common.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * JDBC 时间值统一转换（2026-11-07，联调 F1 修复）。
 *
 * <p><b>背景</b>：{@code JdbcTemplate.queryForList} 对 {@code DATETIME} 列的返回类型随驱动分叉——
 * MySQL Connector/J 8.0.23+ 按 JDBC 4.2 返回 {@link LocalDateTime}，H2 返回 {@link Timestamp}。
 * 业务代码按其中一种类型强转会在另一种驱动下 {@code ClassCastException}
 * （H2 单测全绿、MySQL 运行期 500 的实证见 docs/moderation-soft-tier-e2e-report-20261107.md F1）。
 *
 * <p><b>约定</b>：从 {@code Map<String, Object>} 行里取时间列一律走本类，禁止裸强转。
 */
public final class JdbcTimes {

    private JdbcTimes() {
    }

    /**
     * 把时间列值统一转为 {@link LocalDateTime}。
     *
     * @param value {@code queryForList} 行值：{@code null} / {@link LocalDateTime}（MySQL）/
     *              {@link Timestamp}（H2）/ {@link java.util.Date}（兜底）
     * @return 转换结果；入参 {@code null} 返回 {@code null}
     * @throws IllegalArgumentException 非预期类型（防静默吞掉驱动行为变化）
     */
    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date d) {
            return new Timestamp(d.getTime()).toLocalDateTime();
        }
        throw new IllegalArgumentException("不支持的时间列类型: " + value.getClass().getName());
    }
}
