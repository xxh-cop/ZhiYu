package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.dto.AuditRequest;
import com.zhiyu.modules.admin.dto.AuditQueueItemVO;
import com.zhiyu.modules.admin.dto.CareReminderVO;
import com.zhiyu.modules.admin.dto.DashboardStatsVO;
import com.zhiyu.modules.admin.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端控制器（M2 批 4）。
 *
 * <p>提供 4 个接口：
 * <ul>
 *   <li>GET  /api/admin/audit-queue          — 待审核内容队列</li>
 *   <li>PUT  /api/admin/projects/{id}/audit   — 审核手作项目</li>
 *   <li>GET  /api/admin/care-reminders        — 脱敏关怀提醒汇总</li>
 *   <li>GET  /api/admin/dashboard/stats       — 数据看板指标</li>
 * </ul>
 *
 * <p>权限：Spring Security 默认要求 {@code /api/admin/**} 为 role=3；
 * 仅接口 3（care-reminders）显式放行 role ∈ {2,3}，接口 2（audit）仍仅 role=3。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 待审核内容队列（第一版只支持 craft_project 类型）。
     *
     * @param type 内容类型（project/work/comment，默认 project）
     * @param page 页码（从 1 开始，默认 1）
     * @return 待审项目分页列表
     */
    @GetMapping("/audit-queue")
    public Result<PageVO<AuditQueueItemVO>> auditQueue(
            @RequestParam(defaultValue = "project") String type,
            @RequestParam(defaultValue = "1") Integer page) {
        return Result.success(adminService.getAuditQueue(type, page));
    }

    /**
     * 审核手作项目（通过/驳回）。仅管理员（role=3）可操作。
     *
     * @param id  项目 ID
     * @param req 审核请求体
     * @return 更新后的项目 VO
     */
    @PutMapping("/projects/{id}/audit")
    public Result<AuditQueueItemVO> auditProject(
            @PathVariable Long id,
            @Valid @RequestBody AuditRequest req) {
        return Result.success(adminService.auditProject(id, req));
    }

    /**
     * 脱敏关怀提醒汇总。
     *
     * @param status 处理状态（0 待处理 / 1 已跟进，不传则查全部）
     * @param page   页码（从 1 开始，默认 1）
     * @return 关怀提醒分页列表
     */
    @GetMapping("/care-reminders")
    public Result<PageVO<CareReminderVO>> careReminders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page) {
        return Result.success(adminService.getCareReminders(status, page));
    }

    /**
     * 数据看板统计指标。
     *
     * @return 看板指标聚合数据
     */
    @GetMapping("/dashboard/stats")
    public Result<DashboardStatsVO> dashboardStats() {
        return Result.success(adminService.getDashboardStats());
    }

    /**
     * AY1（2026-11-05）+ 分层下架（2026-11-05）：下架已上架项目（status 1→2），理由必填。
     * kind=compliance 走导出窗口（系统配置），kind=operational 必须给 archiveAt（≥7 天）。
     * 仅管理员（/api/admin/** 兜底 role=3）；落 admin_operation_log + 通知传承人。
     */
    @PutMapping("/projects/{id}/takedown")
    public Result<Void> takedownProject(@PathVariable Long id,
                                        @Valid @RequestBody com.zhiyu.modules.admin.dto.ProjectTakedownDTO req) {
        adminService.takedownProject(id, req.getReason(), req.getKind(), req.getArchiveAt());
        return Result.success();
    }

    /**
     * BC1（2026-11-05）：项目问责详情——auditReason + 动作来源（评审驳回 vs 事后下架）。
     */
    @GetMapping("/projects/{id}")
    public Result<java.util.Map<String, Object>> projectDetail(@PathVariable Long id) {
        return Result.success(adminService.projectDetail(id));
    }

    /**
     * AY2（2026-11-05）：逻辑删除项目（任意未删除状态），理由经 query 参数必填。
     * 级联逻辑删除步骤/材料并退役孤儿媒体；落 admin_operation_log + 通知传承人。
     */
    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        adminService.deleteProject(id, reason);
        return Result.success();
    }
}
