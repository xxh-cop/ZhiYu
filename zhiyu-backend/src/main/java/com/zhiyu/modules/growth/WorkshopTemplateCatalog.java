package com.zhiyu.modules.growth;

import java.util.List;

/** 团体工作坊模板：只提供活动文案与准备清单，不写入数据库、不替代活动创建校验。 */
public record WorkshopTemplateCatalog(String version, String disclaimer, List<WorkshopTemplate> templates) {

    public record WorkshopTemplate(String id, String title, String summary, String kind, int minutes,
                                   int capacity, String description, String materials, String safety,
                                   String sourceNote, List<String> tags) {
    }
}
