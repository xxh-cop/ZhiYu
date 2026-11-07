package com.zhiyu.modules.work.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品 VO（T1.8a）。
 *
 * <p>不含 userId（A6 裁决），authorName 在匿名时为「匿名同学」。
 */
@Data
public class WorkVO {

    private Long id;
    private String title;
    private String coverUrl;
    private String mediaType;
    private String videoUrl;
    private String message;
    private String authorName;
    private Boolean anonymous;
    private Integer visible;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Boolean liked;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Boolean favorited;
    private Boolean shareable;
    /** 审核状态：0 待审（先审后发，仅作者可见） / 1 已公开 / 2 已下架（2026-11-04 N1）。 */
    private Integer status;
    /**
     * 审核理由（AE2，2026-11-04）：仅 GET /api/works/{id} 对<b>作者本人</b>下发
     * （历史驳回/下架理由）；他人不可见（非公开作品对他人本来就 1004）；未审核/已通过为 null。
     */
    private String auditReason;
    /**
     * 软档降权截止（2026-11-07 处置链）：仅 GET /api/works/{id} 对<b>作者本人</b>下发
     * （与 auditReason 同口径；「展示受限」徽标数据源——反静默折叠要求作者始终可见自身状态）；
     * 他人视角与社区流列表不下发（NON_NULL）。
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.time.LocalDateTime softDemoteUntil;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createTime;
}
