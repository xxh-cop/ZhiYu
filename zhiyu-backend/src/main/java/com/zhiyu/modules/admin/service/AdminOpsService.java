package com.zhiyu.modules.admin.service;

import com.zhiyu.security.BusinessAccess;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 管理端只读运行健康与备份清单；不执行备份、恢复或外部 AI 调用。 */
@Service
public class AdminOpsService {

    private static final List<String> CORE_TABLES = List.of(
            "user", "emotion_session", "emotion_message", "ai_audit_log", "practice_session",
            "heritage_catalog_version", "runtime_config", "program_booking", "learning_program",
            "work", "wellbeing_record", "care_reminder", "admin_operation_log", "user_ai_memory", "ai_trace");

    private final JdbcTemplate db;
    private final DataSource dataSource;
    private final BusinessAccess access;
    private final Environment env;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ThreadPoolTaskExecutor sseExecutor;

    public AdminOpsService(JdbcTemplate db, DataSource dataSource, BusinessAccess access,
                           Environment env, @Nullable StringRedisTemplate redisTemplate,
                           @Qualifier("sseExecutor") ThreadPoolTaskExecutor sseExecutor) {
        this.db = db;
        this.dataSource = dataSource;
        this.access = access;
        this.env = env;
        this.redisTemplate = redisTemplate;
        this.sseExecutor = sseExecutor;
    }

    public record Component(String key, String label, String status, String detail, Long latencyMs) {
    }

    public record Counts(long aiAudit24h, long blocked24h, long adminOps24h,
                         long practiceSessions, long heritageVersions) {
    }

    public record Jvm(long uptimeSeconds, long heapUsedMb, long heapMaxMb) {
    }

    public record Health(String generatedAt, String overall, List<Component> components,
                         Counts counts, Jvm jvm) {
    }

    public record TableCount(String table, long rows, boolean present) {
    }

    public record BackupManifest(String generatedAt, int totalTables, int presentTables,
                                 List<TableCount> tables, List<String> missingTables) {
    }

    public Health health() {
        access.role(3);
        List<Component> components = new ArrayList<>();
        boolean dbUp = true;
        long dbStart = System.nanoTime();
        try {
            db.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            dbUp = false;
        }
        long dbLatency = (System.nanoTime() - dbStart) / 1_000_000;
        components.add(new Component("database", "MySQL", dbUp ? "UP" : "DOWN",
                dbUp ? "连接正常" : "数据库连接失败，请检查服务与网络", dbLatency));

        Set<String> present = dbUp ? presentTables() : Set.of();
        List<String> missing = CORE_TABLES.stream().filter(table -> !present.contains(table)).toList();
        components.add(new Component("schema", "数据库结构", dbUp && missing.isEmpty() ? "UP" : "DOWN",
                !dbUp ? "数据库不可用，无法检查结构"
                        : missing.isEmpty() ? "关键表齐全（" + CORE_TABLES.size() + " 张）"
                        : "缺少表：" + String.join("、", missing), null));

        components.add(redisComponent());
        components.add(uploadComponent());
        components.add(ragComponent());
        components.add(aiComponent());
        components.add(ssePoolComponent());
        components.add(jvmComponent());

        boolean down = components.stream().anyMatch(c -> "DOWN".equals(c.status()));
        boolean warn = components.stream().anyMatch(c -> "WARN".equals(c.status()));
        String overall = down ? "DOWN" : warn ? "DEGRADED" : "UP";

        Counts counts = dbUp ? counts() : new Counts(0, 0, 0, 0, 0);
        Runtime runtime = Runtime.getRuntime();
        Jvm jvm = new Jvm(ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                runtime.maxMemory() / (1024 * 1024));
        return new Health(LocalDateTime.now().toString(), overall, components, counts, jvm);
    }

    public BackupManifest backupManifest() {
        access.role(3);
        Set<String> present = presentTables();
        List<TableCount> tables = CORE_TABLES.stream()
                .map(table -> new TableCount(table, present.contains(table) ? tableCount(table) : 0,
                        present.contains(table)))
                .toList();
        List<String> missing = tables.stream().filter(t -> !t.present()).map(TableCount::table).toList();
        return new BackupManifest(LocalDateTime.now().toString(), CORE_TABLES.size(),
                CORE_TABLES.size() - missing.size(), tables, missing);
    }

    private Component redisComponent() {
        if (redisTemplate == null) {
            return new Component("redis", "Redis", "WARN", "未配置 Redis 客户端，熔断/心流使用内存降级", null);
        }
        long start = System.nanoTime();
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> {
                connection.ping();
                return "PONG";
            });
            return new Component("redis", "Redis", "UP", "连接正常；用于共享熔断与心流状态",
                    (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            return new Component("redis", "Redis", "WARN", "不可达，应用会使用内存降级",
                    (System.nanoTime() - start) / 1_000_000);
        }
    }

    private Component uploadComponent() {
        try {
            Path path = Path.of(env.getProperty("zhiyu.file.upload-dir", "./uploads"));
            if (!Files.exists(path)) {
                return new Component("uploads", "媒体目录", "WARN", "目录尚不存在，首次上传时创建", null);
            }
            if (!Files.isDirectory(path) || !Files.isWritable(path)) {
                return new Component("uploads", "媒体目录", "DOWN", "目录不可写，请检查权限", null);
            }
            return new Component("uploads", "媒体目录", "UP", "目录存在且可写（未扫描文件内容）", null);
        } catch (Exception e) {
            return new Component("uploads", "媒体目录", "DOWN", "目录配置无效", null);
        }
    }

    private Component ragComponent() {
        try {
            Path path = Path.of(env.getProperty("zhiyu.ai.rag.vector-store-path", "./rag-store.json"))
                    .toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return new Component("rag", "RAG 本地存储", "UP",
                        "文件存在，" + Math.max(1, Files.size(path) / 1024) + " KB（路径：" + path
                                + "；未校验向量内容）", null);
            }
            return new Component("rag", "RAG 本地存储", "WARN", "尚未构建，首次运行按需创建", null);
        } catch (Exception e) {
            return new Component("rag", "RAG 本地存储", "WARN", "路径配置无效，检索将按需回退", null);
        }
    }

    private Component aiComponent() {
        String chatKey = env.getProperty("spring.ai.openai.api-key", "");
        boolean chatExternal = chatKey != null && !chatKey.isBlank() && !"sk-placeholder".equals(chatKey);
        String embeddingKey = env.getProperty("zhiyu.ai.doubao.api-key", "");
        boolean embedding = embeddingKey != null && !embeddingKey.isBlank() && !"placeholder".equals(embeddingKey);
        String detail = (chatExternal ? "LLM 已配置" : "LLM 未配置，使用 Mock 降级")
                + "；向量 " + (embedding ? "已配置" : "未配置")
                + "；未发起外部调用";
        return new Component("ai", "AI 服务配置", chatExternal ? "UP" : "WARN", detail, null);
    }

    /** SSE 线程池排队可观测（2026-11-03）：只暴露计数，不含任何用户与密钥信息。 */
    private Component ssePoolComponent() {
        try {
            java.util.concurrent.ThreadPoolExecutor pool = sseExecutor.getThreadPoolExecutor();
            int active = pool.getActiveCount();
            int poolSize = pool.getPoolSize();
            int max = pool.getMaximumPoolSize();
            int queueSize = pool.getQueue().size();
            int queueRemaining = pool.getQueue().remainingCapacity();
            boolean saturated = active >= max;
            String detail = "活跃 " + active + " / 池大小 " + poolSize + "（上限 " + max + "）"
                    + "；排队 " + queueSize + "（余量 " + queueRemaining + "）"
                    + (saturated ? "；线程已打满，新请求将排队" : "");
            return new Component("ssePool", "SSE 线程池", saturated ? "WARN" : "UP", detail, null);
        } catch (Exception e) {
            return new Component("ssePool", "SSE 线程池", "WARN", "线程池状态读取失败", null);
        }
    }

    private Component jvmComponent() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new Component("jvm", "JVM", "UP",
                "运行 " + (uptimeSeconds / 60) + " 分钟；堆内存 " + used + " / " + max + " MB", null);
    }

    private Counts counts() {
        return new Counts(
                countSince("ai_audit_log", "create_time"),
                countSince("ai_audit_log", "create_time", "action='BLOCK'"),
                countSince("admin_operation_log", "create_time"),
                tableCount("practice_session"),
                tableCount("heritage_catalog_version"));
    }

    private long countSince(String table, String column) {
        return countSince(table, column, null);
    }

    private long countSince(String table, String column, String extraWhere) {
        try {
            String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " >= ?"
                    + (extraWhere == null ? "" : " AND " + extraWhere);
            Long value = db.queryForObject(sql, Long.class, LocalDateTime.now().minusHours(24));
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private long tableCount(String table) {
        try {
            Long value = db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private Set<String> presentTables() {
        Set<String> present = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String table : CORE_TABLES) {
                try (ResultSet rows = connection.getMetaData()
                        .getTables(connection.getCatalog(), null, table, null)) {
                    if (rows.next()) present.add(table.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            return Set.of();
        }
        return present;
    }
}
