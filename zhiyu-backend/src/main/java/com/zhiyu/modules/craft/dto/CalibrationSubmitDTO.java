package com.zhiyu.modules.craft.dto;

import lombok.Data;

/**
 * 心流自评校准提交（阶段 1-1）。
 *
 * <p>二选一：{@code score}（1~7 整数）作答，或 {@code skip=true} 跳过。
 * 跳过也落记录（区分「没看到」与「看到了不答」）；两者都空 → 1001；
 * 范围校验在服务层（要读量表常量）。
 */
@Data
public class CalibrationSubmitDTO {

    /** 自评分数 1~7（1 完全没投入 … 7 完全沉浸其中）；与 skip 二选一 */
    private Integer score;

    /** 明确跳过；与 score 二选一 */
    private Boolean skip;
}
