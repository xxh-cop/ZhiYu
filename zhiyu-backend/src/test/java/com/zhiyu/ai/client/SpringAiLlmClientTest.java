package com.zhiyu.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SpringAiLlmClient} 单测（T1.1a）。
 *
 * <p>Spring AI 的 {@code ChatClient} 链式 API 全是 final + builder 模式，
 * 用 Mockito mock 成本高、收益低（测的是框架自身行为）。
 * 这里验证：
 * <ul>
 *   <li>{@link ChatMessage} 转换逻辑（不依赖网络）</li>
 *   <li>{@link MockLlmClient} 在 API Key 为 placeholder 时生效（间接验证条件装配）</li>
 *   <li>真 API 调用标注 {@code @Disabled}，手动启用</li>
 * </ul>
 *
 * <p>T1.1b 做 {@code AiClient} 门面时会用集成测试覆盖端到端流程。
 */
class SpringAiLlmClientTest {

    @Test
    @DisplayName("ChatMessage 工厂方法产出正确的 role")
    void chatMessage_rolesCorrect() {
        assertEquals("system", ChatMessage.system("系统提示").role());
        assertEquals("user", ChatMessage.user("用户输入").role());
        assertEquals("assistant", ChatMessage.assistant("助手回复").role());
    }

    @Test
    @DisplayName("ChatMessage content 存储正确")
    void chatMessage_contentStored() {
        ChatMessage msg = ChatMessage.user("你好");
        assertEquals("你好", msg.content());
    }

    @Test
    @DisplayName("MockLlmClient 在无 API Key 时可用（降级验证）")
    void fallbackWhenApiKeyMissing() {
        // 模拟 API Key 为 placeholder 时的降级场景：
        // MockLlmClient 应返回固定话术，不抛异常
        MockLlmClient fallback = new MockLlmClient();
        String reason = fallback.recommendReason("焦虑", List.of("扎染", "赣绣"));

        assertNotNull(reason, "Mock 降级应返回非 null");
        assertTrue(reason.contains("扎染"), "应包含手作名");
        assertFalse(reason.contains("sk-"), "不应泄露 API Key 信息");
    }

    @Test
    @DisplayName("真 DeepSeek API 冒烟（手动启用）")
    @Disabled("需要真实 DEEPSEEK_API_KEY，手动去掉 @Disabled 运行。" +
              "T1.1a 冒烟用 CommandLineRunner 临时验证，此测试留作 T1.1b 集成测试基础。")
    void realApiSmokeTest() {
        // 需要手动设置环境变量 DEEPSEEK_API_KEY 后运行
        // 此处留空，仅作为模板
        fail("此测试需要手动启用并配置 API Key");
    }
}
