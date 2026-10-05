package com.zhiyu.modules.inheritor.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传承人项目列表项 VO（M2 批 5A）。
 */
@Data
public class InheritorProjectListItemVO {

    private Long id;
    private String title;
    private String coverUrl;
    private String category;
    private Integer status;
    private LocalDateTime createTime;
}
