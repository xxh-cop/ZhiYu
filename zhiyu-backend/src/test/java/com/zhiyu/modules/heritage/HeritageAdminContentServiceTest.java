package com.zhiyu.modules.heritage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageAdminContentService;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.security.BusinessAccess;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeritageAdminContentServiceTest {

    JdbcTemplate db;
    HeritageCatalogService catalogService;
    HeritageAdminContentService service;

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:heritageadmin" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
                "sa", "");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-h2.sql"));
        }
        db = new JdbcTemplate(ds);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                + "VALUES(1,'admin','hash','管理员',3,1,1,0),(2,'student','hash','学生',0,1,1,0)");
        var json = new ObjectMapper();
        catalogService = new HeritageCatalogService(json, db);
        service = new HeritageAdminContentService(db, json, new BusinessAccess(db), catalogService);
        admin();
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    private void admin() {
        UserContext.set(new LoginUser(1L, 3));
    }

    private void student() {
        UserContext.set(new LoginUser(2L, 0));
    }

    private HeritageCatalog editedCatalog(String learningFocus) {
        var base = catalogService.baseCatalog();
        var patterns = new ArrayList<>(base.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(
                first.id(), first.traditionId(), first.name(), first.family(), first.variant(),
                first.detail(), first.palette(), first.origin(), first.sourceIds(),
                first.designNote(), learningFocus, first.minutes(), first.level()));
        return new HeritageCatalog(base.version(), base.disclaimer(), base.sources(), base.traditions(), patterns);
    }

    @Test
    @DisplayName("初始快照来自内置目录，revision 为 0")
    void snapshotStartsFromBaseCatalog() {
        var snapshot = service.snapshot();
        assertEquals(0, snapshot.revision());
        assertEquals(24, snapshot.catalog().patterns().size());
        assertEquals("沿枝叶方向观察短线，在少量着色与较大留白之间做选择。",
                snapshot.catalog().patterns().get(0).learningFocus());
    }

    @Test
    @DisplayName("管理员保存后 revision 增加，学生端读取编辑后的目录")
    void saveMakesOverrideVisibleToStudent() {
        var saved = service.save(0, editedCatalog("新的学习观察点：先看留白。"));
        assertEquals(1, saved.revision());
        assertEquals("新的学习观察点：先看留白。", saved.catalog().patterns().get(0).learningFocus());
        student();
        assertEquals("新的学习观察点：先看留白。", catalogService.catalog().patterns().get(0).learningFocus());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM admin_operation_log", Integer.class));
    }

    @Test
    @DisplayName("过期 revision 返回 3001")
    void staleRevisionIsRejected() {
        service.save(0, editedCatalog("第一次保存"));
        var error = assertThrows(BusinessException.class,
                () -> service.save(0, editedCatalog("第二次保存")));
        assertEquals(3001, error.getCode());
    }

    @Test
    @DisplayName("非管理员不能查看或保存")
    void nonAdminIsForbidden() {
        student();
        assertEquals(1003, assertThrows(BusinessException.class, service::snapshot).getCode());
        assertEquals(1003, assertThrows(BusinessException.class,
                () -> service.save(0, catalogService.baseCatalog())).getCode());
    }

    @Test
    @DisplayName("恢复内置目录后学生端回到原始内容")
    void resetRestoresBaseCatalog() {
        service.save(0, editedCatalog("待恢复的编辑"));
        var reset = service.reset();
        assertEquals(0, reset.revision());
        student();
        assertEquals("沿枝叶方向观察短线，在少量着色与较大留白之间做选择。",
                catalogService.catalog().patterns().get(0).learningFocus());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM runtime_config WHERE config_key='heritage_catalog'", Integer.class));
    }

    @Test
    @DisplayName("非 https 来源不会保存")
    void invalidSourceUrlIsRejected() {
        var base = catalogService.baseCatalog();
        var sources = new ArrayList<>(base.sources());
        var first = sources.get(0);
        sources.set(0, new HeritageCatalog.Source(first.id(), first.title(), first.organization(),
                "http://example.com/source", first.accessedAt(), first.mediaReuse(), first.facts()));
        var invalid = new HeritageCatalog(base.version(), base.disclaimer(), sources, base.traditions(), base.patterns());
        var error = assertThrows(BusinessException.class, () -> service.save(0, invalid));
        assertEquals(1001, error.getCode());
    }

    @Test
    @DisplayName("非法色板不会保存")
    void invalidCatalogIsRejected() {
        var base = catalogService.baseCatalog();
        var patterns = new ArrayList<>(base.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(
                first.id(), first.traditionId(), first.name(), first.family(), first.variant(),
                first.detail(), List.of("#FFFFFF", "#111111", "#222222"), first.origin(),
                first.sourceIds(), first.designNote(), first.learningFocus(), first.minutes(), first.level()));
        var invalid = new HeritageCatalog(base.version(), base.disclaimer(), base.sources(), base.traditions(), patterns);
        var error = assertThrows(BusinessException.class, () -> service.save(0, invalid));
        assertEquals(1001, error.getCode());
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM runtime_config WHERE config_key='heritage_catalog'", Integer.class) == 0);
    }
}
