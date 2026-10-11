package com.zhiyu.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 向量库配置（M3-3）。
 *
 * <p>使用 Spring AI 的 {@link SimpleVectorStore}（内存 + JSON 持久化），
 * 依赖 {@link EmbeddingModel} Bean（由 {@code DoubaoEmbeddingConfig} 创建）。
 *
 * <p>当 EmbeddingModel 不存在时（未配置豆包 API Key），
 * 不创建 VectorStore Bean，RAG 功能降级。
 */
@Slf4j
@Configuration
@ConditionalOnBean(EmbeddingModel.class)
public class RagConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("创建 SimpleVectorStore Bean（内存 + JSON 持久化）");
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
