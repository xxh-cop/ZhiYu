package com.zhiyu.modules.admin.controller;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.admin.service.ModerationShadowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 软档影子模式统计（2026-11-07）：只读观测「若开启软档会怎样」。
 *
 * <p>仅 role=3（{@code /api/admin/**} 兜底，与软档池同口径）。返回内容只有计数与比率，
 * 不含任何内容原文。接口<b>不</b>随影子开关 gated——开关关闭后仍需能读到此前采集窗口的数据。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/moderation")
public class AdminModerationShadowController {

    private final ModerationShadowService shadowService;

    /**
     * 影子统计：主指标 softRate（落软档/已判定）、覆盖率与保守下界、无害率（落软档中人工终审通过占比）、
     * 分层明细（内容类型/校园）、provider 分桶（unknown 单独成桶）、日预算截断标记与 degraded 降级标记。
     *
     * @param from     窗口起始日（yyyy-MM-dd，缺省 = to 前 6 天），按对象送审时间过滤
     * @param to       窗口截止日（yyyy-MM-dd，含当日，缺省 = 今天）
     * @param provider 归属过滤：缺省 = production（反向排除演示来源清单，其余含 unknown 存量都算生产——
     *                 云端换供应商不会静默归零）；传具体 provider 值单独取该归属；传 all 不过滤。
     *                 ratio 定义与分母口径、degraded 判定不随本参数变化（仅总体集合过滤）
     */
    @GetMapping("/shadow-stats")
    public Result<Map<String, Object>> shadowStats(@RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to,
                                                   @RequestParam(required = false) String provider) {
        LocalDate toDate = parseDate(to, LocalDate.now());
        LocalDate fromDate = parseDate(from, toDate.minusDays(6));
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(1001, "from 不能晚于 to");
        }
        return Result.success(shadowService.stats(fromDate, toDate, provider));
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(1001, "日期格式应为 yyyy-MM-dd");
        }
    }
}
