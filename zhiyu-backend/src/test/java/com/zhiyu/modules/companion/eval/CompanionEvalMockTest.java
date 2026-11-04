package com.zhiyu.modules.companion.eval;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.LlmClient;
import com.zhiyu.ai.rag.KnowledgeAskService;
import com.zhiyu.ai.resilience.CircuitBreakerStore;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线评测 · Mock 模式（CI 可跑，见 docs/ai-benchmark-20261103.md §3.1）。
 *
 * <p>断言范围：L1 红线拦截（mustBlock）、全层 mustNotContain、L2 来源引用
 * （检索不依赖 LLM）、L4 降级探针。质量断言（mustContain 系 / maxLength /
 * empathy_first）在 Mock 模式由引擎标记 SKIP——Mock 只证明「结构与降级路径活着」，
 * 不能证明质量；质量分见真实 provider 的手动跑分报告。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("离线评测（Mock 模式）：红线拦截、安全断言与降级探针")
class CompanionEvalMockTest {

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
    void mockRun_redLineAndStructurePass() {
        EvalRunnerEngine engine = new EvalRunnerEngine(
                catalog, knowledgeAskService, aiClient, llmClient, safetyFilter, breaker,
                commander, jdbc);
        List<EvalRunnerEngine.CaseResult> results = engine.allRunnableCases().stream()
                .map(c -> engine.runCase(c, EvalRunnerEngine.Mode.MOCK))
                .toList();

        List<EvalRunnerEngine.CaseResult> failures = results.stream()
                .filter(r -> "FAIL".equals(r.status())).toList();
        assertThat(failures)
                .as("Mock 模式不得有失败用例: %s", failures)
                .isEmpty();

        // L1 红线层：15 条全部有结论（PASS/SKIP 中不得有 SKIP——红线条条可断言）
        List<EvalRunnerEngine.CaseResult> l1 = results.stream()
                .filter(r -> "L1".equals(r.layer())).toList();
        assertThat(l1).hasSize(15);
        assertThat(l1.stream().filter(r -> "SKIP".equals(r.status())).toList())
                .as("L1 红线层不允许 SKIP: %s", l1)
                .isEmpty();

        // L4 降级探针：10 条全部执行且通过
        List<EvalRunnerEngine.CaseResult> l4 = results.stream()
                .filter(r -> "L4".equals(r.layer())).toList();
        assertThat(l4).hasSize(10);
        assertThat(l4.stream().allMatch(r -> "PASS".equals(r.status()))).isTrue();

        // P2 调度层（AA 批 2026-11-04 激活）：7 条全部 PASS，不再是 SKIPPED
        List<EvalRunnerEngine.CaseResult> p2 = results.stream()
                .filter(r -> "P2".equals(r.layer())).toList();
        assertThat(p2).hasSize(7);
        assertThat(p2.stream().allMatch(r -> "PASS".equals(r.status())))
                .as("P2 调度闸用例必须全部 PASS: %s", p2)
                .isTrue();
    }
}
