package com.zhiyu.modules.admin.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.OperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class OperationsController {
 private final OperationsService service;
 @GetMapping("/api/admin/dashboard/operations") public Result<?> operations(@RequestParam(defaultValue="7") int days){return Result.success(service.overview(days));}
}
