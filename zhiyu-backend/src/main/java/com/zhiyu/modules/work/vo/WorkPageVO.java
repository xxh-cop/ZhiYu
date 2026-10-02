package com.zhiyu.modules.work.vo;

import lombok.Data;

import java.util.List;

/**
 * 作品分页 VO（T1.8a）。
 */
@Data
public class WorkPageVO {

    private Long total;
    private Integer page;
    private Integer size;
    private List<WorkVO> records;
}
