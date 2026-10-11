package com.zhiyu.ai.embedding;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 豆包 Embedding 配置（M3-3）。
 *
 * <p>从 {@code zhiyu.ai.doubao} 配置段读取 API Key / Base URL / 模型名 / 维度，
 * 创建 {@link DoubaoEmbeddingModel} Bean。
 *
 * <p>生效条件：{@code zhiyu.ai.doubao.api-key} 非空且非占位值。
 * 未配置时 RAG 功能降级（不创建 EmbeddingModel Bean）。
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "zhiyu.ai.doubao")
@Data
public class DoubaoEmbeddingConfig {

    private String apiKey;
    private Embedding embedding = new Embedding();

    @Data
    public static class Embedding {
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        private String model = "doubao-embedding-text-240515";
        private int dimensions = 2048;
    }

    @Bean
    @ConditionalOnExpression("'${zhiyu.ai.doubao.api-key:}' != '' and '${zhiyu.ai.doubao.api-key:}' != 'placeholder'")
    public EmbeddingModel doubaoEmbeddingModel() {
        log.info("创建 DoubaoEmbeddingModel Bean: model={}, dimensions={}",
                embedding.getModel(), embedding.getDimensions());
        return new DoubaoEmbeddingModel(
                apiKey,
                embedding.getBaseUrl(),
                embedding.getModel(),
                embedding.getDimensions()
        );
    }
}
