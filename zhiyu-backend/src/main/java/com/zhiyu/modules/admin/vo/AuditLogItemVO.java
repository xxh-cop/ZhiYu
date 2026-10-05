package com.zhiyu.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 审计日志列表项 VO（P1-A1）。
 *
 * <p>列表不返回 rawInput / aiOutput（加密字段，需详情接口解密后返回）。
 */
@Data
public class AuditLogItemVO {

    private Long id;

    /** 场景：chat / recommend / work_message */
    private String scene;

    /** 过滤动作：PASS / REVIEW / REWRITE / BLOCK */
    private String action;

    /** 命中原因 */
    private String reason;

    /** 创建时间 */
    private LocalDateTime createTime;
}
