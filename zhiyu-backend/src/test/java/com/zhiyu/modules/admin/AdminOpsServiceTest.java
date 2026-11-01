package com.zhiyu.modules.admin;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.admin.service.AdminOpsService;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOpsServiceTest {

    @TempDir
    Path temp;

    private JdbcTemplate db;
    private AdminOpsService service;

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:ops" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
                "sa", "");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-h2.sql"));
        }
        db = new JdbcTemplate(ds);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                + "VALUES(1,'admin','hash','管理员',3,1,1,0),(2,'student','hash','学生',0,1,1,0)");
        Files.createDirectories(temp.resolve("uploads"));
        Files.writeString(temp.resolve("rag-store.json"), "{}");
        var env = new MockEnvironment();
        env.setProperty("zhiyu.file.upload-dir", temp.resolve("uploads").toString());
        env.setProperty("zhiyu.rag.vector-store-path", temp.resolve("rag-store.json").toString());
        service = new AdminOpsService(db, ds, new BusinessAccess(db), env, null);
        UserContext.set(new LoginUser(1L, 3));
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    @DisplayName("健康检查包含数据库、结构、目录、RAG 与 JVM")
    void healthReportsComponents() {
        var health = service.health();
        assertTrue(health.overall().equals("DEGRADED") || health.overall().equals("UP"));
        var database = health.components().stream().filter(c -> "database".equals(c.key())).findFirst().orElseThrow();
        assertEquals("UP", database.status());
        var schema = health.components().stream().filter(c -> "schema".equals(c.key())).findFirst().orElseThrow();
        assertEquals("UP", schema.status());
        var uploads = health.components().stream().filter(c -> "uploads".equals(c.key())).findFirst().orElseThrow();
        assertEquals("UP", uploads.status());
        var rag = health.components().stream().filter(c -> "rag".equals(c.key())).findFirst().orElseThrow();
        assertEquals("UP", rag.status());
        var redis = health.components().stream().filter(c -> "redis".equals(c.key())).findFirst().orElseThrow();
        assertEquals("WARN", redis.status());
        assertEquals(0, health.counts().aiAudit24h());
        assertTrue(health.jvm().heapMaxMb() >= 1);
    }

    @Test
    @DisplayName("备份清单核对关键表与行数")
    void backupManifestCountsCoreTables() {
        var manifest = service.backupManifest();
        assertEquals(15, manifest.totalTables());
        assertEquals(15, manifest.presentTables());
        assertTrue(manifest.missingTables().isEmpty());
        var user = manifest.tables().stream().filter(t -> "user".equals(t.table())).findFirst().orElseThrow();
        assertEquals(2, user.rows());
        assertTrue(user.present());
    }

    @Test
    @DisplayName("非管理员不能读取健康或备份清单")
    void nonAdminIsForbidden() {
        UserContext.set(new LoginUser(2L, 0));
        assertEquals(1003, assertThrows(BusinessException.class, service::health).getCode());
        assertEquals(1003, assertThrows(BusinessException.class, service::backupManifest).getCode());
    }

    @Test
    @DisplayName("健康页不返回密钥内容")
    void healthDoesNotLeakSecrets() {
        var health = service.health();
        String text = health.toString();
        assertFalse(text.contains("sk-placeholder"));
        assertFalse(text.contains("api-key"));
        assertFalse(text.contains("password"));
    }
}
