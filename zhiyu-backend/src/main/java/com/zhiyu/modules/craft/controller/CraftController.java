package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.dto.CreateSessionDTO;
import com.zhiyu.modules.craft.dto.HeartbeatReq;
import com.zhiyu.modules.craft.dto.SessionEventBatchReq;
import com.zhiyu.modules.craft.dto.SessionStatusDTO;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.service.CraftRecommendService;
import com.zhiyu.modules.craft.service.CraftSessionEventService;
import com.zhiyu.modules.craft.service.CraftSessionService;
import com.zhiyu.modules.craft.vo.CraftSessionVO;
import com.zhiyu.modules.craft.vo.HeartbeatResult;
import com.zhiyu.modules.craft.vo.RecommendVO;
import com.zhiyu.modules.craft.vo.SessionEventBatchResult;
import com.zhiyu.security.BusinessAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    private final CraftSessionEventService craftSessionEventService;
    private final BusinessAccess access;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
        // S1（2026-11-04）：推荐落库含情绪标签，属敏感处理，需已同意隐私政策
        Long userId = access.consentedUser();
        return Result.success(craftRecommendService.recommend(userId, sessionId, skillLevel, minutes));
    }

    // ── T1.9b-backend 项目步骤 ──

    /**
     * 查询项目步骤的结构化视图（2026-11-05 分层下架）。
     *
     * <p>上架项目正常下发；已下架/已归档返回步骤（可回看）+ 会话状态 +
     * readOnly/canExport/canReview + 截止时间 + noticeKey/notice，不再 1004。
     * 仅项目不存在或待审时 1004。
     *
     * @param id 项目 ID
     * @return 结构化步骤视图
     */
    @GetMapping("/projects/{id}/steps")
    public Result<com.zhiyu.modules.craft.vo.CraftProjectStepsVO> listSteps(@PathVariable Long id) {
        return Result.success(craftProjectService.listSteps(id));
    }

    // ── T1.6 创作会话 ──

    @PostMapping("/session")
    public Result<CraftSessionVO> createSession(@Valid @RequestBody CreateSessionDTO dto) {
        // S1（2026-11-04）：创作会话可关联情绪会话并产生心流数据，挂同意门
        Long userId = access.consentedUser();
        return Result.success(craftSessionService.create(userId, dto.getProjectId(), dto.getEmotionSessionId()));
    }

    @PutMapping("/session/{id}/status")
    public Result<CraftSessionVO> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody SessionStatusDTO dto) {
        Long userId = access.consentedUser();
        return Result.success(craftSessionService.changeStatus(userId, id, dto.getAction()));
    }

    @GetMapping("/session/{id}")
    public Result<CraftSessionVO> getProgress(@PathVariable Long id) {
        Long userId = access.consentedUser();
        return Result.success(craftSessionService.getProgress(userId, id));
    }

    // ── T1.7 心跳 ──

    @PostMapping("/session/{id}/heartbeat")
    public Result<HeartbeatResult> heartbeat(
            @PathVariable Long id,
            @Valid @RequestBody HeartbeatReq req) {
        // S1（2026-11-04）：心跳写入停留/焦躁等行为信号，挂同意门
        Long userId = access.consentedUser();
        return Result.success(craftSessionService.heartbeat(userId, id, req));
    }

    // ── 2026-11-05 会话行为事件批量上报（心流度量配套） ──

    /**
     * 批量上报会话行为事件（生命周期、音频播放等沉浸强信号）。
     *
     * <p>幂等：按 (session_id, client_seq) 去重，重复序号计 deduped 不算错误。
     * 仅白名单事件类型可上报；服务端派生类型一律 1001。
     */
    @PostMapping("/session/{id}/events")
    public Result<SessionEventBatchResult> ingestEvents(
            @PathVariable Long id,
            @Valid @RequestBody SessionEventBatchReq req) {
        // 行为事件属行为信号写入，挂同意门（与心跳同口径）
        Long userId = access.consentedUser();
        return Result.success(craftSessionEventService.ingest(userId, id, req));
    }

    // ── 2026-11-05 下架窗口导出 ──

    /**
     * 导出本人在合规下架窗口内的会话创作数据（附件下载）。
     *
     * <p>仅 TakedownStateResolver 判定 canExport 可导出；窗口过期/不在窗口/开关关闭
     * 一律 3001 + 可读原因（HTTP 200 Result 体），非本人 1003，未登录 1002。
     * 登录口径与 GET /api/user/data-export 一致（access.user()，行使既有数据权利不挂同意门）。
     * 成功导出写入 craft_session_event 的 export 事件。
     */
    @GetMapping("/session/{id}/export")
    public org.springframework.http.ResponseEntity<byte[]> exportSession(@PathVariable Long id) throws Exception {
        Long userId = access.user();
        java.util.Map<String, Object> payload = craftSessionService.exportSession(userId, id);
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Object projectId = ((java.util.Map<?, ?>) payload.get("project")).get("id");
        String filename = "zhiyu-craft-session-" + id + "-p" + projectId + ".json";
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/json;charset=UTF-8"))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition",
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(filename, java.nio.charset.StandardCharsets.US_ASCII)
                                .build().toString())
                .body(body);
    }
}
