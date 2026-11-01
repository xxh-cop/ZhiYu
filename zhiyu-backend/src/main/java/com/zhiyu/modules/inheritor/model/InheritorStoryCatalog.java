package com.zhiyu.modules.inheritor.model;

import java.util.List;

/**
 * 传承人故事内容包：合成教学叙事，不冒充真实人物访谈或传承人履历。
 * 工艺背景通过 sourceIds 引用项目已登记公开来源。
 */
public record InheritorStoryCatalog(String version, String disclaimer, List<Story> stories) {

    public record Story(String id, String title, String subtitle, String traditionId, int readingMinutes,
                        boolean synthetic, String synopsis, List<Section> sections, String quote,
                        List<String> sourceIds, String boundary, String learningPrompt) {
    }

    public record Section(String heading, String body) {
    }
}
