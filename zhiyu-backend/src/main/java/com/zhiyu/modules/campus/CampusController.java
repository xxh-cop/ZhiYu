package com.zhiyu.modules.campus;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class CampusController {
    private final CampusService service;
    @GetMapping("/api/campus/verification") public Result<?> mine(){return Result.success(service.mine());}
    @PostMapping("/api/campus/verification") public Result<?> apply(@Valid @RequestBody CampusService.Application r){service.apply(r);return Result.success();}
    @GetMapping("/api/admin/campus-verifications") public Result<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(required=false) String status){return Result.success(service.list(page,status));}
    @PostMapping("/api/admin/campus-verifications/{id}/review") public Result<?> review(@PathVariable Long id,@Valid @RequestBody CampusService.Review r){service.review(id,r);return Result.success();}
}
