package com.zhiyu.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小组列表项 VO（P1-B1）。
 */
@Data
public class GroupListItemVO {

    private Long id;
    private String name;
    private String description;
    private Integer memberCount;
    private Integer maxCount;
    private Integer status;
    private LocalDateTime createTime;
}
