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
 * <p>权限：Spring Security 已配置 {@code /api/admin/**} 要求 role ∈ {1,2,3}，
 * 接口 2（audit）由 SecurityConfig 精确匹配 PUT 方法限定 role=3（仅管理员）。
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
}
