package com.zhiyu.modules.admin.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.routing.ModelRouter;
import com.zhiyu.modules.admin.service.RuntimeSettings;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容审核高危漏放率门槛评测（一次性评测 harness，不进 CI）。
 *
 * <p>触发方式（仓库根加载 .env 后）：
 * <pre>
 *   mvn test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=ModerationGateEvalIT" \
 *       "-Dzhiyu.moderation.mode=full" "-Dzhiyu.moderation.tag=gate-r1"
 * </pre>
 *
 * <p>口径：直接调 {@link AiClient#moderationSuggest(String)}（与
 * {@code AdminModerationService} 同一门面方法），prompt 渲染规则与业务层逐字一致
 * （{@code {targetType}/{title}/{content}/{mediaNote}} 替换，mediaNote 恒为「无」——
 * 本轮只评文本）。判定映射：HAS_CONCERNS→拦截、UNCERTAIN→关注、NO_OBVIOUS_ISSUE→放行、
 * raw==null→降级（UNAVAILABLE）、JSON 解析失败→降级（PARSE_FAIL；业务层同样按
 * available=false 处理）。降级不进漏放率分母，单列报告。
 *
 * <p>与生产链路的差异（如实声明）：不走 HTTP/JWT、不经
 * {@code AdminModerationService} 的 DB 取数与 30 次/小时频控、不做输入侧检测审计；
 * 评测 userId 固定为 {@value #EVAL_USER_ID}（合成 id，无对应用户行），审计与指标行
 * 仍写入 MySQL 的 ai_audit_log / ai_trace（append-only，评测后保留并在报告记录区间）。
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = "^(?!sk-placeholder$).+$")
@DisplayName("内容审核门槛评测（真实 provider）：高危漏放率")
class ModerationGateEvalIT {

    /** 合成评测用户 id（无对应用户行；审计/指标行以此归集，便于台账核对）。 */
    static final long EVAL_USER_ID = 2990000000000000099L;

    @Autowired
    private AiClient aiClient;

    @Autowired
    @Qualifier("moderationSuggestPrompt")
    private String promptTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ModelRouter modelRouter;

    @Autowired
    private RuntimeSettings runtimeSettings;

    @Autowired
    private com.zhiyu.ai.client.LlmClient llmClient;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(EVAL_USER_ID, 3));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void run() throws Exception {
        String mode = System.getProperty("zhiyu.moderation.mode", "full");
        String tag = System.getProperty("zhiyu.moderation.tag", "gate");
        String datasetPath = System.getProperty("zhiyu.moderation.dataset",
                "../.ai-tmp/moderation-eval/dataset.json");
        Path path = Path.of(datasetPath).toAbsolutePath().normalize();
        JsonNode ds = json.readTree(Files.readString(path, StandardCharsets.UTF_8));
        ArrayNode pool = "probe".equals(mode)
                ? (ArrayNode) ds.get("probes")
                : (ArrayNode) ds.get("samples");

        System.out.println("[MOD-EVAL] dataset=" + path + " version=" + ds.get("version").asText()
                + " mode=" + mode + " pool=" + pool.size());
        System.out.println("[MOD-EVAL] llmClientImpl=" + llmClient.getClass().getSimpleName()
                + " modelMode=" + aiClient.modelMode()
                + " standardModel=" + aiClient.modelName()
                + " routing=" + modelRouter.describe()
                + " temperature=" + runtimeSettings.number("model", "temperature")
                + " maxTokens=" + (int) runtimeSettings.number("model", "maxTokens"));
        if (!"EXTERNAL".equals(aiClient.modelMode())) {
            throw new IllegalStateException("评测必须走真实模型，当前是 Mock");
        }

        LocalDateTime runStart = LocalDateTime.now();
        String startedAt = runStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        List<Map<String, Object>> rows = new ArrayList<>();
        int i = 0;
        for (JsonNode sample : pool) {
            i++;
            String id = sample.get("id").asText();
            String targetType = sample.get("targetType").asText();
            boolean isWork = "work".equals(targetType);
            String title = isWork ? sample.get("title").asText() : "（评论，无标题）";
            String content = sample.get("content").asText();
            String prompt = promptTemplate
                    .replace("{targetType}", isWork ? "作品" : "评论")
                    .replace("{title}", title)
                    .replace("{content}", content)
                    .replace("{mediaNote}", "无");

            long t0 = System.nanoTime();
            String raw = aiClient.moderationSuggest(prompt);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000L;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("category", sample.get("category").asText());
            row.put("polarity", sample.get("polarity").asText());
            row.put("riskLevel", sample.get("riskLevel").asInt());
            row.put("targetType", targetType);
            row.put("latencyMs", latencyMs);
            if (raw == null) {
                row.put("verdict", "UNAVAILABLE");
            } else {
                Map<String, Object> parsed = parse(raw);
                if (parsed == null) {
                    row.put("verdict", "PARSE_FAIL");
                    row.put("rawPreview", raw.substring(0, Math.min(300, raw.length())));
                } else {
                    row.put("verdict", switch (parsed.get("tendency").toString()) {
                        case "HAS_CONCERNS" -> "BLOCK";
                        case "UNCERTAIN" -> "REVIEW";
                        default -> "PASS";
                    });
                    row.put("tendency", parsed.get("tendency"));
                    row.put("riskPoints", parsed.get("riskPoints"));
                    row.put("uncertainty", parsed.get("uncertainty"));
                }
            }
            rows.add(row);
            System.out.println("[MOD-EVAL] " + i + "/" + pool.size() + " " + id
                    + " verdict=" + row.get("verdict") + " latency=" + latencyMs + "ms");
        }

        // 等异步 ai_trace 写入线程排空（AiCallMetrics 为单线程 daemon executor）
        Thread.sleep(3000);

        Map<String, Object> traceStats = stats(
                "SELECT COUNT(*) c, COALESCE(MIN(id),0) mn, COALESCE(MAX(id),0) mx,"
                        + " COALESCE(SUM(input_tokens),0) tin, COALESCE(SUM(output_tokens),0) tout"
                        + " FROM ai_trace WHERE user_id=? AND scene='llm-moderation' AND create_time>=?",
                runStart);
        List<Map<String, Object>> traceByStatus = jdbc.queryForList(
                "SELECT result_status s, COUNT(*) c FROM ai_trace"
                        + " WHERE user_id=? AND scene='llm-moderation' AND create_time>=? GROUP BY result_status",
                EVAL_USER_ID, runStart);
        List<Map<String, Object>> traceByModel = jdbc.queryForList(
                "SELECT model m, COUNT(*) c, COALESCE(SUM(input_tokens),0) tin,"
                        + " COALESCE(SUM(output_tokens),0) tout FROM ai_trace"
                        + " WHERE user_id=? AND scene='llm-moderation' AND create_time>=? GROUP BY model",
                EVAL_USER_ID, runStart);
        Map<String, Object> auditStats = stats(
                "SELECT COUNT(*) c, COALESCE(MIN(id),0) mn, COALESCE(MAX(id),0) mx, 0 tin, 0 tout"
                        + " FROM ai_audit_log WHERE user_id=? AND scene='moderation' AND create_time>=?",
                runStart);
        List<Map<String, Object>> auditByAction = jdbc.queryForList(
                "SELECT action a, COUNT(*) c FROM ai_audit_log"
                        + " WHERE user_id=? AND scene='moderation' AND create_time>=? GROUP BY action",
                EVAL_USER_ID, runStart);

        ObjectNode out = json.createObjectNode();
        out.put("datasetVersion", ds.get("version").asText());
        out.put("mode", mode);
        out.put("tag", tag);
        out.put("startedAt", startedAt);
        out.put("evalUserId", EVAL_USER_ID);
        out.put("modelMode", aiClient.modelMode());
        out.put("standardModel", aiClient.modelName());
        out.put("routing", modelRouter.describe());
        out.put("temperature", runtimeSettings.number("model", "temperature"));
        out.put("maxTokens", (int) runtimeSettings.number("model", "maxTokens"));
        out.putPOJO("results", rows);
        out.putPOJO("traceStats", traceStats);
        out.putPOJO("traceByStatus", traceByStatus);
        out.putPOJO("traceByModel", traceByModel);
        out.putPOJO("auditStats", auditStats);
        out.putPOJO("auditByAction", auditByAction);

        Path outPath = Path.of("../.ai-tmp/moderation-eval",
                "results-" + tag + "-" + mode + ".json").toAbsolutePath().normalize();
        Files.writeString(outPath, json.writerWithDefaultPrettyPrinter().writeValueAsString(out),
                StandardCharsets.UTF_8);
        System.out.println("[MOD-EVAL] results written: " + outPath);
        System.out.println("[MOD-EVAL] trace=" + traceStats + " byStatus=" + traceByStatus
                + " byModel=" + traceByModel);
        System.out.println("[MOD-EVAL] audit=" + auditStats + " byAction=" + auditByAction);

        org.assertj.core.api.Assertions.assertThat(rows).hasSize(pool.size());
    }

    private Map<String, Object> stats(String sql, LocalDateTime runStart) {
        return jdbc.queryForMap(sql, EVAL_USER_ID, runStart);
    }

    /** 与 AdminModerationService.parse 同口径：容忍 markdown 围栏；结构不合法返回 null。 */
    private Map<String, Object> parse(String raw) {
        try {
            String text = raw.trim()
                    .replaceAll("(?s)^```(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*```$", "")
                    .trim();
            JsonNode node = json.readTree(text);
            String tendency = node.path("tendency").asText("");
            if (!java.util.Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN").contains(tendency)) {
                return null;
            }
            List<Map<String, String>> riskPoints = new ArrayList<>();
            for (JsonNode rp : node.path("riskPoints")) {
                String note = rp.path("note").asText("");
                if (note.isBlank()) {
                    return null;
                }
                String location = rp.path("location").asText("");
                riskPoints.add(Map.of("location", location.isBlank() ? "content" : location,
                        "note", note));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tendency", tendency);
            out.put("riskPoints", riskPoints);
            out.put("uncertainty", node.path("uncertainty").asText(""));
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
