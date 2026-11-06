package com.zhiyu.modules.craft.controller;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.craft.service.impl.StuckGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 卡点引导用户级总开关（2026-11-05，设计稿 docs/stuck-guidance-design-20261105.md §2）。
 *
 * <p>GET /api/craft/stuck-guide/setting —— 查询开关
 * <p>PUT /api/craft/stuck-guide/setting —— {enabled: true|false} 永久关闭/开启
 *
 * <p>「AI 的关心如果不可拒绝，本身就是控制感的剥夺」——本端点是「可拒绝」的后端落点：
 * 关闭后任何路径不再出现卡点引导。
 */
@RestController
@RequiredArgsConstructor
public class StuckGuideController {

    private final StuckGuideService service;

    @GetMapping("/api/craft/stuck-guide/setting")
    public Result<?> getSetting() {
        return Result.success(Map.of("enabled", service.currentEnabled()));
    }

    @PutMapping("/api/craft/stuck-guide/setting")
    public Result<?> putSetting(@RequestBody Map<String, Object> body) {
        Object v = body.get("enabled");
        if (!(v instanceof Boolean)) {
            throw new BusinessException(1001, "enabled 必须为布尔值");
        }
        service.setCurrentEnabled((Boolean) v);
        return Result.success();
    }
}
