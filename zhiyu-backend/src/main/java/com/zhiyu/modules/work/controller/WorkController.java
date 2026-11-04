package com.zhiyu.modules.work.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.work.dto.CommentReq;
import com.zhiyu.modules.work.dto.LikeReq;
import com.zhiyu.modules.work.dto.WorkPublishReq;
import com.zhiyu.modules.work.service.WorkInteractionService;
import com.zhiyu.modules.work.service.WorkService;
import com.zhiyu.modules.work.vo.CommentPageVO;
import com.zhiyu.modules.work.vo.CommentVO;
import com.zhiyu.modules.work.vo.LikeVO;
import com.zhiyu.modules.work.vo.WorkPageVO;
import com.zhiyu.modules.work.vo.WorkVO;
import com.zhiyu.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 作品社区控制器（T1.8a）。
 *
 * <p>GET  /api/works                  —— 作品社区流（分页）
 * <p>POST /api/works                  —— 发布作品
 * <p>GET  /api/works/{id}             —— 作品详情
 * <p>POST /api/works/{id}/like        —— 点赞/收藏（toggle）
 * <p>POST /api/works/{id}/comments    —— 发表留暖评论
 * <p>GET  /api/works/{id}/comments    —— 评论列表（分页）
 */
@Slf4j
@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;
    private final WorkInteractionService workInteractionService;
    private final com.zhiyu.security.BusinessAccess access;

    @GetMapping
    public Result<WorkPageVO> listWorks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(workService.listWorks(page, size));
    }

    @PostMapping
    public Result<WorkVO> publish(@Valid @RequestBody WorkPublishReq req) {
        // S1（2026-11-04）：发布产生 UGC 并触发 AI 寄语生成，挂隐私同意门
        Long userId = access.consentedUser();
        return Result.success(workService.publish(userId, req));
    }

    // ── T1.9c-backend 作品详情 ──
    @GetMapping("/{id}")
    public Result<WorkVO> getWork(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(workService.getWork(userId, id));
    }

    @PostMapping("/{id}/like")
    public Result<LikeVO> toggleLike(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LikeReq req) {
        // S1（2026-11-04）：点赞/收藏是个人行为记录落库，挂同意门
        Long userId = access.consentedUser();
        Integer type = (req != null) ? req.getType() : null;
        return Result.success(workInteractionService.toggleLike(userId, id, type));
    }

    @PostMapping("/{id}/comments")
    public Result<CommentVO> comment(
            @PathVariable Long id,
            @Valid @RequestBody CommentReq req) {
        // S1（2026-11-04）：评论写入落库并经安全过滤，挂同意门
        Long userId = access.consentedUser();
        return Result.success(workInteractionService.comment(userId, id, req.getContent()));
    }

    @GetMapping("/{id}/comments")
    public Result<CommentPageVO> listComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(workInteractionService.listComments(id, page, size));
    }
}
