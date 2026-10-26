package com.zhiyu.modules.care;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class CareWorkflowController {
    private final CareWorkflowService service;
    private final OperationLog logs;
    public record Followup(@NotBlank String action){}
    @GetMapping("/api/admin/care-reminders/{id}") public Result<?> detail(@PathVariable Long id){return Result.success(service.detail(id));}
    @PostMapping("/api/admin/care-reminders/{id}/followups") public Result<?> follow(@PathVariable Long id,@Valid @RequestBody Followup r){service.follow(id,r.action());return Result.success();}
    @GetMapping("/api/admin/operation-logs") public Result<?> logs(@RequestParam(defaultValue="1") int page){return Result.success(logs.list(page));}
}
