package com.zhiyu.modules.heritage.model;

import java.util.List;

/**
 * 非遗只读目录（H1a）。内容来自 classpath {@code catalog/heritage-catalog.json}，
 * 全部纹样为原创教学再设计，不是传统原作；来源仅作材料/技艺背景参考。
 *
 * <p>目录 ID 均为字符串，前端与 API 不得转成 JavaScript Number。
 *
 * @param version     目录版本
 * @param disclaimer  免责与边界声明
 * @param sources     已登记的官方来源（4 条）
 * @param traditions  非遗工艺（4 门）
 * @param patterns    原创纹样（24 个）
 */
public record HeritageCatalog(String version, String disclaimer,
                              List<Source> sources, List<Tradition> traditions, List<Pattern> patterns) {

    /** 官方来源；{@code mediaReuse=NOT_GRANTED} 表示项目页未明示媒体再利用许可。 */
    public record Source(String id, String title, String organization, String url,
                         String accessedAt, String mediaReuse, List<String> facts) {
    }

    /** 非遗工艺；来源只作材料与技艺背景，不支撑具体原创图形。 */
    public record Tradition(String id, String name, String shortName, String region,
                            String category, String registryCode, Integer listedYear, List<String> sourceIds,
                            String summary, List<String> materials, List<String> process,
                            List<String> safety, String practiceBoundary) {
    }

    /** 原创教学纹样；{@code palette} 固定四色，按底、墨、主、辅排列。 */
    public record Pattern(String id, String traditionId, String name, String family,
                          String variant, Integer detail, List<String> palette, String origin,
                          List<String> sourceIds, String designNote, String learningFocus,
                          Integer minutes, String level) {
    }
}
