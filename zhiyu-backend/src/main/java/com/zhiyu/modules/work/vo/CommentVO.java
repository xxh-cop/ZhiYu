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
    private String content;
    private String authorName;
    private LocalDateTime createTime;
}
