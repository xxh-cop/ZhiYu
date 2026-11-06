package com.zhiyu.modules.craft.vo;

import lombok.Data;

/**
 * 心跳响应 VO（T1.7）。
 *
 * <p>对应 OpenAPI schema {@code HeartbeatResult.data}。
 */
@Data
public class HeartbeatResult {

    /** 是否处于心流状态 */
    private boolean inFlow;

    /** 是否需要呼吸引导 */
    private boolean needBreathing;

    /**
     * 卡点引导文案（2026-11-05 卡点引导，可空）。
     *
     * <p>仅在 DWELL_STAGNATION 触发后的后续心跳携带一次：轻提示、可一键收起、
     * 可在设置中永久关闭；null 表示本次无引导。AI 生成失败/超时/熔断时
     * 为静态模板文案，永不为空串。
     */
    private String stuckGuide;

    public HeartbeatResult(boolean inFlow, boolean needBreathing) {
        this(inFlow, needBreathing, null);
    }

    public HeartbeatResult(boolean inFlow, boolean needBreathing, String stuckGuide) {
        this.inFlow = inFlow;
        this.needBreathing = needBreathing;
        this.stuckGuide = stuckGuide;
    }
}
