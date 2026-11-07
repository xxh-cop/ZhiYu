package com.zhiyu.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * {@link LlmClientRegistry} 单元测试（2026-11-08 装配层批）。
 *
 * <p>回归钉：{@link OllamaLlmClient} 继承 {@link SpringAiLlmClient}，按父类型注入时
 * 本地兜底会被错认成云端（cloud-only 能力被本地顶替的真实事故），云槽必须精确排除子类。
 */
class LlmClientRegistryTest {

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }
        };
    }

    @Test
    @DisplayName("OllamaLlmClient 不得落入云槽（D4 回归：父类型注入防误认）")
    void ollamaSubclassIsNotMistakenForCloud() {
        OllamaLlmClient ollama = mock(OllamaLlmClient.class);
        var registry = new LlmClientRegistry(providerOf(ollama), providerOf(ollama), providerOf(null));

        assertNull(registry.cloudOrNull(), "本地兜底实现不得被认作云端");
        assertSame(ollama, registry.localOrNull());
        assertSame(ollama, registry.primary(), "云缺席时展示首选回落到本地");
    }

    @Test
    @DisplayName("SpringAiLlmClient 本体正确落云槽")
    void cloudBeanRecognized() {
        SpringAiLlmClient cloud = mock(SpringAiLlmClient.class);
        var registry = new LlmClientRegistry(providerOf(cloud), providerOf(null), providerOf(null));

        assertSame(cloud, registry.cloudOrNull());
        assertNull(registry.localOrNull());
        assertSame(cloud, registry.primary());
    }

    @Test
    @DisplayName("云缺席且无本地时展示首选回落 Mock")
    void primaryFallsBackToMock() {
        MockLlmClient mockClient = mock(MockLlmClient.class);
        var registry = new LlmClientRegistry(providerOf(null), providerOf(null), providerOf(mockClient));

        assertNull(registry.cloudOrNull());
        assertSame(mockClient, registry.primary());
    }
}
