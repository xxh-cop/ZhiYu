package com.zhiyu.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * RAG 引用来源（M3-3）。
 *
 * @param title   来源文件名 / 知识库名
 * @param excerpt 内容摘要（前 80 字）
 */
@Data
@AllArgsConstructor
public class RagSourceVO {
    private String title;
    private String excerpt;
}
