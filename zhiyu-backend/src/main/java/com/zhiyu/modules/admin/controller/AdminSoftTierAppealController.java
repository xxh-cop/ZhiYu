package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.SoftTierPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 软档申诉单列表（2026-11-07，设计 docs/moderation-soft-tier-disposition-chain-20261107.md）。
 *
 * <p>独立控制器：契约路径为 {@code /api/admin/soft-tier-appeals}（openapi.yaml 软档处置链段），
 * 不能挂在 {@code AdminSoftTierPoolController}（类级 {@code /api/admin/soft-tier-pool}）下——
 * 方法级绝对路径会被类级前缀拼接，导致契约路径 404（联调 F2）。
 *
 * <p>仅 role=3（/api/admin/** 兜底）；处置链开关关闭时 1001「软档处置链未开启」。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/soft-tier-appeals")
public class AdminSoftTierAppealController {

    private final SoftTierPoolService poolService;

    /** 申诉单分页列表（appeal_status=FILED，按申诉时间倒序；含 appealReason 与作者昵称）。 */
    @GetMapping
    public Result<Map<String, Object>> appeals(@RequestParam(defaultValue = "1") int page) {
        return Result.success(poolService.appeals(page));
    }
}
