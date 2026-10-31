package com.zhiyu.modules.companion.tool;

import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.graph.HeritageGraphIndex;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 只读非遗检索：返回有界关系检索结果与引用元数据，不调用生成模型。 */
@Component
public class SearchHeritageTool implements CompanionTool {

    private final HeritageGraphIndex graphIndex;

    public SearchHeritageTool(HeritageGraphIndex graphIndex) {
        this.graphIndex = graphIndex;
    }

    @Override
    public String name() {
        return "searchHeritage";
    }

    @Override
    public String description() {
        return "检索已审核的非遗工艺、原创纹样与来源；返回引用元数据，不生成回答。";
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> input) {
        if (context == null || context.userId() == null) {
            return ToolResult.failure("1002", "请先登录");
        }
        String question = string(input.get("question"), 500);
        if (question.isBlank()) {
            return ToolResult.failure("1001", "请输入要检索的问题");
        }
        List<GraphHit> hits = graphIndex.search(question);
        return ToolResult.success(Map.of(
                "retrievalMode", "STRUCTURED_GRAPH",
                "hits", hits,
                "note", "只展示已审核目录中的关系检索结果，不调用生成模型。"));
    }

    private String string(Object value, int max) {
        if (value == null) return "";
        String text = value.toString().trim();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
