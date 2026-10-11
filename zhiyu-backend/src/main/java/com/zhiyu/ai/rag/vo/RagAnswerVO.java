package com.zhiyu.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答返回体（M3-3）。
 *
 * @param answer  LLM 生成的回答文本
 * @param sources 引用的知识库来源列表
 */
@Data
@AllArgsConstructor
public class RagAnswerVO {
    private String answer;
    private List<RagSourceVO> sources;
}
