package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.AdminExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 导出审计接口（2026-11-04 N2），对应 {@code /api/admin/export-logs}。
 *
 * <p>仅管理员（role=3，服务层再校验）；评论导出当前不开放（返回 3001）。
 */
@RestController
@RequestMapping("/api/admin/export-logs")
@RequiredArgsConstructor
public class AdminExportController {

    private final AdminExportService service;

    /** 导出登记请求体。 */
    public record RegisterReq(
            @NotBlank @Size(max = 20) String targetType,
            @Size(max = 500) String filterSummary,
            @NotBlank @Size(max = 500) String fieldList,
            @NotNull Integer recordCount,
            @NotBlank @Size(max = 200) String purpose) {
    }

    /** 登记一次导出，对应 {@code POST /api/admin/export-logs}。 */
    @PostMapping
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterReq req) {
        return Result.success(service.register(req.targetType(), req.filterSummary(),
                req.fieldList(), req.recordCount(), req.purpose()));
    }

    /** 审计自查分页，对应 {@code GET /api/admin/export-logs}。 */
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(required = false) String targetType) {
        return Result.success(service.list(page, targetType));
    }
}
