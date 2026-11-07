package com.zhiyu.modules.admin.service;

import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdminAiRoutingService} 集成单测（2026-11-08，H2 内存库）。
 *
 * <p>隔离说明同 {@link AdminAiMetricsServiceTest}：共享 H2 与其他用例的真实埋点并存，
 * 因此统计断言一律使用本类独有的 {@code rt76-*} 场景名或收紧到分钟级窗口，
 * 与执行顺序无关。事故锚点断言一律显式传 {@code anchorOverride}，
 * 不依赖墙钟与配置默认值（保证任意运行时刻结果确定）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAiRoutingServiceTest {

    private static final String SCENE_A = "rt76-chat";
    private static final String SCENE_B = "rt76-strong";

    @Autowired
    private AdminAiRoutingService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long rowId = 910_000_000_000L;

    private void insertTrace(String scene, String provider, String status, LocalDateTime createTime) {
        jdbc.update("""
                        INSERT INTO ai_trace(id, trace_id, user_id, scene, model, provider,
                            result_status, create_time)
                        VALUES(?,?,?,?,?,?,?,?)
                        """, rowId++, java.util.UUID.randomUUID().toString(), null, scene,
                "deepseek-chat", provider, status, createTime);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capabilityOf(Map<String, Object> result, String task) {
        return ((List<Map<String, Object>>) result.get("capabilities")).stream()
                .filter(row -> task.equals(row.get("task")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少能力行: " + task));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> distributionOf(Map<String, Object> result,
                                                     String scene, String provider) {
        return ((List<Map<String, Object>>) result.get("distribution")).stream()
                .filter(row -> scene.equals(row.get("scene"))
                        && (provider == null ? row.get("provider") == null : provider.equals(row.get("provider"))))
                .toList();
    }

    @Test
    @DisplayName("capabilities 呈现归属表生效状态（cloud-only/兜底/sceneKeys/crisis 红线）")
    void capabilitiesReflectRouterPolicy() {
        Map<String, Object> result = service.overview(null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> capabilities = (List<Map<String, Object>>) result.get("capabilities");
        assertEquals(12, capabilities.size(), "应覆盖全部 12 个已知能力（含 crisis）");

        Map<String, Object> chat = capabilityOf(result, "chat");
        assertEquals("standard", chat.get("tier"));
        assertEquals(Boolean.FALSE, chat.get("cloudOnly"));
        assertEquals(Boolean.TRUE, chat.get("fallbackAllowed"), "chat 默认允许本地兜底");
        assertEquals(List.of("llm-chat"), chat.get("sceneKeys"));

        Map<String, Object> listening = capabilityOf(result, "listening_summary");
        assertEquals("strong", listening.get("tier"));
        assertEquals(Boolean.TRUE, listening.get("cloudOnly"));
        assertEquals(Boolean.FALSE, listening.get("fallbackAllowed"), "cloud-only 恒不允许本地兜底");
        assertEquals(List.of("llm-strong", "llm-message"), listening.get("sceneKeys"));

        Map<String, Object> stuck = capabilityOf(result, "stuck_guide");
        assertEquals(List.of("llm-message"), stuck.get("sceneKeys"), "stuck_guide 与 message 共用 scene");

        Map<String, Object> shadow = capabilityOf(result, "moderation_shadow");
        assertEquals(List.of(), shadow.get("sceneKeys"), "影子判定不落 ai_trace");

        Map<String, Object> crisis = capabilityOf(result, "crisis");
        assertEquals("none", crisis.get("tier"));
        assertEquals(Boolean.FALSE, crisis.get("llmAllowed"), "crisis 红线：不走 LLM");
        assertNull(crisis.get("cloudModel"));
        assertEquals(List.of(), crisis.get("sceneKeys"));

        Map<String, Object> registry = (Map<String, Object>) result.get("registry");
        assertNotNull(registry);
        assertTrue(registry.containsKey("cloudAvailable"));
        assertTrue(registry.containsKey("localAvailable"));
        assertEquals(Boolean.FALSE, registry.get("shadowLocalEffective"), "测试装配默认不开演示模式");

        Map<String, Object> anchor = (Map<String, Object>) result.get("incidentAnchor");
        assertNotNull(anchor);
        assertEquals("config", anchor.get("source"), "缺省锚点来自配置");
        assertNotNull(anchor.get("at"));
    }

    @Test
    @DisplayName("distribution 按 scene+provider+period 透视状态计数（happy path）")
    void distributionPivotsBySceneProviderAndPeriod() {
        LocalDateTime now = LocalDateTime.now();
        // rt76-chat：云 2 OK + 1 FALLBACK；本地 1 OK（云不可用时的本地兜底产出）
        insertTrace(SCENE_A, "deepseek", "OK", now.minusHours(3));
        insertTrace(SCENE_A, "deepseek", "OK", now.minusHours(2));
        insertTrace(SCENE_A, "deepseek", "FALLBACK", now.minusHours(1));
        insertTrace(SCENE_A, "ollama", "OK", now.minusMinutes(30));
        // rt76-strong：1 BLOCKED（输入拦截，未发起调用，provider 为 null）
        insertTrace(SCENE_B, null, "BLOCKED", now.minusMinutes(10));

        // 锚点 = now：以上行全部早于锚点 → 全部 preFix（确定性，不依赖墙钟）
        Map<String, Object> result = service.overview(now.minusDays(1), now.plusMinutes(1), now);

        Map<String, Object> cloud = distributionOf(result, SCENE_A, "deepseek").stream()
                .findFirst().orElseThrow(() -> new AssertionError("缺 deepseek 行"));
        assertEquals("preFix", cloud.get("period"));
        assertEquals(3L, cloud.get("calls"));
        assertEquals(2L, cloud.get("okCount"));
        assertEquals(1L, cloud.get("fallbackCount"));
        assertEquals(0L, cloud.get("errorCount"));

        Map<String, Object> local = distributionOf(result, SCENE_A, "ollama").stream()
                .findFirst().orElseThrow(() -> new AssertionError("缺 ollama 行"));
        assertEquals("preFix", local.get("period"));
        assertEquals(1L, local.get("calls"));

        Map<String, Object> blocked = distributionOf(result, SCENE_B, null).stream()
                .findFirst().orElseThrow(() -> new AssertionError("缺 null provider 行"));
        assertEquals(1L, blocked.get("blockedCount"));

        // 纯计数口径：不出现用户标识字段
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> distribution = (List<Map<String, Object>>) result.get("distribution");
        for (Map<String, Object> row : distribution) {
            assertFalse(row.containsKey("userId"), "分布行不得含用户标识");
            assertFalse(row.containsKey("user_id"), "分布行不得含用户标识");
        }
    }

    @Test
    @DisplayName("period 按事故锚点分桶：同一 scene+provider 拆成 pre/post 两行")
    void distributionSplitsByAnchor() {
        LocalDateTime now = LocalDateTime.now();
        insertTrace(SCENE_A, "deepseek", "OK", now.minusHours(2));
        insertTrace(SCENE_A, "deepseek", "OK", now.minusMinutes(30));
        // 锚点卡在两行中间
        LocalDateTime anchor = now.minusHours(1);

        Map<String, Object> result = service.overview(now.minusDays(1), now.plusMinutes(1), anchor);
        List<Map<String, Object>> rows = distributionOf(result, SCENE_A, "deepseek");
        assertEquals(2, rows.size(), "同一 scene+provider 应按锚点拆两行");
        Map<String, Object> pre = rows.stream().filter(r -> "preFix".equals(r.get("period")))
                .findFirst().orElseThrow(() -> new AssertionError("缺 preFix 行"));
        Map<String, Object> post = rows.stream().filter(r -> "postFix".equals(r.get("period")))
                .findFirst().orElseThrow(() -> new AssertionError("缺 postFix 行"));
        assertEquals(1L, pre.get("calls"));
        assertEquals(1L, post.get("calls"));

        Map<String, Object> anchorInfo = (Map<String, Object>) result.get("incidentAnchor");
        assertEquals("override", anchorInfo.get("source"), "显式传锚点应标记 override");
    }

    @Test
    @DisplayName("cloudOnlyLocalCheck：独占 scene 的 ollama 记录按锚点拆历史/现行，verdict 如实")
    void cloudOnlyLocalCheckSplitsHistoryFromCurrent() {
        LocalDateTime now = LocalDateTime.now();
        // cloud-only 独占 scene（moderation → llm-moderation）：锚点前 2 行（历史事故证据）+ 锚点后 1 行（现行违规）
        insertTrace("llm-moderation", "ollama", "OK", now.minusHours(3));
        insertTrace("llm-moderation", "ollama", "FALLBACK", now.minusHours(2));
        insertTrace("llm-moderation", "ollama", "OK", now.minusMinutes(30));
        // 共享 scene（llm-message 与可兜底能力共用）的 ollama 行不得计入本检查
        insertTrace("llm-message", "ollama", "OK", now.minusMinutes(20));

        LocalDateTime anchor = now.minusHours(1);
        Map<String, Object> result = service.overview(now.minusHours(4), now.plusMinutes(1), anchor);

        @SuppressWarnings("unchecked")
        Map<String, Object> check = (Map<String, Object>) result.get("cloudOnlyLocalCheck");
        assertEquals(2L, check.get("preFixLocalCalls"), "锚点前历史事故证据");
        assertEquals(1L, check.get("postFixLocalCalls"), "锚点后现行违规");
        assertEquals("VIOLATION", check.get("verdict"));

        @SuppressWarnings("unchecked")
        List<String> exclusive = (List<String>) check.get("exclusiveScenes");
        assertTrue(exclusive.contains("llm-moderation"));
        assertTrue(exclusive.contains("llm-strong"));
        assertFalse(exclusive.contains("llm-message"), "共享 scene 不算 cloud-only 独占");

        // 锚点后移到最后一条 ollama 行之后 → 全部归历史，post=0、verdict=CLEAN
        Map<String, Object> clean = service.overview(now.minusHours(4), now.plusMinutes(1), now.plusMinutes(1));
        @SuppressWarnings("unchecked")
        Map<String, Object> cleanCheck = (Map<String, Object>) clean.get("cloudOnlyLocalCheck");
        assertEquals(0L, cleanCheck.get("postFixLocalCalls"));
        assertEquals("CLEAN", cleanCheck.get("verdict"));
    }

    @Test
    @DisplayName("区间外记录不计入；from/to 非法抛 1001")
    void windowValidation() {
        LocalDateTime now = LocalDateTime.now();
        insertTrace(SCENE_A, "deepseek", "OK", now.minusDays(30));

        Map<String, Object> result = service.overview(now.minusDays(7), now, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> distribution = (List<Map<String, Object>>) result.get("distribution");
        assertTrue(distribution.stream().noneMatch(row -> SCENE_A.equals(row.get("scene"))),
                "区间外记录不得计入");

        assertThrows(BusinessException.class, () -> service.overview(now, now.minusDays(1), null));
        assertThrows(BusinessException.class, () -> service.overview(now.minusDays(91), now, null));
    }
}
