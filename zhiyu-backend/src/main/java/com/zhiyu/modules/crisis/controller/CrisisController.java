package com.zhiyu.modules.crisis.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.crisis.config.CrisisProperties;
import com.zhiyu.modules.crisis.vo.HotlinesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 危机干预公开接口（M3-B1），对应 {@code /api/crisis/**}。
 *
 * <p>{@code GET /api/crisis/hotlines} 为公开接口（{@code permitAll}），
 * 未登录也能访问，用于登录页与危机干预页展示心理援助热线。
 */
@RestController
@RequestMapping("/api/crisis")
@RequiredArgsConstructor
public class CrisisController {

    private final CrisisProperties crisisProperties;

    /**
     * 获取心理援助热线信息。
     *
     * <p>返回学校心理中心与全国心理援助热线的号码和名称，
     * 配置来自 {@code application.yml} 的 {@code zhiyu.crisis}。
     */
    @GetMapping("/hotlines")
    public Result<HotlinesVO> getHotlines() {
        HotlinesVO vo = new HotlinesVO();
        vo.setPsychCenterPhone(crisisProperties.getPsychCenterPhone());
        vo.setNationalHotline(crisisProperties.getNationalHotline());
        vo.setPsychCenterName(crisisProperties.getPsychCenterName());
        vo.setNationalHotlineName(crisisProperties.getNationalHotlineName());
        return Result.success(vo);
    }
}
