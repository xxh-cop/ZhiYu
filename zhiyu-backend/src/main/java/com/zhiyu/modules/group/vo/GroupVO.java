package com.zhiyu.modules.group.vo;

import lombok.Data;

/**
 * 小组 VO（T1.8b）。
 */
@Data
public class GroupVO {

    private Long id;
    private String name;
    private String description;
    private Integer maxCount;
    private Integer memberCount;
    private Integer status;
    /** 当前用户是否已加入 */
    private Boolean joined;
    /** 当前用户角色：成员 / 组长 / null（未加入） */
    private String myRole;
}
