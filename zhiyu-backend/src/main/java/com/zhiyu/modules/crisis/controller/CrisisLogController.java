package com.zhiyu.modules.crisis.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.crisis.CrisisSessionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 危机路径留存查询（AJ，2026-11-04）。
 *
 * <p>仅 role ∈ {2,3}（SecurityConfig 放行 + 服务层再叠加关怀范围过滤）；
 * 学生端无任何入口。列表只出元数据，摘要仅在详情且每次查看落操作审计。
 */
@RestController
@RequiredArgsConstructor
public class CrisisLogController {

    private final CrisisSessionLogService service;

    @GetMapping("/api/admin/crisis-logs")
    public Result<?> list(@RequestParam(defaultValue = "1") int page) {
        return Result.success(service.list(page));
    }

    @GetMapping("/api/admin/crisis-logs/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }
}
