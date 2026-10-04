package com.zhiyu.modules.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据看板统计指标 VO（M2 批 4）。
 *
 * <p>聚合 user / work / craft_project / care_reminder / emotion_session 五张表。
 */
@Data
public class DashboardStatsVO {

    /** 总用户数 */
    private long totalUsers;

    /** 总作品数 */
    private long totalWorks;

    /** 待审项目数 */
    private long pendingProjects;

    /** 待处理关怀提醒数 */
    private long pendingCareReminders;

    /** 情绪分布（按 mood 分组统计） */
    private List<MoodItem> moodDistribution;

    /** 最近 7 天用户注册趋势 */
    private List<TrendItem> userTrend7d;

    /** 情绪分布项 */
    @Data
    public static class MoodItem {
        /** 情绪 code（如 anxious / stressed 等） */
        private String mood;
        /** 该情绪的会话数 */
        private int count;
    }

    /** 用户趋势项 */
    @Data
    public static class TrendItem {
        /** 日期（格式 yyyy-MM-dd） */
        private String date;
        /** 当日新注册用户数 */
        private int count;
    }
}
