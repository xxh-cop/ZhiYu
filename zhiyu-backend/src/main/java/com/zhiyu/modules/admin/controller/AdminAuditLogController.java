package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminAuditLogService;
import com.zhiyu.modules.admin.vo.AuditLogDetailVO;
import com.zhiyu.modules.admin.vo.AuditLogItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 AI 审计日志控制器（P1-A1）。
 *
 * <p>提供 2 个接口：
 * <ul>
 *   <li>GET /api/admin/audit-logs       — 审计日志分页查询（仅 role=3）</li>
 *   <li>GET /api/admin/audit-logs/{id}  — 审计日志详情（仅 role=3）</li>
 * </ul>
 *
 * <p>权限：{@code /api/admin/**} 默认仅 role=3，本控制器未做任何放宽；
 * 列表与详情都要求管理员（role=3）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    /**
     * 审计日志分页查询。
     *
     * @param page      页码（默认 1）
     * @param pageSize  每页大小（默认 10）
     * @param scene     场景筛选（可空）
     * @param action    动作筛选（可空）
     * @param startTime 起始时间（可空，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（可空，格式 yyyy-MM-dd HH:mm:ss）
     */
    @GetMapping
    public Result<PageVO<AuditLogItemVO>> listAuditLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(adminAuditLogService.listAuditLogs(page, pageSize, scene, action, startTime, endTime));
    }

    /**
     * 审计日志详情（含解密后的 rawInput / aiOutput）。仅管理员。
     *
     * @param id 日志 ID
     */
    @GetMapping("/{id}")
    public Result<AuditLogDetailVO> getAuditLog(@PathVariable Long id) {
        return Result.success(adminAuditLogService.getAuditLog(id));
    }
}
