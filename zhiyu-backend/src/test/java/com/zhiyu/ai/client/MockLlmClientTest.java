package com.zhiyu.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MockLlmClient} 单测（T1.1a）。
 *
 * <p>验证 Mock 降级行为：
 * <ul>
 *   <li>{@code streamChat} 逐字符回调</li>
 *   <li>{@code recommendReason} 返回非 null 且包含手作名</li>
 *   <li>{@code workMessage} 返回非 null</li>
 * </ul>
 *
 * <p>不依赖 Spring 上下文、不打真 API。
 */
class MockLlmClientTest {

    @Test
    @DisplayName("streamChat 逐字符回调 onToken")
    void streamChat_invokesCallback() {
        MockLlmClient client = new MockLlmClient();
        StringBuilder sb = new StringBuilder();
        List<ChatMessage> history = List.of(ChatMessage.user("我有点焦虑"));

        client.streamChat(history, sb::append);

        assertTrue(sb.length() > 0, "回调应产生非空输出");
        assertTrue(sb.toString().contains("陪"), "应包含陪伴语");
    }

    @Test
    @DisplayName("recommendReason 返回包含手作名的推荐理由")
    void recommendReason_returnsNonEmptyWithCraftNames() {
        MockLlmClient client = new MockLlmClient();
        String result = client.recommendReason("焦虑", List.of("扎染", "赣绣"));

        assertNotNull(result);
        assertTrue(result.contains("扎染"), "应包含手作名「扎染」");
        assertTrue(result.length() > 10, "推荐理由应有一定长度");
    }

    @Test
    @DisplayName("workMessage 返回非空寄语")
    void workMessage_returnsNonEmpty() {
        MockLlmClient client = new MockLlmClient();
        String result = client.workMessage("我的扎染方巾", "平静");

        assertNotNull(result);
        assertTrue(result.length() > 10, "寄语应有一定长度");
    }

    @Test
    @DisplayName("ChatMessage 工厂方法正确设置 role")
    void chatMessage_factoryMethods() {
        assertEquals("system", ChatMessage.system("sys").role());
        assertEquals("user", ChatMessage.user("hi").role());
        assertEquals("assistant", ChatMessage.assistant("reply").role());
    }
}
