package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminAiRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * AI 模型路由只读总览（2026-11-08）：配置呈现 + ai_trace provider 维度纯计数。
 *
 * <p>权限：{@code /api/admin/**} 兜底 role=3（SecurityConfig），学生/传承人/咨询师 403。
 * 只读端点，不提供任何配置写入（切换走启动参数，刻意如此）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-routing")
public class AdminAiRoutingController {

    private final AdminAiRoutingService service;

    /**
     * 路由总览：capabilities（配置）+ distribution（按 scene+provider+period 聚合计数）
     * + cloudOnlyLocalCheck（cloud-only 本地记录按事故锚点拆 pre/post）。
     *
     * @param from   区间起（ISO-8601，如 2026-11-01T00:00:00）；缺省取 to-7天
     * @param to     区间止；缺省取当前时间
     * @param anchor 事故锚点临时覆盖（ISO-8601）；缺省取配置
     *               {@code zhiyu.admin.ai-routing.incident-fixed-at}
     */
    @GetMapping("/overview")
    public Result<?> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime anchor) {
        return Result.success(service.overview(from, to, anchor));
    }
}
