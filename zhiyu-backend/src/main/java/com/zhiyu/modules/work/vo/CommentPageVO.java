package com.zhiyu.modules.work.vo;

import lombok.Data;

import java.util.List;

/**
 * 评论分页 VO（T1.8a）。
 */
@Data
public class CommentPageVO {

    private Long total;
    private Integer page;
    private Integer size;
    private List<CommentVO> records;
}
