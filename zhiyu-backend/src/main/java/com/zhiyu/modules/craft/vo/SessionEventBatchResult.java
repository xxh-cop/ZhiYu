package com.zhiyu.modules.craft.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 会话行为事件批量上报结果（2026-11-05 心流度量配套）。
 *
 * <p>对应 OpenAPI schema {@code SessionEventBatchResult}。
 */
@Data
@AllArgsConstructor
public class SessionEventBatchResult {

    /** 新入库条数 */
    private int accepted;

    /** 因 (session_id, client_seq) 已存在而去重的条数（幂等，不算错误） */
    private int deduped;

    /** 因数据不可接受（如发生时刻超前过多）而丢弃的条数 */
    private int failed;
}
