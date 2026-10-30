package com.zhiyu.modules.heritage.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 非遗只读目录控制器（H1a Task 1）。
 *
 * <p>{@code GET /api/heritage/catalog} 返回登录学生可读的原创纹样目录；
 * 鉴权沿用现有 Spring Security（{@code anyRequest().authenticated()}），
 * 未登录由过滤器链返回 HTTP 401 + code 1002。
 */
@RestController
@RequestMapping("/api/heritage")
@RequiredArgsConstructor
public class HeritageController {

    private final HeritageCatalogService service;

    @GetMapping("/catalog")
    public Result<HeritageCatalog> catalog() {
        return Result.success(service.catalog());
    }
}
