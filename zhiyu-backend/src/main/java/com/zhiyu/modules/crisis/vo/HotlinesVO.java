package com.zhiyu.modules.crisis.vo;

import lombok.Data;

/**
 * 心理援助热线返回体，对应 {@code GET /api/crisis/hotlines}。
 *
 * <p>该接口为公开接口（permitAll），未登录也能访问，用于登录页展示。
 */
@Data
public class HotlinesVO {

    /** 学校心理中心电话 */
    private String psychCenterPhone;

    /** 全国心理援助热线号码 */
    private String nationalHotline;

    /** 学校心理中心名称 */
    private String psychCenterName;

    /** 全国心理援助热线名称 */
    private String nationalHotlineName;
}
