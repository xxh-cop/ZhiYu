package com.zhiyu.modules.practice.model;

import java.util.List;

/**
 * 短练习目录：1–10 分钟的可暂停练习，用于安放注意力与陪伴，
 * 不作诊断、疗效或情绪好坏判断。内容来自 classpath {@code catalog/short-practices.json}。
 */
public record ShortPracticeCatalog(String version, String disclaimer, List<ShortPractice> practices) {

    public record ShortPractice(String id, String title, String subtitle, String kind, int minutes,
                                List<String> moodTags, String patternId, List<String> materials,
                                List<Step> steps, String safety, String closing) {
    }

    public record Step(int order, String title, String cue, int seconds) {
    }
}
