package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审核辅助建议（BL，2026-11-05）。
 *
 * <p>按需调用：审核员在审核工作台显式触发，无自动/批量预跑。
 * AI 只给建议不处置；响应固定带 {@code disclaimer} 字段。
 * 鉴权：{@code /api/admin/**} 兜底仅 role=3（SecurityConfig）。
 */
@RestController
@RequiredArgsConstructor
public class AdminModerationController {

    private final AdminModerationService moderationService;

    /** 作品审核辅助建议，对应 {@code POST /api/admin/works/{id}/ai-suggest}。 */
    @PostMapping("/api/admin/works/{id}/ai-suggest")
    public Result<Map<String, Object>> suggestWork(@PathVariable long id) {
        return Result.success(moderationService.suggest("work", id));
    }

    /** 评论审核辅助建议，对应 {@code POST /api/admin/comments/{id}/ai-suggest}。 */
    @PostMapping("/api/admin/comments/{id}/ai-suggest")
    public Result<Map<String, Object>> suggestComment(@PathVariable long id) {
        return Result.success(moderationService.suggest("comment", id));
    }
}
