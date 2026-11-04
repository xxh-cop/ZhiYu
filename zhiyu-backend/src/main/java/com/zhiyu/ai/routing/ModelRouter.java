package com.zhiyu.ai.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务分档模型路由（2026-11-04，B2；见 ai-config/ai-capabilities.yaml 的 routing 段）。
 *
 * <p>最小静态映射：task → tier → model。不做动态打分路由（分类调用本身延迟与成本翻倍）。
 *
 * <p>档位语义：
 * <ul>
 *   <li>{@code standard} —— 日常对话、推荐、寄语、RAG、陪伴编排（默认 deepseek-chat）</li>
 *   <li>{@code strong}   —— 倾听小结、（预留）内容审核辅助（默认 deepseek-reasoner，
 *       与 standard 同 endpoint/key，模型名可用环境变量覆盖）</li>
 *   <li>{@code qwen}     —— .env 未配置 QWEN_API_KEY / QWEN_BASE_URL，
 *       {@link Route#available()} 恒为 false，<b>显式不可用</b>，调用方必须走回落链</li>
 * </ul>
 *
 * <p><b>红线</b>：{@code crisis}（危机干预）不路由到任何 LLM 档位——
 * 高危命中直接返回预置干预话术，{@link Route#llmAllowed()} 为 false。
 * Agent 的「自主决策」在危机场景是负资产。
 */
@Slf4j
@Component
public class ModelRouter {

    /** 任务 → 档位。crisis → none 表示禁止 LLM。 */
    private static final Map<String, String> TASK_TIER = Map.of(
            "chat", "standard",
            "companion", "standard",
            "recommend", "standard",
            "message", "standard",
            "rag", "standard",
            "listening_summary", "strong",
            "moderation", "strong",
            "crisis", "none");

    /**
     * @param task       业务任务名
     * @param tier       解析出的档位（standard / strong / none）
     * @param model      该档实际使用的模型名；none 档为 null
     * @param available  档位当前可用（未配置档位为 false，调用方必须回落）
     * @param llmAllowed 该任务是否允许调用 LLM（crisis=false，红线）
     */
    public record Route(String task, String tier, String model, boolean available, boolean llmAllowed) {
    }

    private final String standardModel;
    private final String strongModel;
    private final boolean strongEnabled;

    public ModelRouter(
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String standardModel,
            @Value("${zhiyu.ai.routing.strong-model:deepseek-reasoner}") String strongModel,
            @Value("${zhiyu.ai.routing.strong-enabled:true}") boolean strongEnabled) {
        this.standardModel = standardModel;
        this.strongModel = strongModel;
        this.strongEnabled = strongEnabled;
        log.info("ModelRouter 初始化：standard={}，strong={}（enabled={}），crisis=none（不走 LLM）",
                standardModel, strongModel, strongEnabled);
    }

    public Route resolve(String task) {
        String tier = TASK_TIER.getOrDefault(task, "standard");
        return switch (tier) {
            case "none" -> new Route(task, "none", null, false, false);
            case "strong" -> new Route(task, "strong", strongModel, strongEnabled, true);
            default -> new Route(task, "standard", standardModel, true, true);
        };
    }

    /** 可观测描述：用于运营页/日志展示当前路由表（不含任何密钥）。 */
    public String describe() {
        return "standard=" + standardModel + ", strong=" + strongModel
                + "(enabled=" + strongEnabled + "), crisis=none";
    }
}
