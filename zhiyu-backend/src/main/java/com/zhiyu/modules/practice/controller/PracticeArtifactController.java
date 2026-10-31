package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.dto.SavePracticeArtifactDTO;
import com.zhiyu.modules.practice.service.PracticeArtifactService;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/practice/sessions/{id}/artifact")
@RequiredArgsConstructor
public class PracticeArtifactController {

    private final PracticeArtifactService service;

    @PostMapping
    public Result<Map<String, Object>> save(@PathVariable Long id,
                                            @Valid @RequestBody SavePracticeArtifactDTO request) {
        return Result.success(service.save(UserContext.getUserId(), id, request));
    }

    @GetMapping
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.success(service.get(UserContext.getUserId(), id));
    }

    @DeleteMapping
    public Result<?> delete(@PathVariable Long id) {
        service.delete(UserContext.getUserId(), id);
        return Result.success();
    }
}
