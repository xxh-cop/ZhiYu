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
    private Long projectId;
    private Long sessionId;
}
