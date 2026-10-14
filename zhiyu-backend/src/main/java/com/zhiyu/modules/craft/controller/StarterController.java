package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.service.StarterCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/craft")
public class StarterController {
    private final StarterCatalogService service;
    @GetMapping("/starter") public Result<?> starter(){return Result.success(service.available());}
    @GetMapping("/session/active") public Result<?> active(@RequestParam(required=false) Long projectId){return Result.success(service.active(projectId));}
}
