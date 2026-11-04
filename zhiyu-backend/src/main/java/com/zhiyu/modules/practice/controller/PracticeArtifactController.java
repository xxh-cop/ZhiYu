package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.dto.SavePracticeArtifactDTO;
import com.zhiyu.modules.practice.service.PracticeArtifactService;
import com.zhiyu.security.BusinessAccess;
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

/**
 * 练习产物（S1 2026-11-04 门控）：notes 为用户反思文本，读写挂隐私同意门；
 * 删除属撤回权利，只做账号状态复核（{@code user()}）。
 */
@RestController
@RequestMapping("/api/practice/sessions/{id}/artifact")
@RequiredArgsConstructor
public class PracticeArtifactController {

    private final PracticeArtifactService service;
    private final BusinessAccess access;

    @PostMapping
    public Result<Map<String, Object>> save(@PathVariable Long id,
                                            @Valid @RequestBody SavePracticeArtifactDTO request) {
        return Result.success(service.save(access.consentedUser(), id, request));
    }

    @GetMapping
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.success(service.get(access.consentedUser(), id));
    }

    @DeleteMapping
    public Result<?> delete(@PathVariable Long id) {
        service.delete(access.user(), id);
        return Result.success();
    }
}
