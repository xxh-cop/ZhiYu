package com.zhiyu.modules.work.controller;
import com.zhiyu.modules.work.service.CommentModerationService;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class CommentModerationController {
    private final CommentModerationService service;
    public record Review(@NotNull @Min(1) @Max(2) Integer status,@NotNull @Min(0) @Max(2) Integer expectedStatus){}
    @GetMapping("/api/admin/comments") public Result<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="0") int status){return Result.success(service.list(page,status));}
    @PostMapping("/api/admin/comments/{id}/review") public Result<?> review(@PathVariable long id,@Valid @RequestBody Review r){service.review(id,r.status(),r.expectedStatus());return Result.success();}
    @DeleteMapping("/api/works/{id}/comments/{commentId}") public Result<?> remove(@PathVariable long id,@PathVariable long commentId){service.remove(id,commentId);return Result.success();}
}
