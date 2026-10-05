package com.zhiyu.modules.crisis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 危机干预配置属性，绑定 {@code zhiyu.crisis}（见 {@code application.yml}）。
 *
 * <p>包含学校心理中心与全国心理援助热线的电话号码与名称，
 * 用于 {@code GET /api/crisis/hotlines} 公开接口与高危命中后的关怀话术。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhiyu.crisis")
public class CrisisProperties {

    /** 学校心理中心电话 */
    private String psychCenterPhone;

    /** 学校心理中心名称 */
    private String psychCenterName;

    /** 全国心理援助热线号码 */
    private String nationalHotline;

    /** 全国心理援助热线名称 */
    private String nationalHotlineName;
}
