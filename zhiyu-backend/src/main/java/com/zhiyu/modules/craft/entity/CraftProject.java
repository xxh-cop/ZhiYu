package com.zhiyu.modules.craft.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 手作项目实体（T1.5）。
 *
 * <p>对应 {@code craft_project} 表（见 sql/schema.sql）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("craft_project")
public class CraftProject extends BaseEntity {

    /** 上传传承人 user.id */
    private Long inheritorId;

    /** 项目标题 */
    private String title;

    /** 项目简介 */
    private String intro;

    /** 文化背景 */
    private String cultureBg;

    /** 1 入门 / 2 进阶 / 3 高阶 */
    private Integer level;

    /** 预计时长（分钟） */
    private Integer estMinutes;

    /** 技艺品类，取值见 dict_category.label */
    private String category;

    /** 疗愈效果标签，逗号分隔，词表见 dict_therapy_tags */
    private String therapyTags;

    /** 封面图 URL */
    private String coverUrl;

    /** 平台上传的教学视频引用；与项目一起审核。 */
    private String videoUrl;

    /** 0 待审 / 1 上架 / 2 下架 */
    private Integer status;

    /** 审核/下架/删除理由（AY1 2026-11-05；驳回理由同列落库） */
    private String auditReason;
}
