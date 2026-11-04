package com.zhiyu.modules.emotion.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.emotion.service.EmotionBaselineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 情绪基线偏离提醒（AM1，2026-11-04）。
 *
 * <p>GET  /api/emotion/baseline-nudge         —— 查询是否提示（隐私同意门）
 * <p>PUT  /api/emotion/baseline-nudge/setting —— 开关设置（登录即可）
 *
 * <p>只做提醒不做判断：返回 {enabled, nudge, level, actionType}，不含任何原始情绪记录；
 * 提示文案与「本系统不能替代专业心理咨询」声明由前端按 level 渲染。
 */
@RestController
@RequiredArgsConstructor
public class EmotionBaselineController {

    private final EmotionBaselineService service;

    @GetMapping("/api/emotion/baseline-nudge")
    public Result<?> nudge() {
        return Result.success(service.nudge());
    }

    @PutMapping("/api/emotion/baseline-nudge/setting")
    public Result<?> setting(@RequestBody Map<String, Object> body) {
        Object v = body.get("enabled");
        if (!(v instanceof Boolean)) {
            throw new com.zhiyu.common.exception.BusinessException(1001, "enabled 必须为布尔值");
        }
        service.setEnabled((Boolean) v);
        return Result.success();
    }
}
