package com.zhiyu.modules.admin.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.admin.dto.UserUpdateDTO;
import com.zhiyu.modules.admin.vo.UserDetailVO;
import com.zhiyu.modules.admin.vo.UserListItemVO;

/**
 * 管理端用户管理服务（P1-A1）。
 *
 * <p>提供用户列表分页查询、详情、编辑、状态切换、角色变更、逻辑删除。
 * 复用 {@link com.zhiyu.modules.auth.mapper.UserMapper}（BaseMapper）。
 */
public interface AdminUserService {

    /**
     * 用户分页列表。
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @param role     角色筛选（可空）
     * @param status   状态筛选（可空）
     * @param keyword  关键字（匹配 username 或 nickname，可空）
     * @return 分页结果
     */
    PageVO<UserListItemVO> listUsers(Integer page, Integer pageSize, Integer role, Integer status, String keyword);

    /**
     * 用户详情。
     *
     * @param id 用户 ID
     * @return 用户详情 VO
     */
    UserDetailVO getUser(Long id);

    /**
     * 编辑用户（nickname / school / avatarUrl）。
     *
     * @param id  用户 ID
     * @param dto 编辑请求体
     * @return 更新后的用户详情 VO
     */
    UserDetailVO updateUser(Long id, UserUpdateDTO dto);

    /**
     * 切换用户状态（启用/禁用）。
     *
     * @param id     用户 ID
     * @param status 0 禁用 / 1 正常
     */
    void updateStatus(Long id, Integer status);

    /**
     * 变更用户角色。
     *
     * @param id   用户 ID
     * @param role 角色码 0-3
     */
    void updateRole(Long id, Integer role);

    /**
     * 逻辑删除用户。
     *
     * @param id 用户 ID
     */
    void deleteUser(Long id);
}
