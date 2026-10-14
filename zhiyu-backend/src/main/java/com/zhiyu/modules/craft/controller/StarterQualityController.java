package com.zhiyu.modules.craft.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.service.StarterCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class StarterQualityController {
 private final StarterCatalogService service;
 @GetMapping("/api/admin/starter-quality") public Result<?> quality(){return Result.success(service.quality());}
}
