package com.zhiyu.modules.growth;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/growth/programs/{id}")
public class ProgramFeedbackController {
    private final ProgramFeedbackService service;
    @GetMapping("/feedback") public Result<?> own(@PathVariable long id){return Result.success(service.own(id));}
    @PutMapping("/feedback") public Result<?> save(@PathVariable long id,@Valid @RequestBody ProgramFeedbackService.Feedback r){service.save(id,r);return Result.success();}
    @DeleteMapping("/feedback") public Result<?> remove(@PathVariable long id){service.remove(id);return Result.success();}
    @GetMapping("/feedback-summary") public Result<?> summary(@PathVariable long id){return Result.success(service.summary(id));}
}
