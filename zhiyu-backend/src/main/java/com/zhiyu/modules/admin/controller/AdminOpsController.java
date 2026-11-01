package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端只读运行健康与备份清单；仅 ROLE_3。 */
@RestController
@RequestMapping("/api/admin/ops")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AdminOpsService service;

    @GetMapping("/health")
    public Result<AdminOpsService.Health> health() {
        return Result.success(service.health());
    }

    @GetMapping("/backup-manifest")
    public Result<AdminOpsService.BackupManifest> backupManifest() {
        return Result.success(service.backupManifest());
    }
}
