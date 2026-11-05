package com.zhiyu.modules.craft.takedown;

import com.zhiyu.modules.craft.entity.CraftProject;

import java.time.LocalDateTime;

/**
 * 下架状态推导（2026-11-05 分层下架）。
 *
 * <p>纯函数：由 {@link CraftProject} 当前行 + 配置 + 当前时间推导出
 * 「学生视角的有效状态」，供 listSteps / 心跳闸口 / 归档 Worker 复用同一口径。
 *
 * <p>状态机（craft_project.status）：0 待审 / 1 上架 / 2 下架 / 3 归档。
 * 合法迁移 1→2（带 kind）、2→3（到期归档）；2/3 不可回 1（恢复上线走新建送审）。
 *
 * <ul>
 *   <li>合规下架（compliance）：公开面即收；进行中/暂停会话转只读——可回看、
 *       窗口内可导出、不可推进步骤；窗口（export_until）结束视为已归档。</li>
 *   <li>运营下架（operational）：存量会话不受影响，可运行到 archive_at；
 *       到期视为已归档（只读）。archive_at 缺失的存量行按
 *       takedown_at + default-deadline-days 推导。</li>
 * </ul>
 */
public final class TakedownStateResolver {

    public static final String KIND_COMPLIANCE = "compliance";
    public static final String KIND_OPERATIONAL = "operational";

    public static final String NOTICE_COMPLIANCE = "craft-takedown-compliance";
    public static final String NOTICE_OPERATIONAL = "craft-takedown-operational";
    public static final String NOTICE_ARCHIVED = "craft-archived";

    private TakedownStateResolver() {
    }

    /**
     * 学生视角的有效状态视图。
     *
     * @param effectiveStatus 有效状态（2 下架但窗口/期限已过 → 3 归档，与 Worker 落库口径一致）
     * @param readOnly        会话只读（不可推进步骤）
     * @param canReview       可回看历史步骤
     * @param canExport       当前可导出（合规窗口内且导出开关开）
     * @param noticeKey       提示语模板 key（notices/<key>.md）
     * @param deadline        当前阶段的截止时间（导出窗口或归档时间）
     */
    public record TakedownView(int effectiveStatus, boolean readOnly, boolean canReview,
                               boolean canExport, String noticeKey, LocalDateTime deadline) {
    }

    public static TakedownView resolve(CraftProject project, TakedownProperties props, LocalDateTime now) {
        int status = project.getStatus() == null ? 0 : project.getStatus();
        switch (status) {
            case 1:
                return new TakedownView(1, false, true, false, null, null);
            case 3:
                return new TakedownView(3, true, true, false, NOTICE_ARCHIVED, project.getArchiveAt());
            case 2:
                if (KIND_OPERATIONAL.equals(project.getTakedownKind())) {
                    LocalDateTime deadline = project.getArchiveAt() != null
                            ? project.getArchiveAt()
                            : (project.getTakedownAt() != null
                                ? project.getTakedownAt().plusDays(props.getOperational().getDefaultDeadlineDays())
                                : null);
                    if (deadline != null && now.isAfter(deadline)) {
                        return new TakedownView(3, true, true, false, NOTICE_ARCHIVED, deadline);
                    }
                    return new TakedownView(2, false, true, false, NOTICE_OPERATIONAL, deadline);
                }
                // 合规下架（含 AY1 历史 NULL kind 终态下架：行为与合规一致——即时收授权）
                LocalDateTime exportUntil = project.getExportUntil();
                if (exportUntil != null && !now.isAfter(exportUntil)) {
                    return new TakedownView(2, true, true, props.isExportEnabled(), NOTICE_COMPLIANCE, exportUntil);
                }
                return new TakedownView(3, true, true, false, NOTICE_ARCHIVED, exportUntil);
            default:
                // 0 待审：对学生不可见（调用方在解析前应已 1004）
                return new TakedownView(0, true, false, false, null, null);
        }
    }
}
