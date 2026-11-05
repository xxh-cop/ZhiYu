package com.zhiyu.ai.flow;

import java.util.List;

/**
 * 心流检测结果（T1.7；2026-11-05 扩展派生信号）。
 *
 * @param state           心流状态
 * @param flowDelta       本次心跳应累加的心流秒数（0 或正数，用于增量落库 flow_seconds；
 *                        只计入跨过 focusIdleSeconds 阈值之后的时长，按心跳切片累加）
 * @param signals         本次评估新产生的派生信号（无则为空列表），用于事件落库
 * @param prevStepNo      发生步骤切换时的原步骤号（未切换为 null）
 * @param prevDwellSeconds 发生步骤切换时原步骤的最终停留秒数（未切换为 null）
 */
public record FlowDetectionResult(FlowState state,
                                  int flowDelta,
                                  List<FlowSignal> signals,
                                  Integer prevStepNo,
                                  Integer prevDwellSeconds) {
}
