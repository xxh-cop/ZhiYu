package com.zhiyu.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RagAnswerVO {
    private String answer;
    private List<RagSourceVO> sources;
    private String retrievalMode;
    private boolean generated;

    public RagAnswerVO(String answer, List<RagSourceVO> sources) {
        this(answer, sources, "NONE", false);
    }
}
