package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.SoftTierPoolService;
import com.zhiyu.security.UserContext;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 软档抽检池（2026-11-07，设计 docs/moderation-soft-tier-disposition-chain-20261107.md）。
 *
 * <p>仅 role=3（{@code /api/admin/**} 兜底；池内含内容原文，咨询师 role=2 不进池）。
 * 处置链开关 {@code zhiyu.moderation.soft-tier.disposition.enabled} 默认 false，
 * 关闭时全部端点返回 1001「软档处置链未开启」。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/soft-tier-pool")
public class AdminSoftTierPoolController {

    private final SoftTierPoolService poolService;

    /** 复核队列（默认 OPEN = PENDING + ESCALATED；ESCALATED 置顶、sla_due_at 升序）。 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(required = false) String status) {
        return Result.success(poolService.list(page, status));
    }

    /** 复核详情（含内容节选与 ai_audit_log 关联；作品封面为 1h 签名临时地址）。 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return Result.success(poolService.detail(id, UserContext.getUserId()));
    }

    /** 看板聚合（当日入池量/待复核量/超时数/申诉数/裁决分布，一次返回）。 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(poolService.stats());
    }

    // 申诉单列表在 AdminSoftTierAppealController（契约路径 /api/admin/soft-tier-appeals）——
    // 本类类级前缀是 /api/admin/soft-tier-pool，方法级写绝对路径会被拼接（联调 F2 的教训）。

    public record Verdict(
            @NotNull(message = "action 不能为空")
            @Pattern(regexp = "CLEAR|UPHOLD|BLOCK", message = "action 只能是 CLEAR/UPHOLD/BLOCK") String action,
            @Size(max = 255, message = "裁决理由不能超过 255 字") String reason) {
    }

    /** 裁决：CLEAR 恢复曝光 / UPHOLD 维持至窗口结束 / BLOCK 改判拦截（理由必填，走既有下架状态机）。 */
    @PostMapping("/{id}/verdict")
    public Result<Void> verdict(@PathVariable long id, @jakarta.validation.Valid @RequestBody Verdict v) {
        poolService.verdict(id, UserContext.getUserId(), v.action(), v.reason());
        return Result.success();
    }
}
