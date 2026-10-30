package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminAiGovernanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-governance")
public class AdminAiGovernanceController {

    private final AdminAiGovernanceService service;

    @GetMapping("/overview")
    public Result<?> overview(@RequestParam(defaultValue = "30") int days) {
        return Result.success(service.overview(days));
    }
}
