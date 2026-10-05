package com.zhiyu.modules.auth.vo;

import lombok.Data;

/**
 * 隐私政策状态返回体，对应 {@code GET /api/user/privacy/status}。
 */
@Data
public class PrivacyStatusVO {

    /** 隐私政策是否同意：0 未同意 1 已同意 */
    private Integer privacyAgreed;

    /** 是否允许心理咨询师联系：0 不允许 1 允许 */
    private Integer canContact;
}
