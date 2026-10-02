package com.zhiyu.ai.flow;

/**
 * 心流检测结果（T1.7）。
 *
 * @param state     心流状态
 * @param flowDelta 本次心跳应累加的心流秒数（0 或正数，用于增量落库 flow_seconds）
 */
public record FlowDetectionResult(FlowState state, int flowDelta) {
}
