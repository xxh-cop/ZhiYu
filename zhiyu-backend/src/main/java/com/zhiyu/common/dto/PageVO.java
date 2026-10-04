package com.zhiyu.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 通用分页响应包装（M2 批 4）。
 *
 * <p>管理端分页接口统一返回 {@code Result<PageVO<T>>}，
 * 前端据此渲染分页控件。
 *
 * @param <T> 记录类型
 */
@Data
public class PageVO<T> {

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int pageNum;

    /** 每页大小 */
    private int pageSize;

    /** 当前页记录列表 */
    private List<T> records;

    public PageVO(long total, int pageNum, int pageSize, List<T> records) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records;
    }
}
