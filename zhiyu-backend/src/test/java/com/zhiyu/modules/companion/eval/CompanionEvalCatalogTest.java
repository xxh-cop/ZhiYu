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

        assertEquals(20, summary.get("total"));
        assertEquals(10, summary.get("safetyTotal"));
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
