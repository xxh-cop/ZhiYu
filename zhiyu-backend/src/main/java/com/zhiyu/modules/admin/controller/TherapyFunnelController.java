package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.TherapyFunnelService;
import com.zhiyu.modules.admin.vo.TherapyFunnelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 疗愈闭环漏斗接口（20261028）。
 */
@RestController
@RequiredArgsConstructor
public class TherapyFunnelController {

    private final TherapyFunnelService service;

    @GetMapping("/api/admin/dashboard/therapy-funnel")
    public Result<TherapyFunnelVO> funnel(@RequestParam(defaultValue = "7") int days) {
        return Result.success(service.funnel(days));
    }
}
