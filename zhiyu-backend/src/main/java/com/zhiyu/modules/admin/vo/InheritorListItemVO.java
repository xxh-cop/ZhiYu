package com.zhiyu.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传承人列表项 VO（P1-B1）。
 */
@Data
public class InheritorListItemVO {

    private Long id;
    private Long userId;
    private String nickname;
    private String skill;
    private String honor;
    private Integer verified;
    private LocalDateTime createTime;
}
