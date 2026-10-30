package com.zhiyu.modules.heritage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 非遗目录读取服务。
 *
 * <p>内置目录仍从 classpath {@code catalog/heritage-catalog.json} 以 UTF-8 加载并校验；
 * 管理端保存的目录覆盖存放在现有 {@code runtime_config} 的 {@code heritage_catalog} 键中。
 * 学生端优先读取覆盖内容，没有覆盖时回退到内置目录。校验失败的内置目录会阻止启动。
 */
@Slf4j
@Service
public class HeritageCatalogService {

    /** family → 允许的 variant 组合；与 catalog JSON 的原创几何族一一对应。 */
    private static final Map<String, List<String>> ALLOWED_VARIANTS = Map.of(
            "stitch", List.of("sprig", "petals", "arcs", "grid", "seed", "ribbon"),
            "weave", List.of("plain", "twill", "basket", "chevron", "diamond", "window"),
            "cutout", List.of("bloom", "leaves", "window", "fan", "stars", "double"),
            "porcelain", List.of("medallion", "border", "sprig", "wave", "lattice", "petal"));

    /** 供共创服务复用同一白名单，避免 AI 变体与几何核心出现两套枚举。 */
    public static List<String> variantsFor(String family) {
        return ALLOWED_VARIANTS.getOrDefault(family, List.of());
    }

    private final HeritageCatalog catalog;
    private final ObjectMapper json;
    private final JdbcTemplate db;

    public HeritageCatalogService(ObjectMapper json) throws IOException {
        this(json, null);
    }

    @Autowired
    public HeritageCatalogService(ObjectMapper json, @Nullable JdbcTemplate db) throws IOException {
        HeritageCatalog loaded;
        try (var reader = new InputStreamReader(
                new ClassPathResource("catalog/heritage-catalog.json").getInputStream(),
                StandardCharsets.UTF_8)) {
            loaded = json.readValue(reader, HeritageCatalog.class);
        }
        validate(loaded);
        this.catalog = loaded;
        this.json = json;
        this.db = db;
    }

    /** 内置目录快照，供管理端初始读取与“恢复内置”使用。 */
    public HeritageCatalog baseCatalog() {
        return catalog;
    }

    /** 登录学生读取目录：优先管理端已保存的覆盖，否则内置目录。 */
    public HeritageCatalog catalog() {
        if (UserContext.getUserId() == null) {
            throw new BusinessException(1002, "请先登录");
        }
        HeritageCatalog override = overrideCatalog();
        return override == null ? catalog : override;
    }

    private HeritageCatalog overrideCatalog() {
        if (db == null) return null;
        try {
            var rows = db.queryForList(
                    "SELECT payload FROM runtime_config WHERE config_key=? AND deleted=0", "heritage_catalog");
            if (rows.isEmpty()) return null;
            var parsed = json.readValue(rows.get(0).get("payload").toString(), HeritageCatalog.class);
            validate(parsed);
            return parsed;
        } catch (Exception e) {
            log.warn("非遗目录覆盖读取失败，回退内置目录: {}", e.getMessage());
            return null;
        }
    }

    /** 校验目录结构：4 来源 / 4 工艺 / 24 纹样、ID 唯一、引用闭合、四色、白名单组合。 */
    public static void validate(HeritageCatalog data) {
        if (data == null) {
            throw new IllegalStateException("非遗目录为空");
        }
        if (data.sources() == null || data.sources().size() != 4) {
            throw new IllegalStateException("非遗目录必须包含 4 个来源，实际: " + sizeOf(data.sources()));
        }
        if (data.traditions() == null || data.traditions().size() != 4) {
            throw new IllegalStateException("非遗目录必须包含 4 门工艺，实际: " + sizeOf(data.traditions()));
        }
        if (data.patterns() == null || data.patterns().size() != 24) {
            throw new IllegalStateException("非遗目录必须包含 24 个纹样，实际: " + sizeOf(data.patterns()));
        }

        Set<String> sourceIds = uniqueIds("来源", data.sources().stream()
                .map(HeritageCatalog.Source::id).toList());
        Set<String> traditionIds = uniqueIds("工艺", data.traditions().stream()
                .map(HeritageCatalog.Tradition::id).toList());
        uniqueIds("纹样", data.patterns().stream().map(HeritageCatalog.Pattern::id).toList());

        for (var source : data.sources()) {
            if (isBlank(source.title()) || isBlank(source.organization())
                    || isBlank(source.url()) || isBlank(source.accessedAt())
                    || isBlank(source.mediaReuse())) {
                throw new IllegalStateException("来源 " + source.id() + " 的标题、机构、链接、日期和媒体许可不能为空");
            }
            if (!source.url().startsWith("https://")) {
                throw new IllegalStateException("来源 " + source.id() + " 的链接必须以 https:// 开头");
            }
            try {
                java.time.LocalDate.parse(source.accessedAt());
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalStateException("来源 " + source.id() + " 的访问日期必须是 YYYY-MM-DD");
            }
            if (!Set.of("NOT_GRANTED", "GRANTED").contains(source.mediaReuse())) {
                throw new IllegalStateException("来源 " + source.id() + " 的媒体许可状态不合法");
            }
            if (source.facts() == null || source.facts().isEmpty()
                    || source.facts().stream().anyMatch(HeritageCatalogService::isBlank)) {
                throw new IllegalStateException("来源 " + source.id() + " 必须至少有一条已核实事实");
            }
        }
        for (var tradition : data.traditions()) {
            requireKnownSources("工艺 " + tradition.id(), tradition.sourceIds(), sourceIds);
        }
        for (var pattern : data.patterns()) {
            if (pattern.traditionId() == null || !traditionIds.contains(pattern.traditionId())) {
                throw new IllegalStateException("纹样 " + pattern.id() + " 引用了未知工艺: " + pattern.traditionId());
            }
            requireKnownSources("纹样 " + pattern.id(), pattern.sourceIds(), sourceIds);
            if (!"ORIGINAL_STUDY".equals(pattern.origin())) {
                throw new IllegalStateException("纹样 " + pattern.id() + " 必须标记为原创教学再设计");
            }
            if (pattern.detail() == null || pattern.detail() < 4 || pattern.detail() > 12) {
                throw new IllegalStateException("纹样 " + pattern.id() + " 的 detail 必须在 4..12");
            }
            if (pattern.minutes() == null || pattern.minutes() <= 0) {
                throw new IllegalStateException("纹样 " + pattern.id() + " 的 minutes 必须为正整数");
            }
            if (pattern.palette() == null || pattern.palette().size() != 4
                    || pattern.palette().stream().anyMatch(color -> color == null || !color.matches("^#[0-9a-fA-F]{6}$"))) {
                throw new IllegalStateException("纹样 " + pattern.id() + " 的 palette 必须为四色");
            }
            List<String> variants = ALLOWED_VARIANTS.get(pattern.family());
            if (variants == null || !variants.contains(pattern.variant())) {
                throw new IllegalStateException("纹样 " + pattern.id() + " 的 family/variant 不在白名单: "
                        + pattern.family() + "/" + pattern.variant());
            }
        }
    }

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Set<String> uniqueIds(String label, List<String> ids) {
        Set<String> unique = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(label + "存在空 ID");
            }
            if (!unique.add(id)) {
                throw new IllegalStateException(label + "存在重复 ID: " + id);
            }
        }
        return unique;
    }

    private static void requireKnownSources(String label, List<String> refs, Set<String> knownSourceIds) {
        if (refs == null || refs.isEmpty()) {
            throw new IllegalStateException(label + " 必须引用至少一个来源");
        }
        for (String ref : refs) {
            if (!knownSourceIds.contains(ref)) {
                throw new IllegalStateException(label + " 引用了未登记来源: " + ref);
            }
        }
    }
}
