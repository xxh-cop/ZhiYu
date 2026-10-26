package com.zhiyu.modules.growth;
import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/growth/programs/{id}/lessons")
public class CourseLessonController {
    private final CourseLessonService service;
    public record Progress(@NotNull Boolean completed){}
    @GetMapping public Result<?> list(@PathVariable long id){return Result.success(service.list(id));}
    @PostMapping public Result<?> create(@PathVariable long id,@Valid @RequestBody CourseLessonService.Lesson r){return Result.success(Map.of("id",service.save(id,null,r)));}
    @PutMapping("/{lessonId}") public Result<?> update(@PathVariable long id,@PathVariable long lessonId,@Valid @RequestBody CourseLessonService.Lesson r){return Result.success(Map.of("id",service.save(id,lessonId,r)));}
    @DeleteMapping("/{lessonId}") public Result<?> delete(@PathVariable long id,@PathVariable long lessonId){service.delete(id,lessonId);return Result.success();}
    @PutMapping("/{lessonId}/progress") public Result<?> progress(@PathVariable long id,@PathVariable long lessonId,@Valid @RequestBody Progress r){service.progress(id,lessonId,r.completed());return Result.success();}
}
