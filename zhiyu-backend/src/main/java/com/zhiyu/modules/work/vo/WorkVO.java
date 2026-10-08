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
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime createTime;
}
