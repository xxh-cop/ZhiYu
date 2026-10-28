package com.zhiyu.modules.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 疗愈闭环六阶段漏斗（20261028）。
 *
 * <p>同用户、同时间窗、按 user_id 去重；不返回用户级信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TherapyFunnelVO {

    private int days;
    private int cohort;
    private boolean insufficient;
    private List<Stage> stages;
    private Flow flow;
    private String generatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stage {
        private String key;
        private String label;
        private int users;
        private Double fromPrevious;
        private Double fromStart;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Flow {
        private int completedSessions;
        private int flowSeconds;
        private Double focusRate;
    }
}
