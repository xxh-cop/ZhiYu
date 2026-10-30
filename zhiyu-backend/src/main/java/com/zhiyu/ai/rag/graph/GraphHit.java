package com.zhiyu.ai.rag.graph;

import java.util.List;

/** 结构化关系检索命中的唯一条目；sourceIds 指向当前目录中的已登记来源。 */
public record GraphHit(String nodeId, String nodeType, String title, String excerpt,
                       String url, String organization, String mediaReuse,
                       List<String> sourceIds, String relationPath, String catalogVersion) {
}
