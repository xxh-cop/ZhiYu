package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.service.CraftRecommendService;
import com.zhiyu.modules.craft.vo.RecommendVO;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手作推荐控制器（T1.5）。
 *
 * <p>GET /api/craft/recommend —— 根据当前心情推荐手作。
 *
 * <p>JWT 鉴权走 {@code SecurityConfig} 的 {@code anyRequest().authenticated()}，
 * 无需额外配置。
 */
@Slf4j
@RestController
@RequestMapping("/api/craft")
@RequiredArgsConstructor
public class CraftController {

    private final CraftRecommendService craftRecommendService;

    /**
     * 根据当前心情推荐手作。
     *
     * @param sessionId  情绪会话 ID（可空）
     * @param skillLevel 用户技能等级 1-3，默认 1
     * @param minutes    可用时间（分钟），默认 60
     * @return 推荐结果
     */
    @GetMapping("/recommend")
    public Result<RecommendVO> recommend(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(defaultValue = "1") Integer skillLevel,
            @RequestParam(defaultValue = "60") Integer minutes) {
        Long userId = UserContext.getUserId();
        return Result.success(craftRecommendService.recommend(userId, sessionId, skillLevel, minutes));
    }
}
