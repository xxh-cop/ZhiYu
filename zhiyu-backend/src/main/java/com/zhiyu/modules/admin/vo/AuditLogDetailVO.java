package com.zhiyu.modules.admin.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 审计日志详情 VO（P1-A1）。
 *
 * <p>在 {@link AuditLogItemVO} 基础上增加解密后的 rawInput / aiOutput。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogDetailVO extends AuditLogItemVO {

    /** 原始输入（解密后明文） */
    private String rawInput;

    /** AI 输出（解密后明文） */
    private String aiOutput;
}
