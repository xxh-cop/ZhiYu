package com.zhiyu.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品列表项 VO（P1-B1）。
 */
@Data
public class WorkListItemVO {

    private Long id;
    private Long userId;
    private String authorName;
    private String title;
    private String coverUrl;
    private Integer visible;
    private Integer anonymous;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createTime;
}
