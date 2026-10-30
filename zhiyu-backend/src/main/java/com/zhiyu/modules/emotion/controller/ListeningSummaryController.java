package com.zhiyu.modules.emotion.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.emotion.service.ListeningSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/emotion/sessions/{id}/summary")
public class ListeningSummaryController {

    public record Generate(boolean consent) {
    }

    public record Save(@NotBlank @Size(max = 300) String summary,
                       boolean generated,
                       @AssertTrue(message = "请确认保存授权") boolean consent) {
    }

    private final ListeningSummaryService service;

    @PostMapping("/generate")
    public Result<?> generate(@PathVariable Long id, @Valid @RequestBody Generate input) {
        return Result.success(service.generate(id, input.consent()));
    }

    @GetMapping
    public Result<?> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PutMapping
    public Result<?> save(@PathVariable Long id, @Valid @RequestBody Save input) {
        return Result.success(service.save(id, input.summary(), input.generated(), input.consent()));
    }

    @DeleteMapping
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
