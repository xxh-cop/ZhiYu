package com.zhiyu.modules.user.service;

import com.zhiyu.modules.auth.vo.UserVO;
import com.zhiyu.modules.user.dto.PasswordChangeDTO;
import com.zhiyu.modules.user.dto.UserProfileUpdateDTO;

/**
 * 用户个人资料服务（批次 4.5）。
 */
public interface UserProfileService {

    /**
     * 更新个人资料（只更新非 null 字段）。
     *
     * @param userId 当前用户 ID
     * @param dto    更新请求
     * @return 最新 UserVO
     */
    UserVO updateProfile(Long userId, UserProfileUpdateDTO dto);

    /**
     * 修改密码。
     *
     * @param userId 当前用户 ID
     * @param dto    密码修改请求
     * @throws com.zhiyu.common.exception.BusinessException code=2001 旧密码不匹配
     */
    void changePassword(Long userId, PasswordChangeDTO dto);
}
