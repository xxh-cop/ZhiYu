package com.zhiyu.ai.rag;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.rag.graph.GraphHit;
import com.zhiyu.ai.rag.graph.HeritageGraphIndex;
import com.zhiyu.ai.rag.vo.RagAnswerVO;
import com.zhiyu.ai.rag.vo.RagSourceVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 非遗问答统一编排：结构化关系检索始终可用，向量召回可选。
 * 不把结构化检索称为向量模型；高风险输入不进入摘录回答。
 */
@Service
public class KnowledgeAskService {

    public static final String MODE_GRAPH_VECTOR = "GRAPH_VECTOR";
    public static final String MODE_STRUCTURED_GRAPH = "STRUCTURED_GRAPH";
    public static final String MODE_VECTOR = "VECTOR";
    public static final String MODE_NONE = "NONE";
    private static final String NO_RESULT =
            "暂时没有在已审核的非遗资料中找到可引用的内容，可以换成工艺、材料或纹样名称再试。";

    private final HeritageGraphIndex graphIndex;
    private final RagService ragService;
    private final AiClient aiClient;
    private final String promptTemplate;

    public KnowledgeAskService(HeritageGraphIndex graphIndex,
                               @Nullable RagService ragService,
                               AiClient aiClient,
                               @Qualifier("ragPrompt") String promptTemplate) {
        this.graphIndex = graphIndex;
        this.ragService = ragService;
        this.aiClient = aiClient;
        this.promptTemplate = promptTemplate;
    }

    public RagAnswerVO ask(String question) {
        AiClient.KnowledgeResult review = aiClient.reviewKnowledgeQuestion(question);
        if (review.blocked()) {
            return new RagAnswerVO(review.text(), List.of(), MODE_NONE, false);
        }
        List<GraphHit> graphHits = graphIndex.search(question);
        List<RagService.VectorHit> vectorHits;
        try {
            vectorHits = ragService == null ? List.of() : ragService.retrieve(question);
        } catch (Exception e) {
            vectorHits = List.of();
        }
        List<RagSourceVO> sources = sources(graphHits, vectorHits);
        if (sources.isEmpty()) {
            return new RagAnswerVO(NO_RESULT, List.of(), MODE_NONE, false);
        }

        String mode = !graphHits.isEmpty() && !vectorHits.isEmpty() ? MODE_GRAPH_VECTOR
                : !graphHits.isEmpty() ? MODE_STRUCTURED_GRAPH : MODE_VECTOR;
        if (MODE_STRUCTURED_GRAPH.equals(mode)) {
            return new RagAnswerVO(extractive(graphHits, vectorHits), sources, mode, false);
        }

        String prompt = promptTemplate
                .replace("{context}", context(graphHits, vectorHits))
                .replace("{question}", question);
        AiClient.KnowledgeResult result = aiClient.knowledgeAnswerResultReviewed(question, () -> prompt);
        if (!result.generated()) {
            return new RagAnswerVO(extractive(graphHits, vectorHits), sources, mode, false);
        }
        return new RagAnswerVO(result.text(), sources, mode, true);
    }

    private List<RagSourceVO> sources(List<GraphHit> graphHits, List<RagService.VectorHit> vectorHits) {
        LinkedHashMap<String, RagSourceVO> unique = new LinkedHashMap<>();
        graphHits.stream().filter(hit -> "SOURCE".equals(hit.nodeType())).forEach(hit ->
                unique.putIfAbsent(hit.nodeId(), new RagSourceVO(hit.nodeId(), hit.title(), hit.excerpt(),
                        hit.url(), hit.organization(), hit.mediaReuse(), hit.catalogVersion(),
                        hit.relationPath(), "VERIFIED_SOURCE")));
        for (RagService.VectorHit hit : vectorHits) {
            // 同一文档的多个切片只保留一条引用，避免前端出现重复来源。
            String key = hit.title() == null || hit.title().isBlank()
                    ? hit.id() : "vector:" + hit.title();
            unique.putIfAbsent(key, new RagSourceVO(hit.id(), hit.title(), hit.excerpt(),
                    null, null, null, hit.corpusVersion(), "检索文档", "REFERENCE"));
        }
        return unique.values().stream().limit(6).toList();
    }

    private String context(List<GraphHit> graphHits, List<RagService.VectorHit> vectorHits) {
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (GraphHit hit : graphHits) {
            String label = switch (hit.nodeType()) {
                case "SOURCE" -> "已核实来源";
                case "TRADITION" -> "已登记工艺";
                case "PATTERN" -> "原创教学纹样";
                case "MEDIUM" -> "纹样介质";
                case "MATERIAL" -> "材料";
                case "SAFETY" -> "安全提醒";
                default -> "已审核资料";
            };
            context.append("【资料").append(index++).append("｜").append(label).append("】")
                    .append(hit.title()).append('\n')
                    .append(hit.excerpt()).append('\n')
                    .append("关系：").append(hit.relationPath()).append("\n\n");
        }
        for (RagService.VectorHit hit : vectorHits) {
            context.append("【资料").append(index++).append("｜检索参考】")
                    .append(hit.title()).append('\n')
                    .append(hit.excerpt()).append("\n\n");
        }
        return context.toString();
    }

    private String extractive(List<GraphHit> graphHits, List<RagService.VectorHit> vectorHits) {
        StringBuilder answer = new StringBuilder("以下为结构化资料检索结果，未调用生成模型：\n");
        graphHits.stream().filter(hit -> "TRADITION".equals(hit.nodeType())).findFirst()
                .ifPresent(hit -> answer.append("工艺：").append(hit.excerpt()).append('\n'));
        graphHits.stream().filter(hit -> "PATTERN".equals(hit.nodeType())).findFirst()
                .ifPresent(hit -> answer.append("纹样：").append(hit.title())
                        .append("——").append(hit.excerpt()).append('\n'));
        graphHits.stream().filter(hit -> "MATERIAL".equals(hit.nodeType())).findFirst()
                .ifPresent(hit -> answer.append("材料：").append(hit.title())
                        .append("——").append(hit.excerpt()).append('\n'));
        graphHits.stream().filter(hit -> "SAFETY".equals(hit.nodeType())).findFirst()
                .ifPresent(hit -> answer.append("安全提醒：").append(hit.excerpt()).append('\n'));
        List<GraphHit> verified = graphHits.stream()
                .filter(hit -> "SOURCE".equals(hit.nodeType())).limit(2).toList();
        if (!verified.isEmpty()) {
            answer.append("可核对来源：")
                    .append(verified.stream().map(GraphHit::title).collect(Collectors.joining("；")))
                    .append("。\n");
        } else if (!vectorHits.isEmpty()) {
            answer.append("参考资料：").append(vectorHits.get(0).excerpt()).append('\n');
        }
        return answer.append("来源仅作材料与技艺背景参考；本系统不能替代专业心理咨询。").toString();
    }
}
