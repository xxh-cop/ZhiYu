package com.zhiyu.modules.admin.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品详情 VO（P1-B1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkDetailVO extends WorkListItemVO {

    private String message;
    /** 历史驳回/下架理由（I1 起落库；历史数据为 NULL，前端按空态展示），O2 2026-11-04 供详情抽屉展示。 */
    private String auditReason;
    private Long projectId;
    private Long sessionId;
}
