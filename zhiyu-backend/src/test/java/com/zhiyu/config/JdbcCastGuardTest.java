package com.zhiyu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 源码闸口（构建期，与 scripts/check-jdbc-timestamp-cast.js 同规则）：生产代码禁止裸
 * {@code (Timestamp)} / {@code (java.sql.Timestamp)} 强转。
 *
 * <p>背景：{@code JdbcTemplate.queryForList} 对 DATETIME 列的返回类型随驱动分叉
 * （MySQL Connector/J 返回 {@code LocalDateTime}，H2 返回 {@code Timestamp}），
 * 裸强转在另一种驱动下 {@code ClassCastException}——H2 全绿、MySQL 运行期才炸，
 * 已三次实证（PersonalWorkService / SoftTierPoolService / ModerationShadowService）。
 * 时间列行值一律走 {@code com.zhiyu.common.util.JdbcTimes}。
 *
 * <p>匹配规则唯一实现于 {@link JdbcCastScan}（与 node 版逐字符同源；
 * 两版结论一致性由 {@link JdbcCastGateConsistencyTest} 双跑锁定）。
 * 范围：只扫 {@code src/main/java}（生产代码；测试只在 H2 运行）。
 * 唯一豁免：命中行同行注释 {@code // jdbc-cast-ok: <理由≥6字符>}，逐行锚定，
 * 不接受整目录/整文件跳过。已知边界：字符串字面量里的 "(Timestamp)" 两边都会命中——
 * 用豁免注解写明理由。
 */
class JdbcCastGuardTest {

    private static final Path SRC = Path.of("src", "main", "java");

    @Test
    @DisplayName("生产代码无裸 (Timestamp) 强转（命中即失败并打印文件与行号）")
    void noBareTimestampCastInMainSources() throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SRC)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                hits.addAll(JdbcCastScan.findViolations(
                        Files.readAllLines(file, StandardCharsets.UTF_8),
                        SRC.relativize(file).toString().replace('\\', '/')));
            }
        }
        assertEquals(List.of(), hits,
                "命中裸 (Timestamp) 强转（驱动分叉 ClassCastException 三次实证；"
                        + "请改用 com.zhiyu.common.util.JdbcTimes，确需保留加同行注释 jdbc-cast-ok: 理由）:\n"
                        + String.join("\n", hits));
    }
}
