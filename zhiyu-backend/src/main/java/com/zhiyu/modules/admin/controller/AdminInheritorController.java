package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.dto.InheritorUpdateDTO;
import com.zhiyu.modules.admin.dto.InheritorVerifyDTO;
import com.zhiyu.modules.admin.service.AdminInheritorService;
import com.zhiyu.modules.admin.vo.InheritorDetailVO;
import com.zhiyu.modules.admin.vo.InheritorListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端传承人管理控制器（P1-B1）。
 *
 * <p>提供 4 个接口：
 * <ul>
 *   <li>GET /api/admin/inheritors            — 传承人分页列表（role ∈ {2,3}）</li>
 *   <li>GET /api/admin/inheritors/{id}       — 传承人详情（role ∈ {2,3}）</li>
 *   <li>PUT /api/admin/inheritors/{id}/verify — 认证/取消认证（仅 role=3）</li>
 *   <li>PUT /api/admin/inheritors/{id}       — 编辑传承人资料（仅 role=3）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/inheritors")
@RequiredArgsConstructor
public class AdminInheritorController {

    private final AdminInheritorService adminInheritorService;

    @GetMapping
    public Result<PageVO<InheritorListItemVO>> listInheritors(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer verified,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminInheritorService.listInheritors(page, pageSize, verified, keyword));
    }

    @GetMapping("/{id}")
    public Result<InheritorDetailVO> getInheritor(@PathVariable Long id) {
        return Result.success(adminInheritorService.getInheritor(id));
    }

    @PutMapping("/{id}/verify")
    public Result<Void> verifyInheritor(
            @PathVariable Long id,
            @Valid @RequestBody InheritorVerifyDTO dto) {
        adminInheritorService.verifyInheritor(id, dto.getVerified());
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<InheritorDetailVO> updateInheritor(
            @PathVariable Long id,
            @RequestBody InheritorUpdateDTO dto) {
        return Result.success(adminInheritorService.updateInheritor(id, dto));
    }
}
