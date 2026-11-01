package com.zhiyu.modules.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** 材料包目录读取与校验；模板、工艺和来源必须能在现有目录中闭合。 */
@Service
public class MaterialKitService {

    private final MaterialKitCatalog catalog;

    public MaterialKitService(ObjectMapper json, HeritageCatalogService heritage,
                              WorkshopTemplateService workshopTemplates) throws IOException {
        try (var reader = new InputStreamReader(
                new ClassPathResource("catalog/material-kits.json").getInputStream(),
                StandardCharsets.UTF_8)) {
            this.catalog = json.readValue(reader, MaterialKitCatalog.class);
        }
        var templateIds = workshopTemplates.catalog().templates().stream()
                .map(WorkshopTemplateCatalog.WorkshopTemplate::id)
                .collect(java.util.stream.Collectors.toSet());
        var traditionIds = heritage.baseCatalog().traditions().stream()
                .map(com.zhiyu.modules.heritage.model.HeritageCatalog.Tradition::id)
                .collect(java.util.stream.Collectors.toSet());
        validate(catalog, templateIds, traditionIds);
    }

    public MaterialKitCatalog catalog() {
        return catalog;
    }

    static void validate(MaterialKitCatalog data, Set<String> templateIds, Set<String> traditionIds) {
        if (data == null || data.disclaimer() == null || data.disclaimer().isBlank()) {
            throw new IllegalStateException("材料包目录缺少免责声明");
        }
        if (data.kits() == null || data.kits().size() != 3) {
            throw new IllegalStateException("材料包必须为 3 份，实际: "
                    + (data.kits() == null ? 0 : data.kits().size()));
        }
        Set<String> ids = new HashSet<>();
        for (MaterialKitCatalog.MaterialKit kit : data.kits()) {
            if (kit.id() == null || !kit.id().matches("^[a-z0-9-]{3,60}$")) {
                throw new IllegalStateException("材料包 ID 不合法");
            }
            if (!ids.add(kit.id())) {
                throw new IllegalStateException("材料包 ID 重复: " + kit.id());
            }
            requireText(kit.name(), kit.id() + " 的名称");
            requireText(kit.audience(), kit.id() + " 的适用对象");
            requireText(kit.digitalAlternative(), kit.id() + " 的无材料替代方案");
            requireText(kit.sourceNote(), kit.id() + " 的来源说明");
            if (!templateIds.contains(kit.workshopTemplateId())) {
                throw new IllegalStateException("材料包 " + kit.id() + " 引用了未知工作坊模板: "
                        + kit.workshopTemplateId());
            }
            if (!traditionIds.contains(kit.traditionId())) {
                throw new IllegalStateException("材料包 " + kit.id() + " 引用了未知工艺: " + kit.traditionId());
            }
            if (kit.capacity() < 1 || kit.capacity() > 200) {
                throw new IllegalStateException("材料包 " + kit.id() + " 的建议人数必须在 1..200");
            }
            if (kit.items() == null || kit.items().size() < 4 || kit.items().size() > 10) {
                throw new IllegalStateException("材料包 " + kit.id() + " 必须包含 4 到 10 项材料");
            }
            for (MaterialKitCatalog.Item item : kit.items()) {
                requireText(item.name(), kit.id() + " 的材料名称");
                requireText(item.quantity(), kit.id() + " 的材料数量");
                requireText(item.note(), kit.id() + " 的材料说明");
            }
            requireList(kit.tools(), kit.id() + " 的工具清单", 1, 6);
            requireList(kit.preparation(), kit.id() + " 的准备步骤", 2, 6);
            requireList(kit.safety(), kit.id() + " 的安全提示", 1, 5);
            String text = kit.name() + kit.audience() + kit.sourceNote();
            if (text.contains("http://") || text.contains("https://") || text.contains("￥")
                    || text.contains("¥") || text.contains("元/")) {
                throw new IllegalStateException("材料包 " + kit.id() + " 不能包含采购链接或价格");
            }
        }
    }

    private static void requireList(java.util.List<String> values, String label, int min, int max) {
        if (values == null || values.size() < min || values.size() > max
                || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException(label + "数量不合法");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "不能为空");
        }
    }
}
