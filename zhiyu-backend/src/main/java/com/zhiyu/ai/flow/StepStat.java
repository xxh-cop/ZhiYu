package com.zhiyu.ai.flow;

import lombok.Data;

/**
 * 心流统计状态（T1.7；2026-11-05 扩展焦躁双信号与呼吸提醒闩锁）。
 *
 * <p>每次心跳上报后，由 {@link FlowDetectionService} 读-改-写。
 * 存储由 {@link FlowStatStore} 提供（内存 / Redis / Fallback 装饰器）。
 * Redis 以 Jackson JSON 序列化；新增字段对旧 JSON 反序列化取默认值，兼容。
 *
 * @param stepNo               当前步骤序号
 * @param dwellSeconds         当前步骤累计停留秒数
 * @param repeatCount          步骤回退次数（stepNo 减小才 +1；前进不清零）
 * @param lastFlowSeconds      上次累加心流时使用的 dwellSeconds 基准（切片去重，防止重复累加）
 * @param dwellStagnationCount 同一步骤「重新停留」次数（dwell 不增反降计一次；步骤切换时清零，
 *                             与 repeatCount 不清零的口径有意区分，产品裁决前两套口径并存）
 * @param stagnationSignalled  本步骤 dwell_stagnation 信号是否已发出（每步骤只发一次）
 * @param breathingPrompted    呼吸引导是否处于「已提醒未响应」（响应前不重复发 prompt 信号）
 */
@Data
public class StepStat {

    private int stepNo;
    private int dwellSeconds;
    private int repeatCount;
    private int lastFlowSeconds;
    private int dwellStagnationCount;
    private boolean stagnationSignalled;
    private boolean breathingPrompted;

    /**
     * 创建一个默认的 StepStat（首次心跳时用）。
     */
    public static StepStat initial() {
        return new StepStat();
    }
}
