package com.zhiyu.modules.craft.vo;

import lombok.Data;

/**
 * 手作步骤 VO（T1.9b-backend）。
 *
 * <p>对应 OpenAPI schema {@code CraftStepVO}。
 * 与 {@link com.zhiyu.modules.craft.entity.CraftStep} 实体分离，
 * 只暴露前端需要的字段。
 */
@Data
public class CraftStepVO {

    /** 步骤 ID */
    private Long id;

    /** 步骤序号 */
    private Integer stepNo;

    /** 步骤标题 */
    private String title;

    /** 引导语 */
    private String guide;

    /** 引导语音频 URL */
    private String audioUrl;
}
