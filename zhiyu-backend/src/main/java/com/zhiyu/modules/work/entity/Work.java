package com.zhiyu.modules.work.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiyu.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品实体（T1.8a）。
 *
 * <p>对应 {@code work} 表（见 sql/schema.sql）。
 * user_id 必存（A6 裁决），anonymous 仅控制展示层。
 * message 不加密（T1.8a 裁决①）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work")
public class Work extends BaseEntity {

    /** 归属者 user.id（匿名仅展示层行为，后端必须存） */
    private Long userId;

    /** 关联项目 ID（可空） */
    private Long projectId;

    /** 关联创作会话 ID（可空） */
    private Long sessionId;

    /** 作品标题 */
    private String title;

    /** 封面图 URL（从 craft_project 继承） */
    private String coverUrl;

    /** 媒体类型：image/video/text */
    private String mediaType;

    /** 视频 URL（media_type=video 时有值） */
    private String videoUrl;

    /** AI 寄语（明文，不加密） */
    private String message;

    /** 0私密 1公开 2仅小组 */
    private Integer visible;

    /** 0显示昵称 1匿名展示；默认匿名 */
    private Integer anonymous;

    /** 点赞数（冗余计数） */
    private Integer likeCount;

    /** 0待审 1已发布 2下架 */
    private Integer status;

    /** 审核理由（I1，2026-11-04）：下架/驳回必填，通过可空；历史数据为 NULL（读取兼容）。 */
    private String auditReason;
}
