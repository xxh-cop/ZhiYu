package com.zhiyu.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传承人详情 VO（P1-B1）。
 */
@Data
public class InheritorDetailVO {

    private Long id;
    private Long userId;
    private String nickname;
    private String skill;
    private String intro;
    private String honor;
    private Integer verified;
    private LocalDateTime createTime;
    /** 关联 user 的学校 */
    private String school;
}
