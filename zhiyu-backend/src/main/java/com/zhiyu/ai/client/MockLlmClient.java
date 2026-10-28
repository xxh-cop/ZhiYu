package com.zhiyu.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@link LlmClient} 的 Mock 降级实现（T1.1a）。
 *
 * <p>生效条件：{@code spring.ai.openai.api-key} 为占位值 {@code sk-placeholder}
 * （即用户未配置真实 API Key）。此时 {@link SpringAiLlmClient} 不生效，
 * 本类作为唯一 {@link LlmClient} bean 注入。
 *
 * <p>用途：本地开发 / CI 环境无 API Key 时应用仍能启动，功能不阻塞。
 * 返回固定话术，不调任何外部 API。
 *
 * <p><b>生产隔离</b>：{@code prod} profile 下本类永不注册；若 prod 缺少真实 Key，
 * 由 {@link ProductionLlmClientConfig} 在启动期直接失败并给出配置指引，
 * 不允许静默降级到 mock（见 AGENTS.md §4）。
 */
@Slf4j
@Component
@Profile("!prod")
// 当 api-key 为空串、缺失或仍为占位值时生效；与 SpringAiLlmClient 的真实 Key 条件严格互斥
@ConditionalOnExpression("'${spring.ai.openai.api-key:}' == '' or '${spring.ai.openai.api-key:}' == 'sk-placeholder'")
public class MockLlmClient implements LlmClient {

    public MockLlmClient() {
        log.info("MockLlmClient 初始化完成（降级模式：未配置 DEEPSEEK_API_KEY）");
    }

    @Override
    public void streamChat(List<ChatMessage> history, Consumer<String> onToken) {
        String reply = "我在这里陪着你。每一步都是你与自己对话的方式，慢慢来就好。";
        // 模拟逐字输出
        for (char ch : reply.toCharArray()) {
            onToken.accept(String.valueOf(ch));
        }
    }

    @Override
    public String recommendReason(String mood, List<String> craftNames) {
        return String.format(
                "你现在的状态适合做一些能让双手安静下来的事。「%s」的节奏感能帮你" +
                "把注意力从纷乱的思绪拉回指尖。这不是任务，是一次温柔的陪伴。",
                String.join("、", craftNames));
    }

    @Override
    public String workMessage(String workTitle, String mood) {
        return "你用一针一线缝出了此刻的心意，这份专注本身就是疗愈。愿这份温度留伴你很久。";
    }

    @Override
    public String reflectionSummary(String prompt) {
        return "你完成了这次创作，并把注意力留在了手边的动作上。愿意为自己留出这段时间，本身就值得被看见。";
    }

    @Override
    public String chat(String prompt) {
        return "（降级模式）非遗知识库暂不可用，请稍后再试。";
    }
}
