package com.zhiyu.modules.heritage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 受控非遗纹样共创。
 *
 * <p>模型只能从当前纹样所在 family 的白名单变体、4..12 的 detail 和允许色板中选 3 个组合；
 * 输出必须是 JSON，越界或不可用一律回退到明确标注的内置变体。SVG/HTML/颜色文本不会被执行。
 */
@Service
public class HeritageCoCreateService {

    public record Request(String patternId, String theme, boolean consent) {
    }

    public record Variant(String id, String name, String family, String variant,
                          int detail, List<String> palette, String rationale) {
    }

    public record Result(List<Variant> variants, boolean generated, String message) {
    }

    private record Palette(String id, List<String> colors) {
    }

    private final AiClient ai;
    private final HeritageCatalogService catalogs;
    private final ObjectMapper json;
    private final String promptTemplate;

    public HeritageCoCreateService(
            AiClient ai,
            HeritageCatalogService catalogs,
            ObjectMapper json,
            @Value("classpath:prompts/heritage-co-create.st") Resource prompt) throws IOException {
        this.ai = ai;
        this.catalogs = catalogs;
        this.json = json;
        try (var input = prompt.getInputStream()) {
            this.promptTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public Result create(Request request) {
        if (request == null || request.patternId() == null || request.patternId().isBlank()) {
            throw new BusinessException(1001, "请选择纹样");
        }
        if (!request.consent()) {
            throw new BusinessException(1001, "请先同意本次 AI 共创授权");
        }
        String theme = request.theme() == null ? "" : request.theme().trim();
        if (theme.length() > 120) {
            throw new BusinessException(1001, "共创主题不得超过 120 字");
        }
        HeritageCatalog catalog = catalogs.catalog();
        HeritageCatalog.Pattern base = catalog.patterns().stream()
                .filter(pattern -> pattern.id().equals(request.patternId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(1004, "纹样不存在"));
        List<Palette> palettes = choosePalettes(catalog, base);
        String prompt = renderPrompt(base, palettes, theme);
        String raw = ai.heritageVariants(prompt, base.name() + "\n" + theme);
        List<Variant> parsed = parse(raw, base, palettes);
        if (parsed == null) {
            return new Result(fallback(base, palettes), false,
                    "小织暂时没有连上，已提供内置变体；可以继续手动配色。");
        }
        return new Result(parsed, true, "小织给出了 3 个变体；它们只是本页的配色建议。");
    }

    private String renderPrompt(HeritageCatalog.Pattern base, List<Palette> palettes, String theme) {
        try {
            return promptTemplate
                    .replace("{{baseName}}", base.name())
                    .replace("{{family}}", base.family())
                    .replace("{{variant}}", base.variant())
                    .replace("{{detail}}", String.valueOf(base.detail()))
                    .replace("{{variants}}", json.writeValueAsString(HeritageCatalogService.variantsFor(base.family())))
                    .replace("{{palettes}}", json.writeValueAsString(palettes))
                    .replace("{{example}}", exampleJson(base, palettes))
                    .replace("{{theme}}", theme.isBlank() ? "未填写" : theme);
        } catch (Exception e) {
            throw new IllegalStateException("共创提示词无法编码", e);
        }
    }

    private String exampleJson(HeritageCatalog.Pattern base, List<Palette> palettes) throws Exception {
        List<String> allowed = HeritageCatalogService.variantsFor(base.family());
        int baseIndex = Math.max(0, allowed.indexOf(base.variant()));
        List<Map<String, Object>> examples = List.of(
                example(allowed.get(baseIndex), base.detail(), palettes.get(0).id(),
                        "保留原结构", "保留原始结构与留白，先观察整体节奏。"),
                example(allowed.get((baseIndex + 1) % allowed.size()), base.detail(), palettes.get(1).id(),
                        "换一种结构", "在同一门工艺的结构语言里换一种组织方式。"),
                example(allowed.get((baseIndex + 2) % allowed.size()), Math.min(12, base.detail() + 2),
                        palettes.get(2).id(), "更细密的观察", "提高一点密度，观察线条与留白的变化。"));
        return json.writeValueAsString(Map.of("variants", examples));
    }

    private Map<String, Object> example(String variant, int detail, String paletteId,
                                        String name, String rationale) {
        return new LinkedHashMap<>(Map.of(
                "variant", variant,
                "detail", detail,
                "paletteId", paletteId,
                "name", name,
                "rationale", rationale));
    }

    private List<Palette> choosePalettes(HeritageCatalog catalog, HeritageCatalog.Pattern base) {
        List<Palette> result = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        addPalette(result, signatures, base.palette());
        for (HeritageCatalog.Pattern pattern : catalog.patterns()) {
            if (result.size() >= 4) break;
            addPalette(result, signatures, pattern.palette());
        }
        while (result.size() < 4) {
            addPalette(result, signatures, base.palette());
            if (result.size() < 4) {
                // The catalog is validated as 24 patterns; this is unreachable in normal startup.
                result.add(new Palette("p" + result.size(), List.copyOf(base.palette())));
            }
        }
        return result;
    }

    private void addPalette(List<Palette> result, Set<String> signatures, List<String> colors) {
        if (colors == null || colors.size() != 4) return;
        String signature = String.join("|", colors);
        if (signatures.add(signature)) result.add(new Palette("p" + result.size(), List.copyOf(colors)));
    }

    private List<Variant> parse(String raw, HeritageCatalog.Pattern base, List<Palette> palettes) {
        if (raw == null || raw.isBlank() || raw.length() > 5000) return null;
        try {
            JsonNode root = json.readTree(raw);
            JsonNode array = root.path("variants");
            if (!array.isArray() || array.size() != 3) return null;
            List<String> allowed = HeritageCatalogService.variantsFor(base.family());
            Map<String, Palette> paletteMap = new LinkedHashMap<>();
            for (Palette palette : palettes) paletteMap.put(palette.id(), palette);
            List<Variant> variants = new ArrayList<>();
            Set<String> combinations = new HashSet<>();
            for (int index = 0; index < 3; index++) {
                JsonNode item = array.get(index);
                String variant = item.path("variant").asText("");
                int detail = item.path("detail").asInt(-1);
                String paletteId = item.path("paletteId").asText("");
                String name = item.path("name").asText("").trim();
                String rationale = item.path("rationale").asText("").trim();
                if (!allowed.contains(variant) || detail < 4 || detail > 12
                        || !paletteMap.containsKey(paletteId)
                        || name.isBlank() || name.length() > 30
                        || rationale.isBlank() || rationale.length() > 200) {
                    return null;
                }
                String combination = variant + "|" + detail + "|" + paletteId;
                if (!combinations.add(combination)) return null;
                variants.add(new Variant("ai-" + index, name, base.family(), variant, detail,
                        paletteMap.get(paletteId).colors(), rationale));
            }
            return variants;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Variant> fallback(HeritageCatalog.Pattern base, List<Palette> palettes) {
        List<String> allowed = HeritageCatalogService.variantsFor(base.family());
        int baseIndex = Math.max(0, allowed.indexOf(base.variant()));
        String second = allowed.get((baseIndex + 1) % allowed.size());
        String third = allowed.get((baseIndex + 2) % allowed.size());
        int detail = base.detail();
        int denser = Math.min(12, detail + 2);
        return List.of(
                new Variant("fallback-0", "保留原结构", base.family(), base.variant(), detail,
                        palettes.get(0).colors(), "保留原始结构与留白，先观察整体节奏。"),
                new Variant("fallback-1", "换一种结构", base.family(), second, detail,
                        palettes.get(1).colors(), "在同一门工艺的结构语言里换一种组织方式。"),
                new Variant("fallback-2", "更细密的观察", base.family(), third, denser,
                        palettes.get(2).colors(), "提高一点密度，观察线条与留白的变化。")
        );
    }
}
