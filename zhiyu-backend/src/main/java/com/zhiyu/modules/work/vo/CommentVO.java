package com.zhiyu.modules.work.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论 VO（T1.8a）。
 *
 * <p>评论者实名（work_comment 无 anonymous），authorName 为用户昵称。
 */
@Data
public class CommentVO {

    private Long id;
    private Integer status;
    private boolean mine;
    /**
     * 软档降权截止（2026-11-07 处置链）：口径与作品侧完全一致——仅本人评论（mine=true）
     * + 处置链开关开启 + 降权未过期三条件同时成立时下发；他人评论与已过期为 null（NON_NULL 不出现）。
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private LocalDateTime softDemoteUntil;
    private String content;
    private String authorName;
    private LocalDateTime createTime;
}
