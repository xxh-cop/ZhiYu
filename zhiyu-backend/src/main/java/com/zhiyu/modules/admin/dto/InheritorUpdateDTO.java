package com.zhiyu.modules.admin.dto;

import lombok.Data;

/**
 * 传承人编辑请求体（P1-B1）。
 */
@Data
public class InheritorUpdateDTO {

    private String skill;
    private String intro;
    private String honor;
}
