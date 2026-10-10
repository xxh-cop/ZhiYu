package com.zhiyu.modules.user.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.vo.UserVO;
import com.zhiyu.modules.emotion.entity.EmotionNote;
import com.zhiyu.modules.emotion.mapper.EmotionNoteMapper;
import com.zhiyu.modules.user.dto.PasswordChangeDTO;
import com.zhiyu.modules.user.dto.UserProfileUpdateDTO;
import com.zhiyu.modules.user.service.UserProfileService;
import com.zhiyu.modules.user.vo.UserStatsVO;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
import com.zhiyu.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户个人资料控制器（批次 4.5）。
 *
 * <p>PUT /api/user/profile  —— 更新个人资料
 * <br>PUT /api/user/password —— 修改密码
 * <br>GET  /api/user/stats   —— 学生端「我的」统计数据
 *
 * <p>所有接口均需 authenticated（SecurityConfig 的 anyRequest().authenticated() 已覆盖）。
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final UserMapper userMapper;
    private final WorkMapper workMapper;
    private final EmotionNoteMapper emotionNoteMapper;

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

    /**
     * 学生端「我的」统计数据（批次 6-E）。
     *
     * <p>返回注册天数、作品数、便签数。
     */
    @GetMapping("/stats")
    public Result<UserStatsVO> getStats() {
        Long userId = UserContext.getUserId();

        User user = userMapper.selectById(userId);
        long days = 1;
        if (user != null && user.getCreateTime() != null) {
            days = Math.max(1, Duration.between(user.getCreateTime(), LocalDateTime.now()).toDays());
        }

        Long workCount = workMapper.selectCount(
                new LambdaQueryWrapper<Work>().eq(Work::getUserId, userId));

        Long noteCount = emotionNoteMapper.selectCount(
                new LambdaQueryWrapper<EmotionNote>().eq(EmotionNote::getUserId, userId));

        UserStatsVO vo = new UserStatsVO();
        vo.setDaysSinceRegister(days);
        vo.setWorkCount(workCount);
        vo.setNoteCount(noteCount);
        return Result.success(vo);
    }
}
