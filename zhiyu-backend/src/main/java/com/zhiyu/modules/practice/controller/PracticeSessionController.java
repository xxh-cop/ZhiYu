package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.dto.CreatePracticeSessionDTO;
import com.zhiyu.modules.practice.dto.PracticeSessionActionDTO;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import com.zhiyu.security.BusinessAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/practice/sessions")
@RequiredArgsConstructor
public class PracticeSessionController {

    private final PracticeSessionService service;
    private final BusinessAccess access;

    @PostMapping
    public Result<PracticeSessionVO> create(@Valid @RequestBody CreatePracticeSessionDTO request) {
        // S1（2026-11-04）：练习会话含加密上下文与情绪标签，挂隐私同意门
        return Result.success(service.create(access.consentedUser(), request));
    }

    @GetMapping
    public Result<List<PracticeSessionVO>> list(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(service.list(access.consentedUser(), limit));
    }

    @GetMapping("/{id}")
    public Result<PracticeSessionVO> get(@PathVariable Long id) {
        return Result.success(service.get(access.consentedUser(), id));
    }

    @PutMapping("/{id}/status")
    public Result<PracticeSessionVO> transition(
            @PathVariable Long id,
            @Valid @RequestBody PracticeSessionActionDTO request) {
        return Result.success(service.transition(access.consentedUser(), id, request.getAction()));
    }
}
