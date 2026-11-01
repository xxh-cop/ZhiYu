package com.zhiyu.modules.inheritor.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.inheritor.service.InheritorPublicService;
import com.zhiyu.modules.inheritor.service.InheritorStoryService;
import com.zhiyu.modules.inheritor.vo.InheritorPublicDetailVO;
import com.zhiyu.modules.inheritor.vo.InheritorPublicItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 传承人公开查询 Controller（M2 批 6A）。
 *
 * <p>路径前缀 {@code /api/inheritors}（复数），学生端访问。
 * 与 {@code /api/inheritor/**}（单数，传承人端，ROLE_1）区分。
 *
 * <p>权限：authenticated（默认），所有登录用户均可访问。
 */
@RestController
@RequestMapping("/api/inheritors")
@RequiredArgsConstructor
public class InheritorPublicController {

    private final InheritorPublicService inheritorPublicService;
    private final InheritorStoryService inheritorStoryService;

    /** 合成教学故事列表；明确标注非真实人物访谈。 */
    @GetMapping("/stories")
    public Result<List<InheritorStoryService.Summary>> stories() {
        return Result.success(inheritorStoryService.list());
    }

    /** 单篇故事详情，附工艺背景来源。 */
    @GetMapping("/stories/{id}")
    public Result<InheritorStoryService.Detail> story(@PathVariable String id) {
        return Result.success(inheritorStoryService.get(id));
    }

    /**
     * 接口 1：分页查询已认证传承人列表。
     */
    @GetMapping
    public Result<PageVO<InheritorPublicItemVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(inheritorPublicService.listInheritors(page, pageSize, keyword));
    }

    /**
     * 接口 2：查询传承人详情。
     *
     * @param id inheritor_profile.id
     */
    @GetMapping("/{id}")
    public Result<InheritorPublicDetailVO> detail(@PathVariable Long id) {
        return Result.success(inheritorPublicService.getInheritor(id));
    }
}
