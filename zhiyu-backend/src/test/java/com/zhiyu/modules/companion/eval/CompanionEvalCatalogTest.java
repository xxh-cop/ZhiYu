package com.zhiyu.modules.companion.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionEvalCatalogTest {

    @Test
    void loadsRequiredEvaluationSetsWithoutUserData() throws Exception {
        var catalog = new CompanionEvalCatalog(new ObjectMapper(),
                new ClassPathResource("eval/companion-eval.json"));

        Map<String, Object> summary = catalog.summary();

        // 2026-11-03 五层扩充 + 2026-11-04 SUMMARY×2：L2 24 + L3 17 + L5 8 + L4 10 = 59；
        // 2026-11-04 Z 批：+L6 摘要忠实性 13 + P2 调度层登记 7 → 79；L1 15；e2e 8
        // 2026-11-04 AA 批：P2 七条 cmd-* 转 COMMANDER_GATE 激活（去 SKIPPED 状态，总条数不变）
        assertEquals(79, summary.get("total"));
        assertEquals(15, summary.get("safetyTotal"));
        assertEquals(8, summary.get("e2eTotal"));
        assertFalse(summary.toString().contains("rawInput"));
        assertFalse(summary.toString().contains("userId"));

        @SuppressWarnings("unchecked")
        List<CompanionEvalCatalog.EvalCase> cases =
                (List<CompanionEvalCatalog.EvalCase>) summary.get("cases");
        var ids = new HashSet<String>();
        for (CompanionEvalCatalog.EvalCase item : cases) {
            assertTrue(ids.add(item.id()), "评测 id 不能重复");
            assertFalse(item.question().isBlank());
            assertFalse(item.expectation().isBlank());
        }
    }
}
