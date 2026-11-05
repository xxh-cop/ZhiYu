package com.zhiyu.modules.craft.takedown;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 分层下架配置（2026-11-05），绑定 {@code zhiyu.takedown}（见 {@code application.yml}）。
 *
 * <p>绑定期校验（{@code @Validated}）：{@code operational.default-deadline-days} 小于 7
 * 时启动即失败（配置校验拒绝小于 7 的输入，不静默兜底）。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "zhiyu.takedown")
public class TakedownProperties {

    /** 私有导出总开关：false 时合规下架窗口内也不再下发 canExport=true */
    private boolean exportEnabled = true;

    @Valid
    private Compliance compliance = new Compliance();

    @Valid
    private Operational operational = new Operational();

    @Data
    public static class Compliance {
        /** 合规下架的导出/只读窗口（小时），默认 72 */
        @Min(1)
        private int exportWindowHours = 72;
    }

    @Data
    public static class Operational {
        /**
         * 运营下架默认归档期限（天），默认 14；产品红线最小 7 天。
         * 用途：管理端预填默认值；存量缺 archive_at 的运营下架行由归档 Worker 按
         * takedown_at + 本值推导归档截止。
         */
        @Min(7)
        private int defaultDeadlineDays = 14;
    }
}
