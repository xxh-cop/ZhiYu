package com.zhiyu.modules.notification.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知接口，对应 {@code openapi/openapi.yaml} 的 {@code /api/notifications/**}。
 *
 * <p>全部接口要求登录（SecurityConfig 兜底 authenticated），
 * 服务层只按 UserContext 的本人 ID 过滤，越权标记返回 1003。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 未读数，对应 {@code GET /api/notifications/unread-count}。 */
    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount() {
        // 未读数是计数语义（前端做数值比较），用 Integer 返回 JSON number；
        // 雪花 ID 才按字符串（JacksonConfig 全局 Long→String，Integer 不受影响）
        return Result.success(Map.of("unread", (int) Math.min(notificationService.unreadCount(), Integer.MAX_VALUE)));
    }

    /** 分页列表（可选按类型筛选），对应 {@code GET /api/notifications}。 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String type) {
        return Result.success(notificationService.list(page, size, type));
    }

    /** 标记单条已读（幂等），对应 {@code POST /api/notifications/{id}/read}。 */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.success();
    }

    /** 全部标记已读（幂等），对应 {@code POST /api/notifications/read-all}。 */
    @PostMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.success(Map.of("updated", notificationService.markAllRead()));
    }

    /** 删除单条本人通知（AQ4），对应 {@code DELETE /api/notifications/{id}}。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public Result<Void> deleteOne(@PathVariable Long id) {
        notificationService.deleteOne(id);
        return Result.success();
    }

    /** 批量清空本人通知（AQ4，可按类型过滤），对应 {@code DELETE /api/notifications?type=}。 */
    @org.springframework.web.bind.annotation.DeleteMapping
    public Result<Map<String, Integer>> clearAll(@RequestParam(required = false) String type) {
        return Result.success(Map.of("deleted", notificationService.clearAll(type)));
    }
}
