package com.zhiyu.modules.user.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.auth.vo.UserVO;
import com.zhiyu.modules.user.dto.PasswordChangeDTO;
import com.zhiyu.modules.user.dto.UserProfileUpdateDTO;
import com.zhiyu.modules.user.service.UserProfileService;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人资料控制器（批次 4.5）。
 *
 * <p>PUT /api/user/profile  —— 更新个人资料
 * <br>PUT /api/user/password —— 修改密码
 *
 * <p>两个接口均需 authenticated（SecurityConfig 的 anyRequest().authenticated() 已覆盖）。
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 更新个人资料（昵称、头像、学校、简介）。
     */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.success(userProfileService.updateProfile(userId, dto));
    }

    /**
     * 修改密码。
     *
     * <p>JWT 无状态，修改后当前 token 仍有效，前端提示「下次登录用新密码」。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        Long userId = UserContext.getUserId();
        userProfileService.changePassword(userId, dto);
        return Result.success();
    }
}
