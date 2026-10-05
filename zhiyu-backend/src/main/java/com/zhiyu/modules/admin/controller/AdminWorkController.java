package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.dto.WorkStatusDTO;
import com.zhiyu.modules.admin.service.AdminWorkService;
import com.zhiyu.modules.admin.vo.WorkDetailVO;
import com.zhiyu.modules.admin.vo.WorkListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端作品管理控制器（P1-B1）。
 *
 * <p>提供 4 个接口：
 * <ul>
 *   <li>GET    /api/admin/works            — 作品分页列表（role ∈ {2,3}）</li>
 *   <li>GET    /api/admin/works/{id}       — 作品详情（role ∈ {2,3}）</li>
 *   <li>PUT    /api/admin/works/{id}/status — 变更状态（仅 role=3）</li>
 *   <li>DELETE /api/admin/works/{id}       — 逻辑删除（仅 role=3）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/works")
@RequiredArgsConstructor
public class AdminWorkController {

    private final AdminWorkService adminWorkService;

    @GetMapping
    public Result<PageVO<WorkListItemVO>> listWorks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer visible,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminWorkService.listWorks(page, pageSize, status, visible, keyword));
    }

    @GetMapping("/{id}")
    public Result<WorkDetailVO> getWork(@PathVariable Long id) {
        return Result.success(adminWorkService.getWork(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateWorkStatus(
            @PathVariable Long id,
            @Valid @RequestBody WorkStatusDTO dto) {
        adminWorkService.updateWorkStatus(id, dto.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteWork(@PathVariable Long id) {
        adminWorkService.deleteWork(id);
        return Result.success();
    }
}
