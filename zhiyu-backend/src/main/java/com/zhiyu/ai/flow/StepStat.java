package com.zhiyu.ai.flow;

import lombok.Data;

/**
 * 心流统计状态（T1.7）。
 *
 * <p>每次心跳上报后，由 {@link FlowDetectionService} 读-改-写。
 * 存储由 {@link FlowStatStore} 提供（内存 / Redis / Fallback 装饰器）。
 *
 * @param stepNo            当前步骤序号
 * @param dwellSeconds      当前步骤累计停留秒数
 * @param repeatCount       步骤回退次数（stepNo 减小才 +1；前进不清零）
 * @param lastFlowSeconds   上次累加心流时用的 dwellSeconds（去重，防止重复累加）
 */
@Data
public class StepStat {

    private int stepNo;
    private int dwellSeconds;
    private int repeatCount;
    private int lastFlowSeconds;

    /**
     * 创建一个默认的 StepStat（首次心跳时用）。
     */
    public static StepStat initial() {
        return new StepStat();
    }
}
