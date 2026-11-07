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
    public record Review(@NotNull @Min(1) @Max(2) Integer status,@NotNull @Min(0) @Max(2) Integer expectedStatus,@Size(max=255) String reason,
                         /** 软档标记（2026-11-07 处置链）：true 仅允许随 status=1 使用；narrative/riskPointsNonEmpty 为管理端回显的 AI 建议信息（可空）。 */
                         Boolean softTier,@Size(max=20) String narrative,Boolean riskPointsNonEmpty){}
    @GetMapping("/api/admin/comments") public Result<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="0") int status,@RequestParam(required=false) Long workId){return Result.success(service.list(page,status,workId));}
    /** 评论详情（脱敏摘要，O2 2026-11-04；role∈{2,3} 在服务层校验）。 */
    @GetMapping("/api/admin/comments/{id}") public Result<?> detail(@PathVariable long id){return Result.success(service.detail(id));}
    @PostMapping("/api/admin/comments/{id}/review") public Result<?> review(@PathVariable long id,@Valid @RequestBody Review r){service.review(id,r.status(),r.expectedStatus(),r.reason(),r.softTier(),r.narrative(),r.riskPointsNonEmpty());return Result.success();}
    @DeleteMapping("/api/works/{id}/comments/{commentId}") public Result<?> remove(@PathVariable long id,@PathVariable long commentId){service.remove(id,commentId);return Result.success();}
}
