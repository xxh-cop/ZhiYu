package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.dto.RecommendClickDTO;
import com.zhiyu.modules.craft.service.RecommendEventService;
import com.zhiyu.security.BusinessAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推荐点击事件控制器（20261028）。
 *
 * <p>点击失败不阻塞学生端跳转；接口本身仍校验归属与项目关系。
 */
@RestController
@RequestMapping("/api/craft/recommend")
@RequiredArgsConstructor
public class CraftRecommendEventController {

    private final RecommendEventService service;
    private final BusinessAccess access;

    @PostMapping("/click")
    public Result<Void> click(@Valid @RequestBody RecommendClickDTO dto) {
        // S1（2026-11-04）：推荐点击是行为事件落库，挂隐私同意门
        service.click(access.consentedUser(), dto);
        return Result.success();
    }
}
