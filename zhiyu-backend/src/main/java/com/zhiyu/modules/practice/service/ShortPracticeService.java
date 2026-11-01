package com.zhiyu.modules.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.practice.model.ShortPracticeCatalog;
import com.zhiyu.modules.practice.model.ShortPracticeCatalog.ShortPractice;
import com.zhiyu.modules.practice.model.ShortPracticeCatalog.Step;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 短练习目录读取与按时间/心情排序；内容在启动时校验，坏内容阻止启动。 */
@Service
public class ShortPracticeService {

    private static final Set<String> KINDS = Set.of("HERITAGE", "MINDFULNESS", "DRAWING");

    private final ShortPracticeCatalog catalog;

    public ShortPracticeService(ObjectMapper json, HeritageCatalogService heritage) throws IOException {
        try (var reader = new InputStreamReader(
                new ClassPathResource("catalog/short-practices.json").getInputStream(),
                StandardCharsets.UTF_8)) {
            this.catalog = json.readValue(reader, ShortPracticeCatalog.class);
        }
        validate(catalog, heritage.baseCatalog().patterns().stream()
                .map(com.zhiyu.modules.heritage.model.HeritageCatalog.Pattern::id)
                .collect(java.util.stream.Collectors.toSet()));
    }

    public ShortPracticeCatalog catalog() {
        return catalog;
    }

    /** 按时间接近度与心情匹配排序；不传条件时按分钟数从短到长。 */
    public List<ShortPractice> recommend(Integer minutes, String mood, String kind) {
        String normalizedKind = normalizeKind(kind);
        String normalizedMood = mood == null ? "" : mood.trim().toLowerCase(Locale.ROOT);
        return catalog.practices().stream()
                .filter(item -> normalizedKind == null || item.kind().equals(normalizedKind))
                .sorted(Comparator.comparingInt((ShortPractice item) -> score(item, minutes, normalizedMood))
                        .thenComparingInt(ShortPractice::minutes)
                        .thenComparing(ShortPractice::id))
                .toList();
    }

    public ShortPractice find(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(1004, "短练习不存在");
        }
        return catalog.practices().stream()
                .filter(item -> item.id().equals(id.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(1004, "短练习不存在"));
    }

    private static int score(ShortPractice item, Integer minutes, String mood) {
        int score = 0;
        if (minutes != null) {
            score += Math.abs(item.minutes() - minutes) * 10;
        }
        if (!mood.isEmpty() && item.moodTags().stream().noneMatch(tag -> tag.equalsIgnoreCase(mood))) {
            score += 4;
        }
        return score;
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) return null;
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(normalized)) {
            throw new BusinessException(1001, "不支持的短练习类型");
        }
        return normalized;
    }

    /** 校验 6–8 条、ID 唯一、3–5 步、时长和纹样引用闭合。 */
    static void validate(ShortPracticeCatalog data, Set<String> patternIds) {
        if (data == null || data.disclaimer() == null || data.disclaimer().isBlank()) {
            throw new IllegalStateException("短练习目录缺少免责声明");
        }
        if (data.practices() == null || data.practices().size() < 6 || data.practices().size() > 8) {
            throw new IllegalStateException("短练习目录必须包含 6 到 8 条，实际: "
                    + (data.practices() == null ? 0 : data.practices().size()));
        }
        Set<String> ids = new HashSet<>();
        for (ShortPractice item : data.practices()) {
            if (item.id() == null || !item.id().matches("^[a-z0-9-]{3,40}$")) {
                throw new IllegalStateException("短练习 ID 必须是小写字母、数字或连字符");
            }
            if (!ids.add(item.id())) {
                throw new IllegalStateException("短练习 ID 重复: " + item.id());
            }
            requireText(item.title(), "短练习 " + item.id() + " 的标题");
            requireText(item.subtitle(), "短练习 " + item.id() + " 的副标题");
            requireText(item.safety(), "短练习 " + item.id() + " 的安全提示");
            requireText(item.closing(), "短练习 " + item.id() + " 的收束语");
            if (!KINDS.contains(item.kind())) {
                throw new IllegalStateException("短练习 " + item.id() + " 的类型不合法: " + item.kind());
            }
            if (item.minutes() < 1 || item.minutes() > 15) {
                throw new IllegalStateException("短练习 " + item.id() + " 的分钟数必须在 1..15");
            }
            if (item.moodTags() == null || item.moodTags().isEmpty() || item.moodTags().size() > 6
                    || item.moodTags().stream().anyMatch(tag -> tag == null || tag.isBlank() || tag.length() > 16)) {
                throw new IllegalStateException("短练习 " + item.id() + " 的心情标签不合法");
            }
            if (item.materials() == null) {
                throw new IllegalStateException("短练习 " + item.id() + " 的材料清单不能为空数组");
            }
            if ("HERITAGE".equals(item.kind())) {
                if (item.patternId() == null || !patternIds.contains(item.patternId())) {
                    throw new IllegalStateException("短练习 " + item.id() + " 引用了未知纹样: " + item.patternId());
                }
            } else if (item.patternId() != null && !item.patternId().isBlank()) {
                throw new IllegalStateException("短练习 " + item.id() + " 不是非遗练习，不能引用纹样");
            }
            validateSteps(item);
        }
    }

    private static void validateSteps(ShortPractice item) {
        List<Step> steps = item.steps();
        if (steps == null || steps.size() < 3 || steps.size() > 5) {
            throw new IllegalStateException("短练习 " + item.id() + " 必须包含 3 到 5 步");
        }
        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            if (step == null || step.order() != index + 1) {
                throw new IllegalStateException("短练习 " + item.id() + " 的步骤顺序必须从 1 连续递增");
            }
            requireText(step.title(), "短练习 " + item.id() + " 第 " + (index + 1) + " 步标题");
            requireText(step.cue(), "短练习 " + item.id() + " 第 " + (index + 1) + " 步引导");
            if (step.seconds() < 10 || step.seconds() > 300) {
                throw new IllegalStateException("短练习 " + item.id() + " 第 " + (index + 1) + " 步时长必须在 10..300 秒");
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "不能为空");
        }
    }
}
