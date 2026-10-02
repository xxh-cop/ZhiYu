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

    public HeartbeatResult(boolean inFlow, boolean needBreathing) {
        this.inFlow = inFlow;
        this.needBreathing = needBreathing;
    }
}
