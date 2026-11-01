package com.zhiyu.modules.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 团体工作坊模板读取；只返回文案与准备清单，创建活动仍走 GrowthService 的字段校验。 */
@Service
public class WorkshopTemplateService {

    private final WorkshopTemplateCatalog catalog;

    public WorkshopTemplateService(ObjectMapper json) throws IOException {
        try (var reader = new InputStreamReader(
                new ClassPathResource("catalog/workshop-templates.json").getInputStream(),
                StandardCharsets.UTF_8)) {
            this.catalog = json.readValue(reader, WorkshopTemplateCatalog.class);
        }
        validate(catalog);
    }

    public WorkshopTemplateCatalog catalog() {
        return catalog;
    }

    static void validate(WorkshopTemplateCatalog data) {
        if (data == null || data.disclaimer() == null || data.disclaimer().isBlank()) {
            throw new IllegalStateException("工作坊模板缺少免责声明");
        }
        if (data.templates() == null || data.templates().size() != 3) {
            throw new IllegalStateException("工作坊模板必须为 3 条，实际: "
                    + (data.templates() == null ? 0 : data.templates().size()));
        }
        Set<String> ids = new HashSet<>();
        for (WorkshopTemplateCatalog.WorkshopTemplate template : data.templates()) {
            if (template.id() == null || !template.id().matches("^[a-z0-9-]{3,60}$")) {
                throw new IllegalStateException("工作坊模板 ID 不合法");
            }
            if (!ids.add(template.id())) {
                throw new IllegalStateException("工作坊模板 ID 重复: " + template.id());
            }
            requireText(template.title(), template.id() + " 的标题");
            requireText(template.summary(), template.id() + " 的摘要");
            requireText(template.description(), template.id() + " 的活动说明");
            requireText(template.materials(), template.id() + " 的材料清单");
            requireText(template.safety(), template.id() + " 的安全提示");
            requireText(template.sourceNote(), template.id() + " 的来源说明");
            if (!"WORKSHOP".equals(template.kind())) {
                throw new IllegalStateException("工作坊模板 " + template.id() + " 的类型必须为 WORKSHOP");
            }
            if (template.minutes() < 30 || template.minutes() > 240) {
                throw new IllegalStateException("工作坊模板 " + template.id() + " 的时长必须在 30..240 分钟");
            }
            if (template.capacity() < 1 || template.capacity() > 200) {
                throw new IllegalStateException("工作坊模板 " + template.id() + " 的名额必须在 1..200");
            }
            if (!template.description().contains("不能替代专业心理咨询")) {
                throw new IllegalStateException("工作坊模板 " + template.id() + " 的活动说明必须包含免责声明");
            }
            if (template.tags() == null || template.tags().isEmpty() || template.tags().size() > 6
                    || template.tags().stream().anyMatch(tag -> tag == null || tag.isBlank())) {
                throw new IllegalStateException("工作坊模板 " + template.id() + " 的标签不合法");
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "不能为空");
        }
    }
}
