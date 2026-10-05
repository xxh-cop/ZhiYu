package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.service.CraftProjectPublicService;
import com.zhiyu.modules.craft.vo.CraftProjectDetailVO;
import com.zhiyu.modules.craft.vo.CraftProjectItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 手作项目公开查询 Controller（M2 批 6A）。
 *
 * <p>提供学生端可见的项目查询接口：
 * <ul>
 *   <li>{@code GET /api/inheritors/{id}/projects} —— 传承人的已上架项目列表</li>
 *   <li>{@code GET /api/craft/projects/{id}} —— 已上架项目详情</li>
 * </ul>
 *
 * <p>权限：authenticated（默认），所有登录用户均可访问。
 */
@RestController
@RequiredArgsConstructor
public class CraftProjectPublicController {

    private final CraftProjectPublicService craftProjectPublicService;

    /**
     * 接口 3：查询传承人的已上架项目列表。
     *
     * @param id inheritor_profile.id
     */
    @GetMapping("/api/inheritors/{id}/projects")
    public Result<PageVO<CraftProjectItemVO>> listProjectsByInheritor(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(craftProjectPublicService.listProjectsByInheritor(id, page, pageSize));
    }

    /**
     * 接口 4：查询已上架项目详情（含步骤 + 材料 + 传承人信息）。
     *
     * @param id craft_project.id
     */
    @GetMapping("/api/craft/projects/{id}")
    public Result<CraftProjectDetailVO> projectDetail(@PathVariable Long id) {
        return Result.success(craftProjectPublicService.getProjectDetail(id));
    }
}
