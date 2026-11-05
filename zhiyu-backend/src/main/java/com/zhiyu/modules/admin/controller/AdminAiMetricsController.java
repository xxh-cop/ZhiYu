package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminAiMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * AI 用量与质量指标聚合查询（BO 批，2026-11-05）。
 *
 * <p>权限：{@code /api/admin/**} 兜底 role=3（SecurityConfig），学生/传承人/咨询师 403。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-metrics")
public class AdminAiMetricsController {

    private final AdminAiMetricsService service;

    /**
     * 按时间区间聚合 AI 调用指标（按资源 key 分组）。
     *
     * @param from 区间起（ISO-8601，如 2026-11-01T00:00:00）；缺省取 to-7天
     * @param to   区间止；缺省取当前时间
     */
    @GetMapping
    public Result<?> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(service.metrics(from, to));
    }
}
