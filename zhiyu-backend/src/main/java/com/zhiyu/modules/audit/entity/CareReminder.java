package com.zhiyu.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 关怀提醒实体（T1.3b）。
 *
 * <p>当用户在情绪对话中表达高危信号时，系统自动生成一条 care_reminder，
 * 供授权角色（咨询师）在管理端查看并跟进。
 *
 * <p>注意（AGENTS.md §5 例外）：
 * <ul>
 *   <li>{@code summary} 存<b>脱敏文本、不加密</b>（已去除可识别身份的信息）</li>
 *   <li>不写完整对话原文，只写「在会话 #{sessionId} 中表达了高危信号：{命中关键词}」</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("care_reminder")
public class CareReminder extends BaseEntity {

    /** 触发用户 ID。 */
    private Long userId;

    /** 关联会话 ID（可空）。 */
    private Long sessionId;

    /** 风险等级：1 一般 / 2 高危。 */
    private Integer riskLevel;

    /** 脱敏摘要（不加密，供咨询师查看）。 */
    private String summary;

    /** 处理状态：0 待处理 / 1 已跟进。 */
    private Integer status;
}
