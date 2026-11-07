package com.zhiyu.modules.work.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.work.service.SoftTierRecordService;
import com.zhiyu.security.UserContext;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 软档申诉（2026-11-07，设计 §2.3 逃生通道）。
 *
 * <p>作者对「展示受限」处置的申诉入口：申诉即升级为人工必审（等同抽中），SLA 重新起算。
 * 仅本人可申诉本人内容；幂等（已申诉直接成功）。处置链开关默认 false，关闭时 1001。
 */
@RestController
@RequiredArgsConstructor
public class SoftTierAppealController {

    private final SoftTierRecordService recordService;

    public record Appeal(
            @NotNull(message = "targetType 不能为空")
            @Pattern(regexp = "work|comment", message = "targetType 只能是 work/comment") String targetType,
            @NotNull(message = "targetId 不能为空") Long targetId,
            @jakarta.validation.constraints.NotBlank(message = "请填写申诉理由")
            @jakarta.validation.constraints.Size(max = 200, message = "申诉理由不能超过 200 字") String reason) {
    }

    @PostMapping("/api/soft-tier-appeals")
    public Result<Void> appeal(@jakarta.validation.Valid @RequestBody Appeal a) {
        recordService.appeal(UserContext.getUserId(), a.targetType(), a.targetId(), a.reason());
        return Result.success();
    }
}
