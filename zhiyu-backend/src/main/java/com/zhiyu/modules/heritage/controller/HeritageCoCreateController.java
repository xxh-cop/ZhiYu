package com.zhiyu.modules.heritage.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.heritage.service.HeritageCoCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学生端受控纹样共创；AI 只返回有限结构，不执行任何模型返回的 SVG/HTML。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/heritage")
public class HeritageCoCreateController {

    private final HeritageCoCreateService service;

    @PostMapping("/co-create")
    public Result<HeritageCoCreateService.Result> create(
            @RequestBody HeritageCoCreateService.Request request) {
        return Result.success(service.create(request));
    }
}
