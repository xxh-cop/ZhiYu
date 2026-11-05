package com.zhiyu.modules.craft.calibration;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 心流自评校准配置（阶段 1-1，2026-11-05），绑定 {@code zhiyu.flow.calibration}。
 *
 * <p>{@code sampleRate} 限 [0,1]，绑定期校验，越界启动即失败。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "zhiyu.flow.calibration")
public class FlowCalibrationProperties {

    /** 总开关：false 时 finish 不再抽样，查询/提交端点不受影响（历史记录仍可读） */
    private boolean enabled = true;

    /** 抽样比例（finish 时抽取概率），默认 0.2 */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double sampleRate = 0.2;
}
