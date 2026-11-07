package com.zhiyu.ai.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * <h2>provider 维（2026-11-08 装配层批，D1/D4/D7/D8）</h2>
 *
 * <p>provider 与 tier 正交：云侧 strong=deepseek-reasoner 不动（D7）；本地 Ollama 只有
 * 单档（本地就一个模型），Route 解析到 (local, *) 时模型一律为 localModel。
 *
 * <p><b>能力兜底归属表集中在 {@link #FALLBACK_TASKS} 与 {@link #CLOUD_ONLY_TASKS}</b>，
 * 调用点不各自判定（D1「集中定义、可查」）：
 * <ul>
 *   <li>chat / recommend / message / rag / stuck_guide —— 云为主、允许本地兜底
 *       （各带一个 env 键可单独关停；message 覆盖寄语与创作回顾）</li>
 *   <li>listening_summary / companion / summary / lineart / moderation ——
 *       <b>cloud-only 硬约束，无任何配置键</b>（D1/D4；改它必须改代码走评审）</li>
 *   <li>moderation_shadow —— 仅演示双开关同时成立才允许本地（D8，见
 *       {@link #shadowLocalEffective()}）</li>
 * </ul>
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
            "moderation_shadow", "strong",
            "crisis", "none");

    /**
     * cloud-only 硬约束集合（D1/D4）：这些能力<b>没有</b>任何把兜底指向本地的配置键；
     * {@link ProviderRoutingValidator} 启动期复核本表，契约测试钉死。
     */
    static final Set<String> CLOUD_ONLY_TASKS = Set.of(
            "listening_summary", "companion", "summary", "lineart", "moderation");

    /** 允许本地兜底的能力键（各配一个 env 关停键，默认 local=允许）。 */
    private static final Set<String> FALLBACK_CAPABLE_TASKS = Set.of(
            "chat", "recommend", "message", "rag", "stuck_guide");

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
    private final String localModel;
    private final boolean localDemoMode;
    private final List<String> shadowDemoProviders;
    private final Map<String, Boolean> fallbackSwitches;

    @org.springframework.beans.factory.annotation.Autowired
    public ModelRouter(
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String standardModel,
            @Value("${zhiyu.ai.routing.strong-model:deepseek-reasoner}") String strongModel,
            @Value("${zhiyu.ai.routing.strong-enabled:true}") boolean strongEnabled,
            @Value("${zhiyu.ai.providers.local.model:deepseek-r1:8b}") String localModel,
            @Value("${zhiyu.ai.routing.local-demo-mode:false}") boolean localDemoMode,
            @Value("${zhiyu.moderation.soft-tier.shadow.demo-providers:ollama,mock}") String shadowDemoProviders,
            @Value("${zhiyu.ai.routing.fallback.chat:local}") String fallbackChat,
            @Value("${zhiyu.ai.routing.fallback.recommend:local}") String fallbackRecommend,
            @Value("${zhiyu.ai.routing.fallback.message:local}") String fallbackMessage,
            @Value("${zhiyu.ai.routing.fallback.rag:local}") String fallbackRag,
            @Value("${zhiyu.ai.routing.fallback.stuck-guide:local}") String fallbackStuckGuide) {
        this.standardModel = standardModel;
        this.strongModel = strongModel;
        this.strongEnabled = strongEnabled;
        this.localModel = localModel;
        this.localDemoMode = localDemoMode;
        this.shadowDemoProviders = shadowDemoProviders == null ? List.of()
                : java.util.Arrays.stream(shadowDemoProviders.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.fallbackSwitches = Map.of(
                "chat", allowsLocal(fallbackChat),
                "recommend", allowsLocal(fallbackRecommend),
                "message", allowsLocal(fallbackMessage),
                "rag", allowsLocal(fallbackRag),
                "stuck_guide", allowsLocal(fallbackStuckGuide));
        log.info("ModelRouter 初始化：standard={}，strong={}（enabled={}），crisis=none（不走 LLM）；"
                        + "本地兜底策略={}，localModel={}，影子本地生效={}",
                standardModel, strongModel, strongEnabled,
                fallbackSwitches, localModel, shadowLocalEffective());
    }

    /** 旧 3 参构造（既有测试装配）：无本地兜底、无演示模式，行为与分档前完全一致。 */
    public ModelRouter(String standardModel, String strongModel, boolean strongEnabled) {
        this(standardModel, strongModel, strongEnabled, "deepseek-r1:8b", false, "ollama,mock",
                "none", "none", "none", "none", "none");
    }

    private static boolean allowsLocal(String value) {
        return "local".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    public Route resolve(String task) {
        String tier = TASK_TIER.getOrDefault(task, "standard");
        return switch (tier) {
            case "none" -> new Route(task, "none", null, false, false);
            case "strong" -> new Route(task, "strong", strongModel, strongEnabled, true);
            default -> new Route(task, "standard", standardModel, true, true);
        };
    }

    /**
     * 该能力是否允许「云不可用 → 本地兜底」（D1 归属表唯一查询口）。
     *
     * <p>cloud-only 集合恒 false（含 moderation，D4）；moderation_shadow 走
     * {@link #shadowLocalEffective()} 双开关（D8）；其余按各自 env 开关。
     * 注意：本方法只表达策略，不判定本地端点是否装配——执行侧还需 registry 里有 local。
     */
    public boolean fallbackAllowed(String task) {
        if (CLOUD_ONLY_TASKS.contains(task)) {
            return false;
        }
        if ("moderation_shadow".equals(task)) {
            return shadowLocalEffective();
        }
        if (!FALLBACK_CAPABLE_TASKS.contains(task)) {
            return false;
        }
        return fallbackSwitches.getOrDefault(task, false);
    }

    /**
     * 影子判定本地演示是否生效（D8 双开关）：{@code local-demo-mode} 总闸 <b>且</b>
     * {@code shadow.demo-providers} 清单含 ollama——只开一个不生效（调用方按 cloud-only 处理）。
     * 清单同时是影子统计的演示归属排除口径（ModerationShadowService），
     * 保证「走本地产出的判定」与「统计里被排除的归属」是同一把钥匙。
     */
    public boolean shadowLocalEffective() {
        return localDemoMode && shadowDemoProviders.contains("ollama");
    }

    /** 本地 provider 的模型名（D7：本地单档，不区分 standard/strong）。 */
    public String localModel() {
        return localModel;
    }

    /** 可观测描述：用于运营页/日志展示当前路由表（不含任何密钥）。 */
    public String describe() {
        return "standard=" + standardModel + ", strong=" + strongModel
                + "(enabled=" + strongEnabled + "), crisis=none"
                + ", fallback=" + fallbackSwitches + ", shadowLocal=" + shadowLocalEffective();
    }
}
