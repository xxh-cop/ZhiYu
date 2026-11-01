package com.zhiyu.modules.inheritor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.inheritor.model.InheritorStoryCatalog;
import com.zhiyu.modules.inheritor.model.InheritorStoryCatalog.Section;
import com.zhiyu.modules.inheritor.model.InheritorStoryCatalog.Story;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 传承人故事读取与校验；故事是合成教学叙事，引用目录中的工艺与公开来源。 */
@Service
public class InheritorStoryService {

    private final InheritorStoryCatalog catalog;
    private final Map<String, String> traditionNames;
    private final Map<String, HeritageCatalog.Source> sources;

    public InheritorStoryService(ObjectMapper json, HeritageCatalogService heritage) throws IOException {
        try (var reader = new InputStreamReader(
                new ClassPathResource("catalog/inheritor-stories.json").getInputStream(),
                StandardCharsets.UTF_8)) {
            this.catalog = json.readValue(reader, InheritorStoryCatalog.class);
        }
        var base = heritage.baseCatalog();
        this.traditionNames = base.traditions().stream()
                .collect(Collectors.toMap(HeritageCatalog.Tradition::id, HeritageCatalog.Tradition::shortName));
        this.sources = base.sources().stream()
                .collect(Collectors.toMap(HeritageCatalog.Source::id, Function.identity()));
        validate(catalog, traditionNames.keySet(), sources.keySet());
    }

    public InheritorStoryCatalog catalog() {
        return catalog;
    }

    public List<Summary> list() {
        return catalog.stories().stream().map(this::summary).toList();
    }

    public Detail get(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(1004, "故事不存在");
        }
        Story story = catalog.stories().stream()
                .filter(item -> item.id().equals(id.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(1004, "故事不存在"));
        List<SourceRef> refs = story.sourceIds().stream()
                .map(sources::get)
                .map(source -> new SourceRef(source.id(), source.title(), source.organization(), source.url()))
                .toList();
        return new Detail(story.id(), story.title(), story.subtitle(), story.traditionId(),
                traditionNames.get(story.traditionId()), story.readingMinutes(), story.synthetic(),
                catalog.disclaimer(), story.sections(), story.quote(), story.boundary(),
                story.learningPrompt(), refs);
    }

    private Summary summary(Story story) {
        return new Summary(story.id(), story.title(), story.subtitle(), story.traditionId(),
                traditionNames.get(story.traditionId()), story.readingMinutes(), story.synthetic(),
                story.synopsis());
    }

    public record Summary(String id, String title, String subtitle, String traditionId,
                          String traditionName, int readingMinutes, boolean synthetic, String synopsis) {
    }

    public record Detail(String id, String title, String subtitle, String traditionId, String traditionName,
                         int readingMinutes, boolean synthetic, String disclaimer, List<Section> sections,
                         String quote, String boundary, String learningPrompt, List<SourceRef> sources) {
    }

    public record SourceRef(String id, String title, String organization, String url) {
    }

    static void validate(InheritorStoryCatalog data, Set<String> traditionIds, Set<String> sourceIds) {
        if (data == null || data.disclaimer() == null || data.disclaimer().isBlank()
                || !data.disclaimer().contains("合成") || !data.disclaimer().contains("不是真实")) {
            throw new IllegalStateException("传承人故事必须声明合成教学叙事，且不是真实人物访谈");
        }
        if (data.stories() == null || data.stories().size() != 3) {
            throw new IllegalStateException("传承人故事必须为 3 条，实际: "
                    + (data.stories() == null ? 0 : data.stories().size()));
        }
        Set<String> ids = new HashSet<>();
        for (Story story : data.stories()) {
            if (story.id() == null || !story.id().matches("^[a-z0-9-]{3,60}$")) {
                throw new IllegalStateException("传承人故事 ID 不合法");
            }
            if (!ids.add(story.id())) {
                throw new IllegalStateException("传承人故事 ID 重复: " + story.id());
            }
            requireText(story.title(), story.id() + " 的标题");
            requireText(story.subtitle(), story.id() + " 的副标题");
            requireText(story.synopsis(), story.id() + " 的摘要");
            requireText(story.quote(), story.id() + " 的引语");
            requireText(story.learningPrompt(), story.id() + " 的观察提示");
            requireText(story.boundary(), story.id() + " 的边界说明");
            if (!story.boundary().contains("合成") || !story.boundary().contains("非真实")) {
                throw new IllegalStateException("传承人故事 " + story.id() + " 的边界说明必须标注合成与非真实");
            }
            if (!traditionIds.contains(story.traditionId())) {
                throw new IllegalStateException("传承人故事 " + story.id() + " 引用了未知工艺: " + story.traditionId());
            }
            if (story.readingMinutes() < 2 || story.readingMinutes() > 6) {
                throw new IllegalStateException("传承人故事 " + story.id() + " 的阅读时长必须在 2..6 分钟");
            }
            if (story.sections() == null || story.sections().size() < 2 || story.sections().size() > 4) {
                throw new IllegalStateException("传承人故事 " + story.id() + " 必须包含 2 到 4 节");
            }
            for (Section section : story.sections()) {
                requireText(section.heading(), story.id() + " 的小节标题");
                requireText(section.body(), story.id() + " 的小节内容");
            }
            if (story.sourceIds() == null || story.sourceIds().isEmpty()
                    || story.sourceIds().stream().anyMatch(id -> !sourceIds.contains(id))) {
                throw new IllegalStateException("传承人故事 " + story.id() + " 引用了未知来源");
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "不能为空");
        }
    }
}
