package com.zhiyu.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagSourceVO {
    private String id;
    private String title;
    private String excerpt;
    private String url;
    private String organization;
    private String mediaReuse;
    private String sourceVersion;
    private String relation;
    private String citationType;
}
