package com.zhiyu.modules.companion;

import com.zhiyu.modules.practice.enums.PracticeSessionStatus;

import java.util.Map;
import java.util.Set;

/**
 * 会话状态 → 阶段与工具的硬白名单。
 * 模型只负责提出计划，能否执行由本类决定；终态一律不允许工具。
 */
public final class CompanionPolicy {

    private static final Map<PracticeSessionStatus, Set<String>> TOOL_ALLOWLIST = Map.of(
            PracticeSessionStatus.CREATED, Set.of("suggestBreathing"),
            PracticeSessionStatus.LISTENING, Set.of("searchHeritage", "suggestBreathing"),
            PracticeSessionStatus.RECOMMENDED,
            Set.of("recommendPractice", "searchHeritage", "suggestBreathing"),
            PracticeSessionStatus.PRACTICING, Set.of("searchHeritage", "suggestBreathing"),
            PracticeSessionStatus.PAUSED, Set.of("searchHeritage", "suggestBreathing"),
            PracticeSessionStatus.COMPLETED, Set.of(),
            PracticeSessionStatus.ARTIFACT_SAVED, Set.of(),
            PracticeSessionStatus.ABANDONED, Set.of());

    private CompanionPolicy() {
    }

    public static Set<String> allowedTools(PracticeSessionStatus status) {
        if (status == null) return Set.of();
        return TOOL_ALLOWLIST.getOrDefault(status, Set.of());
    }

    public static boolean allowsTool(PracticeSessionStatus status, String tool) {
        return tool != null && allowedTools(status).contains(tool);
    }

    public static boolean isTerminal(PracticeSessionStatus status) {
        return status == PracticeSessionStatus.COMPLETED
                || status == PracticeSessionStatus.ARTIFACT_SAVED
                || status == PracticeSessionStatus.ABANDONED;
    }

    public static boolean allowsStage(PracticeSessionStatus status, CompanionStage stage) {
        if (status == null || stage == null) return false;
        return switch (status) {
            case CREATED -> stage == CompanionStage.LISTEN || stage == CompanionStage.BREATHE;
            case LISTENING -> stage == CompanionStage.LISTEN || stage == CompanionStage.RECOMMEND
                    || stage == CompanionStage.BREATHE;
            case RECOMMENDED -> stage == CompanionStage.RECOMMEND || stage == CompanionStage.PRACTICE
                    || stage == CompanionStage.BREATHE;
            case PRACTICING -> stage == CompanionStage.PRACTICE || stage == CompanionStage.BREATHE
                    || stage == CompanionStage.DONE;
            case PAUSED -> stage == CompanionStage.PRACTICE || stage == CompanionStage.BREATHE;
            case COMPLETED, ARTIFACT_SAVED, ABANDONED -> stage == CompanionStage.DONE;
        };
    }

    /** 工具必须与它宣称的阶段一致，避免“推荐工具 + 练习阶段”这类绕过。 */
    public static boolean isToolStageConsistent(String tool, CompanionStage stage) {
        if (tool == null) return stage != null;
        return switch (tool) {
            case "recommendPractice" -> stage == CompanionStage.RECOMMEND;
            case "suggestBreathing" -> stage == CompanionStage.BREATHE;
            case "searchHeritage" -> stage == CompanionStage.LISTEN || stage == CompanionStage.RECOMMEND
                    || stage == CompanionStage.PRACTICE;
            default -> false;
        };
    }
}
