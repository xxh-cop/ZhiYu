package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.service.AdaptivePracticeService;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自适应练习计划（S1 2026-11-04 门控）：基于已授权记忆偏好与个人练习历史
 * 生成个性化计划，属个人数据敏感处理，挂隐私同意门。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practice/adaptive-plan")
public class AdaptivePracticeController {

    private final AdaptivePracticeService service;
    private final BusinessAccess access;

    @GetMapping
    public Result<AdaptivePlanVO> plan(@RequestParam(required = false) Integer minutes) {
        return Result.success(service.plan(access.consentedUser(), minutes));
    }
}
