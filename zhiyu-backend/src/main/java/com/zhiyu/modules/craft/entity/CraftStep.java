package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 手作步骤实体（T1.6）。
 *
 * <p>对应 {@code craft_step} 表（见 sql/schema.sql）。
 * 本批次仅用于 count totalSteps，不涉及步骤推进逻辑（T1.7）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_step")
public class CraftStep extends BaseEntity {

    /** 所属项目 ID */
    private Long projectId;

    /** 步骤序号 */
    private Integer stepNo;

    /** 步骤标题 */
    private String title;

    /** 引导语 */
    private String guide;

    /** 引导语音频 URL */
    private String audioUrl;

    /** 音频授权：none/cc0/owned/licensed/pending_review */
    private String licenseStatus;

    /** 音频署名信息（CC/授权来源） */
    private String attribution;

    /** 音频资产状态：ready 可播 / missing 缺失 / disabled 停用；NULL=无音频资产 */
    private String assetStatus;
}
