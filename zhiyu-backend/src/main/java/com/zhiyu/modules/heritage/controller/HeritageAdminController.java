package com.zhiyu.modules.heritage.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageAdminContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端非遗目录维护；权限由 Security 与 service 的 role(3) 双重校验。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/heritage")
public class HeritageAdminController {

    private final HeritageAdminContentService service;

    public record SaveRequest(int revision, HeritageCatalog catalog) {
    }

    @GetMapping("/catalog")
    public Result<HeritageAdminContentService.Snapshot> catalog() {
        return Result.success(service.snapshot());
    }

    @PutMapping("/catalog")
    public Result<HeritageAdminContentService.Snapshot> save(@RequestBody SaveRequest request) {
        return Result.success(service.save(request.revision(), request.catalog()));
    }

    @DeleteMapping("/catalog")
    public Result<HeritageAdminContentService.Snapshot> reset() {
        return Result.success(service.reset());
    }
}
