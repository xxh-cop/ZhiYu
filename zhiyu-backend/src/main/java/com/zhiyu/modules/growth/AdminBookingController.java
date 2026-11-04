package com.zhiyu.modules.growth;

import com.zhiyu.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端预约反查（AU2，2026-11-04）。
 *
 * <p>类级不设 {@code @RequestMapping}，方法级写全路径（同 CareWorkflowController 模式），
 * 避免与学生端 {@code /api/growth} 前缀混淆。鉴权：SecurityConfig 的
 * {@code /api/admin/**} 兜底要求 role=3，服务层再显式复核一次。
 */
@RestController
@RequiredArgsConstructor
public class AdminBookingController {

    private final GrowthService service;

    /** 按预约 ID 反查预约详情与所属节目摘要，对应 {@code GET /api/admin/bookings/{id}}。 */
    @GetMapping("/api/admin/bookings/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return Result.success(service.adminBookingDetail(id));
    }
}
