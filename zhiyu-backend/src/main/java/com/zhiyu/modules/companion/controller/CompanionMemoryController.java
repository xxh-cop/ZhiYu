package com.zhiyu.modules.companion.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.companion.dto.CompanionMemorySaveDTO;
import com.zhiyu.modules.companion.dto.CompanionMemoryToggleDTO;
import com.zhiyu.modules.companion.memory.CompanionMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/companion/memory")
public class CompanionMemoryController {

    private final CompanionMemoryService service;

    @GetMapping
    public Result<Map<String, Object>> get() {
        return Result.success(service.get());
    }

    @PutMapping("/enabled")
    public Result<Map<String, Object>> enable(@RequestBody CompanionMemoryToggleDTO request) {
        return Result.success(service.enable(
                request == null ? null : request.getEnabled(),
                request == null ? null : request.getConsent()));
    }

    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody CompanionMemorySaveDTO request) {
        return Result.success(service.save(
                request == null ? null : request.getConsent(),
                request == null ? null : request.getPreferences()));
    }

    @DeleteMapping
    public Result<Map<String, Object>> deleteAll() {
        return Result.success(service.deleteAll());
    }
}
