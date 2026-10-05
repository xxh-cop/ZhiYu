package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.dto.UserRoleDTO;
import com.zhiyu.modules.admin.dto.UserStatusDTO;
import com.zhiyu.modules.admin.dto.UserUpdateDTO;
import com.zhiyu.modules.admin.service.AdminUserService;
import com.zhiyu.modules.admin.vo.UserDetailVO;
import com.zhiyu.modules.admin.vo.UserListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端用户管理控制器（P1-A1）。
 *
 * <p>提供 6 个接口：
 * <ul>
 *   <li>GET    /api/admin/users            — 用户分页列表（role ∈ {2,3}）</li>
 *   <li>GET    /api/admin/users/{id}       — 用户详情（role ∈ {2,3}）</li>
 *   <li>PUT    /api/admin/users/{id}       — 编辑用户（仅 role=3）</li>
 *   <li>PUT    /api/admin/users/{id}/status — 切换状态（仅 role=3）</li>
 *   <li>PUT    /api/admin/users/{id}/role   — 变更角色（仅 role=3）</li>
 *   <li>DELETE /api/admin/users/{id}       — 逻辑删除（仅 role=3）</li>
 * </ul>
 *
 * <p>权限：{@code /api/admin/**} 已由 {@code SecurityConfig} 限定 role ∈ {1,2,3}，
 * 敏感操作（PUT/DELETE）由 SecurityConfig 精确匹配限定 role=3。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 用户分页列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页大小（默认 10）
     * @param role     角色筛选（可空）
     * @param status   状态筛选（可空）
     * @param keyword  关键字（匹配 username 或 nickname，可空）
     */
    @GetMapping
    public Result<PageVO<UserListItemVO>> listUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminUserService.listUsers(page, pageSize, role, status, keyword));
    }

    /**
     * 用户详情。
     *
     * @param id 用户 ID
     */
    @GetMapping("/{id}")
    public Result<UserDetailVO> getUser(@PathVariable Long id) {
        return Result.success(adminUserService.getUser(id));
    }

    /**
     * 编辑用户（nickname / school / avatarUrl）。仅管理员。
     *
     * @param id  用户 ID
     * @param dto 编辑请求体
     */
    @PutMapping("/{id}")
    public Result<UserDetailVO> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateDTO dto) {
        return Result.success(adminUserService.updateUser(id, dto));
    }

    /**
     * 切换用户状态（启用/禁用）。仅管理员。
     *
     * @param id  用户 ID
     * @param dto 状态请求体
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusDTO dto) {
        adminUserService.updateStatus(id, dto.getStatus());
        return Result.success();
    }

    /**
     * 变更用户角色。仅管理员。
     *
     * @param id  用户 ID
     * @param dto 角色请求体
     */
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleDTO dto) {
        adminUserService.updateRole(id, dto.getRole());
        return Result.success();
    }

    /**
     * 逻辑删除用户。仅管理员。
     *
     * @param id 用户 ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return Result.success();
    }
}
