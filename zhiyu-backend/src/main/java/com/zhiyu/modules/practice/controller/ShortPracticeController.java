package com.zhiyu.modules.practice.controller;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.practice.model.ShortPracticeCatalog;
import com.zhiyu.modules.practice.service.ShortPracticeService;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学生端短练习目录：只读、按时间/心情排序，不保存练习过程原文。 */
@RestController
@RequestMapping("/api/practice/short-practices")
@RequiredArgsConstructor
public class ShortPracticeController {

    private final ShortPracticeService service;
    private final BusinessAccess access;

    @GetMapping
    public Result<List<ShortPracticeCatalog.ShortPractice>> list(
            @RequestParam(required = false) Integer minutes,
            @RequestParam(required = false) String mood,
            @RequestParam(required = false) String kind) {
        access.user();
        if (minutes != null && (minutes < 1 || minutes > 60)) {
            throw new BusinessException(1001, "分钟数需在 1 到 60 之间");
        }
        return Result.success(service.recommend(minutes, mood, kind));
    }

    @GetMapping("/{id}")
    public Result<ShortPracticeCatalog.ShortPractice> get(@PathVariable String id) {
        access.user();
        return Result.success(service.find(id));
    }
}
