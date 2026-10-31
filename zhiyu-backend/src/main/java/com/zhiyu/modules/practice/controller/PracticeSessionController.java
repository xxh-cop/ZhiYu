package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.dto.CreatePracticeSessionDTO;
import com.zhiyu.modules.practice.dto.PracticeSessionActionDTO;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import com.zhiyu.security.UserContext;
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

    @PostMapping
    public Result<PracticeSessionVO> create(@Valid @RequestBody CreatePracticeSessionDTO request) {
        return Result.success(service.create(UserContext.getUserId(), request));
    }

    @GetMapping
    public Result<List<PracticeSessionVO>> list(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(service.list(UserContext.getUserId(), limit));
    }

    @GetMapping("/{id}")
    public Result<PracticeSessionVO> get(@PathVariable Long id) {
        return Result.success(service.get(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}/status")
    public Result<PracticeSessionVO> transition(
            @PathVariable Long id,
            @Valid @RequestBody PracticeSessionActionDTO request) {
        return Result.success(service.transition(UserContext.getUserId(), id, request.getAction()));
    }
}
