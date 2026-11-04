package com.zhiyu.modules.companion.eval;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.rag.KnowledgeAskService;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线评测 · 真实 provider 模式（手动触发，不进 CI）。
 *
 * <p>触发方式（从仓库根加载 .env 后）：
 * <pre>
 *   mvn test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=CompanionEvalRealIT"
 * </pre>
 * 需要环境变量 {@code DEEPSEEK_API_KEY}（非占位值）。报告自动写入
 * {@code docs/eval-runs/yyyyMMdd-HHmm-<tag>-<datasetVersion>.md/.json}；
 * 可用 {@code -Dzhiyu.eval.tag=<tag>} 区分跑分批次（如 baseline / routed）。
 *
 * <p>跑分纪律（docs/ai-benchmark-20261103.md §3.2）：报告由引擎产出，
 * 禁止手工填写通过率；L1 红线层任何失败 = 本次测试失败（一票否决）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 用真实 key 覆盖 test profile 的占位值，触发 SpringAiLlmClient 接管
        "spring.ai.openai.api-key=${DEEPSEEK_API_KEY:sk-placeholder}",
        "spring.ai.openai.chat.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = "^(?!sk-placeholder$).+$")
@DisplayName("离线评测（真实 provider）：全量五层跑分并落盘报告")
class CompanionEvalRealIT {

    @Autowired
    private CompanionEvalCatalog catalog;

    @Autowired
    private KnowledgeAskService knowledgeAskService;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ContentSafetyFilter safetyFilter;

    @Autowired
    private CircuitBreakerStore breaker;

    @Autowired
    private com.zhiyu.modules.dialogue.DialogueCommander commander;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void realRun_fullReportAndRedLineVeto() throws Exception {
        EvalRunnerEngine engine = new EvalRunnerEngine(
                catalog, knowledgeAskService, aiClient, llmClient, safetyFilter, breaker,
                commander, jdbc);
        String startedAt = LocalDateTime.now().toString();
        List<EvalRunnerEngine.CaseResult> results = engine.allRunnableCases().stream()
                .map(c -> engine.runCase(c, EvalRunnerEngine.Mode.REAL))
                .toList();

        String tag = System.getProperty("zhiyu.eval.tag", "real");
        @SuppressWarnings("unchecked")
        String version = (String) ((Map<String, Object>) catalog.summary()).get("version");
        var reportPath = engine.writeReport(version, EvalRunnerEngine.Mode.REAL,
                aiClient.modelName(), startedAt, results, tag);
        System.out.println("[EVAL] 报告已写入: " + reportPath);

        // L1 红线层：一票否决
        List<EvalRunnerEngine.CaseResult> l1Failures = results.stream()
                .filter(r -> "L1".equals(r.layer()) && "FAIL".equals(r.status())).toList();
        assertThat(l1Failures)
                .as("L1 红线层任何失败都是阻断性的: %s", l1Failures)
                .isEmpty();

        // 其余层的失败不阻断（如实记录进报告），但必须跑完五层
        long ran = results.stream().filter(r -> !"SKIP".equals(r.status())).count();
        assertThat(ran).isGreaterThan(0);
        System.out.println("[EVAL] 汇总: " + engine.summarize(version,
                EvalRunnerEngine.Mode.REAL, aiClient.modelName(), startedAt, results).get("byLayer"));
    }
}
