package com.zhiyu.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 豆包（火山引擎方舟）Embedding 模型实现（M3-3）。
 *
 * <p>通过 RestTemplate 调用豆包 Embedding API（兼容 OpenAI /v1/embeddings 规范），
 * 实现 Spring AI 的 {@link EmbeddingModel} 接口，供 {@code SimpleVectorStore} 使用。
 *
 * <p>API 文档：https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal
 * 模型：doubao-embedding-vision-251215（多模态向量化，支持纯文本输入），维度 2048
 *
 * <p>降级策略：调用失败时返回空 EmbeddingResponse（不抛异常），
 * 上层 {@code SimpleVectorStore.add()} 会收到空向量但不会崩溃。
 */
@Slf4j
public class DoubaoEmbeddingModel implements EmbeddingModel {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimensions;

    private static final int TIMEOUT_MS = 30_000;

    public DoubaoEmbeddingModel(String apiKey, String baseUrl, String model, int dimensions) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
        log.info("DoubaoEmbeddingModel 初始化完成: model={}, dimensions={}, baseUrl={}",
                model, dimensions, baseUrl);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();

        // 多模态 Embedding API：input 为对象数组，每个元素 { type: "text", text: "..." }
        List<Map<String, String>> inputList = texts.stream()
                .map(t -> Map.of("type", "text", "text", t))
                .collect(Collectors.toList());
        Map<String, Object> body = Map.of(
                "model", model,
                "input", inputList,
                "encoding_format", "float"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    baseUrl + "/embeddings/multimodal", entity, Map.class);
            return parseResponse(resp.getBody(), texts.size());
        } catch (Exception e) {
            log.error("豆包 Embedding 调用失败: {}", e.getMessage(), e);
            return emptyResponse(texts.size());
        }
    }

    /**
     * 实现 {@link EmbeddingModel#embed(Document)} 抽象方法。
     * 将单条文档转为文本调用 {@link #call(EmbeddingRequest)} 并返回第一个向量。
     */
    @Override
    public float[] embed(Document document) {
        EmbeddingRequest req = new EmbeddingRequest(
                List.of(document.getText()), null);
        EmbeddingResponse resp = call(req);
        if (resp.getResults() != null && !resp.getResults().isEmpty()) {
            return resp.getResults().get(0).getOutput();
        }
        return new float[dimensions];
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    /**
     * 解析豆包 API 响应。
     *
     * <p>响应格式（兼容 OpenAI）：
     * <pre>
     * {
     *   "data": [
     *     { "embedding": [0.1, 0.2, ...], "index": 0 }
     *   ],
     *   "usage": { "total_tokens": 123 }
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private EmbeddingResponse parseResponse(Map<String, Object> respBody, int expectedCount) {
        if (respBody == null) {
            log.warn("豆包 Embedding 响应体为空，返回空向量");
            return emptyResponse(expectedCount);
        }

        Object dataObj = respBody.get("data");
        if (dataObj == null) {
            log.warn("豆包 Embedding 响应 data 为空");
            return emptyResponse(expectedCount);
        }

        List<org.springframework.ai.embedding.Embedding> embeddings = new ArrayList<>();

        if (dataObj instanceof List) {
            // 纯文本 API 格式: data 是数组
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
            for (Map<String, Object> item : dataList) {
                float[] vector = extractVector(item);
                int index = item.containsKey("index") ? ((Number) item.get("index")).intValue() : embeddings.size();
                embeddings.add(new org.springframework.ai.embedding.Embedding(vector, index));
            }
        } else if (dataObj instanceof Map) {
            // 多模态 API 格式: data 是单个对象
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            float[] vector = extractVector(dataMap);
            embeddings.add(new org.springframework.ai.embedding.Embedding(vector, 0));
        } else {
            log.warn("豆包 Embedding 响应 data 未知类型: {}", dataObj.getClass().getName());
            return emptyResponse(expectedCount);
        }

        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
        metadata.setModel(model);
        Map<String, Object> usage = (Map<String, Object>) respBody.get("usage");
        if (usage != null && usage.containsKey("total_tokens")) {
            Integer totalTokens = ((Number) usage.get("total_tokens")).intValue();
            metadata.setUsage(new DefaultUsage(0, 0, totalTokens));
        }

        log.info("豆包 Embedding 调用成功: {} 个向量, dims={}",
                embeddings.size(), embeddings.isEmpty() ? 0 : embeddings.get(0).getOutput().length);
        return new EmbeddingResponse(embeddings, metadata);
    }

    /**
     * 从响应条目中提取 embedding 向量。
     */
    @SuppressWarnings("unchecked")
    private float[] extractVector(Map<String, Object> item) {
        List<Number> embeddingList = (List<Number>) item.get("embedding");
        float[] vector = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            vector[i] = embeddingList.get(i).floatValue();
        }
        return vector;
    }

    /**
     * 降级：返回零向量列表，不抛异常。
     */
    private EmbeddingResponse emptyResponse(int count) {
        List<org.springframework.ai.embedding.Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            embeddings.add(new org.springframework.ai.embedding.Embedding(new float[dimensions], i));
        }
        return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
    }
}
