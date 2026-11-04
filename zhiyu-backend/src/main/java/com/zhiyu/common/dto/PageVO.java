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

    /**
     * 总记录数（S3 2026-11-04：分页元数据统一为 JSON number）。
     * 计数不可能超过 2^31；构造器仍收 long 并由 {@link Math#toIntExact(long)} 收窄，
     * 避免全局 Long→String 序列化策略把分页元数据变成字符串。
     */
    private int total;

    /** 当前页码（从 1 开始） */
    private int pageNum;

    /** 每页大小 */
    private int pageSize;

    /** 当前页记录列表 */
    private List<T> records;

    public PageVO(long total, int pageNum, int pageSize, List<T> records) {
        this.total = Math.toIntExact(total);
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records;
    }
}
