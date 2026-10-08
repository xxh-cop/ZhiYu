package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.dto.CreateSessionDTO;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.dto.SessionStatusDTO;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.service.CraftRecommendService;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.CraftSessionVO;
import com.zhiyu.modules.craft.vo.CraftStepVO;
import com.zhiyu.modules.craft.vo.HeartbeatResult;
import com.zhiyu.modules.craft.vo.RecommendVO;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 手作模块控制器（T1.5 / T1.6 / T1.9b-backend）。
 *
 * <p>GET  /api/craft/recommend             —— 根据当前心情推荐手作（T1.5）
 * <p>GET  /api/craft/projects/{id}/steps   —— 项目步骤列表（T1.9b-backend）
 * <p>POST /api/craft/session                —— 创建创作会话（T1.6）
 * <p>PUT  /api/craft/session/{id}/status    —— 推进会话状态机（T1.6）
 * <p>GET  /api/craft/session/{id}           —— 查询会话进度（T1.6）
 * <p>POST /api/craft/session/{id}/heartbeat —— 上报步骤心跳（T1.7）
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
    private final CraftSessionService craftSessionService;
    private final CraftProjectService craftProjectService;

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

    // ── T1.9b-backend 项目步骤 ──

    /**
     * 查询项目步骤列表（按 stepNo 升序）。
     *
     * @param id 项目 ID
     * @return 步骤列表
     */
    @GetMapping("/projects/{id}/steps")
    public Result<List<CraftStepVO>> listSteps(@PathVariable Long id) {
        return Result.success(craftProjectService.listSteps(id));
    }

    // ── T1.6 创作会话 ──

    @PostMapping("/session")
    public Result<CraftSessionVO> createSession(@Valid @RequestBody CreateSessionDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.success(craftSessionService.create(userId, dto.getProjectId(), dto.getEmotionSessionId()));
    }

    @PutMapping("/session/{id}/status")
    public Result<CraftSessionVO> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody SessionStatusDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.success(craftSessionService.changeStatus(userId, id, dto.getAction()));
    }

    @GetMapping("/session/{id}")
    public Result<CraftSessionVO> getProgress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(craftSessionService.getProgress(userId, id));
    }

    // ── T1.7 心跳 ──

    @PostMapping("/session/{id}/heartbeat")
    public Result<HeartbeatResult> heartbeat(
            @PathVariable Long id,
            @Valid @RequestBody HeartbeatReq req) {
        Long userId = UserContext.getUserId();
        return Result.success(craftSessionService.heartbeat(userId, id, req));
    }
}
