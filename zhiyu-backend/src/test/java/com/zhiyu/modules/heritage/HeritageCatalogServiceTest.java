package com.zhiyu.modules.heritage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HeritageCatalogService} 单测（H1a Task 1）。
 *
 * <p>覆盖：UTF-8 只读加载 4 来源 / 4 工艺 / 24 纹样、ID 唯一、来源引用闭合、
 * palette 四色、family/variant 白名单，以及未登录时的 1002 拒绝。
 */
class HeritageCatalogServiceTest {

    private HeritageCatalogService newService() throws Exception {
        return new HeritageCatalogService(new ObjectMapper());
    }

    private HeritageCatalog loadAsStudent(HeritageCatalogService service) {
        UserContext.set(new LoginUser(1L, 0));
        try {
            return service.catalog();
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("登录后读到 4 个来源、4 门工艺、24 个原创纹样，引用闭合")
    void catalogLoadsFourTraditionsAndTwentyFourPatterns() throws Exception {
        var service = newService();
        UserContext.set(new LoginUser(1L, 0));
        try {
            var data = service.catalog();
            assertEquals(4, data.sources().size());
            assertEquals(4, data.traditions().size());
            assertEquals(24, data.patterns().size());
            assertEquals(24, data.patterns().stream().map(HeritageCatalog.Pattern::id).distinct().count());
            assertTrue(data.patterns().stream().allMatch(p -> "ORIGINAL_STUDY".equals(p.origin())));
            assertTrue(data.patterns().stream().allMatch(p -> data.traditions().stream()
                    .anyMatch(t -> t.id().equals(p.traditionId()))));
            assertTrue(data.patterns().stream().allMatch(p -> p.palette() != null && p.palette().size() == 4));
            assertTrue(data.patterns().stream().allMatch(p -> data.sources().stream()
                    .anyMatch(s -> s.id().equals(p.sourceIds().get(0)))));
            assertNotNull(data.disclaimer());
            assertTrue(data.disclaimer().contains("本系统不能替代专业心理咨询"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("未登录读取目录抛 BusinessException(1002)")
    void catalogRequiresLogin() throws Exception {
        var service = newService();
        UserContext.clear();
        var ex = assertThrows(BusinessException.class, service::catalog);
        assertEquals(1002, ex.getCode());
        assertEquals("请先登录", ex.getMessage());
    }

    @Test
    @DisplayName("重复 ID 触发启动校验失败")
    void validationRejectsDuplicateIds() throws Exception {
        var data = loadAsStudent(newService());
        var patterns = new ArrayList<>(data.patterns());
        // Replace rather than append so the count stays 24 and the ID uniqueness check is exercised.
        patterns.set(1, patterns.get(0));
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources(), data.traditions(), patterns);
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }

    @Test
    @DisplayName("来源引用不闭合触发启动校验失败")
    void validationRejectsDanglingSourceReference() throws Exception {
        var data = loadAsStudent(newService());
        var patterns = new ArrayList<>(data.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(first.id(), first.traditionId(), first.name(), first.family(),
                first.variant(), first.detail(), first.palette(), first.origin(), List.of("missing-source"),
                first.designNote(), first.learningFocus(), first.minutes(), first.level()));
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources(), data.traditions(), patterns);
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }

    @Test
    @DisplayName("palette 不是四色触发启动校验失败")
    void validationRejectsWrongPaletteSize() throws Exception {
        var data = loadAsStudent(newService());
        var patterns = new ArrayList<>(data.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(first.id(), first.traditionId(), first.name(), first.family(),
                first.variant(), first.detail(), List.of("#FFFFFF", "#000000", "#123456"), first.origin(),
                first.sourceIds(), first.designNote(), first.learningFocus(), first.minutes(), first.level()));
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources(), data.traditions(), patterns);
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }

    @Test
    @DisplayName("origin 不是 ORIGINAL_STUDY 触发启动校验失败")
    void validationRejectsNonOriginalOrigin() throws Exception {
        var data = loadAsStudent(newService());
        var patterns = new ArrayList<>(data.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(first.id(), first.traditionId(), first.name(), first.family(),
                first.variant(), first.detail(), first.palette(), "COPIED", first.sourceIds(),
                first.designNote(), first.learningFocus(), first.minutes(), first.level()));
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources(), data.traditions(), patterns);
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }

    @Test
    @DisplayName("detail 超出 4..12 触发启动校验失败")
    void validationRejectsInvalidDetail() throws Exception {
        var data = loadAsStudent(newService());
        var patterns = new ArrayList<>(data.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(first.id(), first.traditionId(), first.name(), first.family(),
                first.variant(), 3, first.palette(), first.origin(), first.sourceIds(),
                first.designNote(), first.learningFocus(), first.minutes(), first.level()));
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources(), data.traditions(), patterns);
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }

    @Test
    @DisplayName("family/variant 不在白名单触发启动校验失败")
    void validationRejectsUnknownFamilyVariant() throws Exception {
        var data = loadAsStudent(newService());
        var patterns = new ArrayList<>(data.patterns());
        var first = patterns.get(0);
        patterns.set(0, new HeritageCatalog.Pattern(first.id(), first.traditionId(), first.name(), first.family(),
                "not-a-variant", first.detail(), first.palette(), first.origin(), first.sourceIds(),
                first.designNote(), first.learningFocus(), first.minutes(), first.level()));
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources(), data.traditions(), patterns);
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }

    @Test
    @DisplayName("工艺或来源数量不足触发启动校验失败")
    void validationRejectsWrongCounts() throws Exception {
        var data = loadAsStudent(newService());
        var invalid = new HeritageCatalog(data.version(), data.disclaimer(), data.sources().subList(0, 3),
                data.traditions(), data.patterns());
        assertThrows(IllegalStateException.class, () -> HeritageCatalogService.validate(invalid));
    }
}
