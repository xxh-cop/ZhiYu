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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeritageAdminContentServiceTest {

    private JdbcTemplate db;
    private HeritageCatalogService catalogService;
    private HeritageAdminContentService service;

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
    @DisplayName("初始工作流：草稿与已发布都来自内置目录")
    void workflowStartsFromBaseCatalog() {
        var workflow = service.workflow();
        assertFalse(workflow.draft().persisted());
        assertEquals(0, workflow.draft().revision());
        assertEquals(0, workflow.published().version());
        assertEquals(24, workflow.draft().catalog().patterns().size());
        assertFalse(workflow.changed());
        assertEquals(0, service.versions().size());
    }

    @Test
    @DisplayName("保存草稿不改变学生端，发布后才生效并生成版本")
    void draftIsPrivateUntilPublished() {
        var saved = service.saveDraft(0, editedCatalog("草稿里的新观察点"));
        assertTrue(saved.draft().persisted());
        assertTrue(saved.changed());
        assertEquals(1, saved.draft().revision());
        assertEquals(0, saved.published().version());
        student();
        assertEquals("沿枝叶方向观察短线，在少量着色与较大留白之间做选择。",
                catalogService.catalog().patterns().get(0).learningFocus());

        admin();
        var published = service.publish(1, "第一次内容发布");
        assertEquals(1, published.published().version());
        assertFalse(published.draft().persisted());
        assertFalse(published.changed());
        student();
        assertEquals("草稿里的新观察点", catalogService.catalog().patterns().get(0).learningFocus());

        admin();
        var versions = service.versions();
        assertEquals(1, versions.size());
        assertEquals("第一次内容发布", versions.get(0).note());
        assertEquals("管理员", versions.get(0).actorName());
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM admin_operation_log", Integer.class));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM runtime_config WHERE config_key='heritage_catalog_draft'", Integer.class));
    }

    @Test
    @DisplayName("过期草稿 revision 返回 3001，不覆盖他人草稿")
    void staleDraftRevisionIsRejected() {
        service.saveDraft(0, editedCatalog("第一位管理员的草稿"));
        var error = assertThrows(BusinessException.class,
                () -> service.saveDraft(0, editedCatalog("第二位管理员的草稿")));
        assertEquals(3001, error.getCode());
    }

    @Test
    @DisplayName("没有草稿或发布 revision 过期时不能发布")
    void publishRequiresCurrentDraft() {
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> service.publish(0, null)).getCode());
        service.saveDraft(0, editedCatalog("待发布"));
        assertEquals(3001, assertThrows(BusinessException.class,
                () -> service.publish(0, null)).getCode());
    }

    @Test
    @DisplayName("回滚生成新版本且学生端回到旧内容")
    void rollbackCreatesNewPublishedVersion() {
        service.saveDraft(0, editedCatalog("版本一的内容"));
        service.publish(1, "v1");
        service.saveDraft(0, editedCatalog("版本二的内容"));
        service.publish(1, "v2");
        student();
        assertEquals("版本二的内容", catalogService.catalog().patterns().get(0).learningFocus());

        admin();
        var rolled = service.rollback(1, null);
        assertEquals(3, rolled.published().version());
        student();
        assertEquals("版本一的内容", catalogService.catalog().patterns().get(0).learningFocus());

        admin();
        assertEquals(3, service.versions().size());
        assertEquals("回滚到 v1", service.versions().get(0).note());
        assertEquals("版本一的内容",
                service.versionDetail(3).catalog().patterns().get(0).learningFocus());
    }

    @Test
    @DisplayName("下架后学生端回到内置目录，历史版本保留")
    void unpublishRestoresBaseAndKeepsHistory() {
        service.saveDraft(0, editedCatalog("下架前内容"));
        service.publish(1, null);
        var unpublished = service.unpublish("演示结束");
        assertEquals(0, unpublished.published().version());
        assertFalse(unpublished.changed());
        student();
        assertEquals("沿枝叶方向观察短线，在少量着色与较大留白之间做选择。",
                catalogService.catalog().patterns().get(0).learningFocus());
        admin();
        assertEquals(1, service.versions().size());
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM runtime_config WHERE config_key='heritage_catalog'", Integer.class));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM runtime_config WHERE config_key='heritage_catalog_draft'", Integer.class));
    }

    @Test
    @DisplayName("放弃草稿后学生端与已发布内容都不受影响")
    void discardDraftKeepsPublished() {
        service.saveDraft(0, editedCatalog("已发布内容"));
        service.publish(1, null);
        service.saveDraft(0, editedCatalog("未发布草稿"));
        var discarded = service.discardDraft();
        assertFalse(discarded.draft().persisted());
        assertFalse(discarded.changed());
        student();
        assertEquals("已发布内容", catalogService.catalog().patterns().get(0).learningFocus());
    }

    @Test
    @DisplayName("恢复内置目录清空草稿与发布内容")
    void resetRestoresBaseCatalog() {
        service.saveDraft(0, editedCatalog("待恢复的编辑"));
        service.publish(1, null);
        var reset = service.reset();
        assertEquals(0, reset.published().version());
        student();
        assertEquals("沿枝叶方向观察短线，在少量着色与较大留白之间做选择。",
                catalogService.catalog().patterns().get(0).learningFocus());
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM runtime_config WHERE config_key IN "
                        + "('heritage_catalog','heritage_catalog_draft')", Integer.class));
    }

    @Test
    @DisplayName("非 https 来源与非法色板不会写入草稿")
    void invalidCatalogIsRejectedWithoutWriting() {
        var base = catalogService.baseCatalog();
        var sources = new ArrayList<>(base.sources());
        var first = sources.get(0);
        sources.set(0, new HeritageCatalog.Source(first.id(), first.title(), first.organization(),
                "http://example.com/source", first.accessedAt(), first.mediaReuse(), first.facts()));
        var invalid = new HeritageCatalog(base.version(), base.disclaimer(), sources,
                base.traditions(), base.patterns());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.saveDraft(0, invalid)).getCode());

        var patterns = new ArrayList<>(base.patterns());
        var pattern = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(
                pattern.id(), pattern.traditionId(), pattern.name(), pattern.family(), pattern.variant(),
                pattern.detail(), List.of("#FFFFFF", "#111111", "#222222"), pattern.origin(),
                pattern.sourceIds(), pattern.designNote(), pattern.learningFocus(),
                pattern.minutes(), pattern.level()));
        var invalidPalette = new HeritageCatalog(base.version(), base.disclaimer(), base.sources(),
                base.traditions(), patterns);
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.saveDraft(0, invalidPalette)).getCode());
        assertTrue(db.queryForList(
                "SELECT payload FROM runtime_config WHERE config_key='heritage_catalog_draft'").isEmpty());
    }

    @Test
    @DisplayName("非管理员不能读取或写入工作流")
    void nonAdminIsForbidden() {
        student();
        assertEquals(1003, assertThrows(BusinessException.class, service::workflow).getCode());
        assertEquals(1003, assertThrows(BusinessException.class, service::versions).getCode());
        assertEquals(1003, assertThrows(BusinessException.class,
                () -> service.saveDraft(0, catalogService.baseCatalog())).getCode());
        assertEquals(1003, assertThrows(BusinessException.class,
                () -> service.publish(1, null)).getCode());
    }

    @Test
    @DisplayName("不存在的版本返回 1004")
    void missingVersionReturns1004() {
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.rollback(99, null)).getCode());
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.versionDetail(99)).getCode());
    }
}
