package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.dto.GroupStatusDTO;
import com.zhiyu.modules.admin.service.AdminGroupService;
import com.zhiyu.modules.admin.vo.GroupDetailVO;
import com.zhiyu.modules.admin.vo.GroupListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端小组管理控制器（P1-B1）。
 *
 * <p>提供 4 个接口：
 * <ul>
 *   <li>GET    /api/admin/groups            — 小组分页列表（仅 role=3）</li>
 *   <li>GET    /api/admin/groups/{id}       — 小组详情（仅 role=3）</li>
 *   <li>PUT    /api/admin/groups/{id}/status — 变更状态（仅 role=3）</li>
 *   <li>DELETE /api/admin/groups/{id}       — 逻辑删除（仅 role=3）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final AdminGroupService adminGroupService;

    @GetMapping
    public Result<PageVO<GroupListItemVO>> listGroups(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminGroupService.listGroups(page, pageSize, status, keyword));
    }

    @GetMapping("/{id}")
    public Result<GroupDetailVO> getGroup(@PathVariable Long id) {
        return Result.success(adminGroupService.getGroup(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateGroupStatus(
            @PathVariable Long id,
            @Valid @RequestBody GroupStatusDTO dto) {
        adminGroupService.updateGroupStatus(id, dto.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        adminGroupService.deleteGroup(id);
        return Result.success();
    }
}
