package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.service.AdaptivePracticeService;
import com.zhiyu.modules.practice.vo.AdaptivePlanVO;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/practice/adaptive-plan")
public class AdaptivePracticeController {

    private final AdaptivePracticeService service;

    @GetMapping
    public Result<AdaptivePlanVO> plan(@RequestParam(required = false) Integer minutes) {
        return Result.success(service.plan(UserContext.getUserId(), minutes));
    }
}
