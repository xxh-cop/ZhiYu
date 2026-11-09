package com.zhiyu.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 回归保护：test profile 下 .env 不得注入，避免真实云 key 进入单测上下文。 */
@SpringBootTest
@ActiveProfiles("test")
class DotenvEnvironmentPostProcessorTest {

    @Autowired
    private ConfigurableEnvironment environment;

    @Test
    void testProfileDoesNotLoadDotenv() {
        for (PropertySource<?> source : environment.getPropertySources()) {
            assertFalse(source.getName().startsWith("zhiyuDotenv:"),
                    "test profile 不应注册 .env 属性源：" + source.getName());
        }
        assertNull(environment.getProperty("QIANWEN_ASR_SAMPLE_RATE"));
        assertEquals(System.getenv("DEEPSEEK_API_KEY"), environment.getProperty("DEEPSEEK_API_KEY"));
        assertEquals(System.getenv("DOUBAO_API_KEY"), environment.getProperty("DOUBAO_API_KEY"));
    }
}
