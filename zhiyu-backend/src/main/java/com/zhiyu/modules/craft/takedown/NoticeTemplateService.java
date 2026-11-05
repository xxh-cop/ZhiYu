package com.zhiyu.modules.craft.takedown;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 下架/归档提示语模板服务（2026-11-05 分层下架）。
 *
 * <p>模板外置 {@code resources/notices/<key>.md}（与 {@code prompts/*.st} 同纪律），
 * 代码只引用 {@code craft_project.notice_key}；读取显式 UTF-8，不依赖 file.encoding。
 *
 * <p>模板缺失不拖垮接口：渲染失败返回 {@code null} 并记 warn，
 * 调用方（listSteps）仍下发 noticeKey 供前端兜底。
 */
@Slf4j
@Service
public class NoticeTemplateService {

    /** key 白名单：小写字母/数字/中划线，防路径穿越 */
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9-]{1,64}");

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public NoticeTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 渲染模板：{placeholder} 占位符替换为 vars 中的值（缺省替换为空串）。
     *
     * @param key  notice_key（非法 key 或模板缺失返回 null）
     * @param vars 占位符值（title/deadline/exportEntry）
     * @return 渲染后文本；模板不可用返回 null
     */
    public String render(String key, Map<String, String> vars) {
        String template = load(key);
        if (template == null) {
            return null;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** 加载模板原文（带缓存）；key 非法或文件不存在返回 null。 */
    public String load(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return null;
        }
        return cache.computeIfAbsent(key, this::readResource);
    }

    private String readResource(String key) {
        Resource resource = resourceLoader.getResource("classpath:notices/" + key + ".md");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String text = reader.lines().collect(Collectors.joining("\n"));
            // 剥掉模板顶部的 HTML 注释（作者文档，如下发会泄露措辞红线等内部说明）
            return text.replaceAll("(?s)<!--.*?-->", "").stripLeading();
        } catch (Exception e) {
            log.warn("提示语模板不可用: key={}, err={}", key, e.getMessage());
            return null;
        }
    }
}
