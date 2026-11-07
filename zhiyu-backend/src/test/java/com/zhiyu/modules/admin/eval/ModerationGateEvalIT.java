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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内容审核门槛评测 harness（断点续跑版，不进 CI）。
 *
 * <p>触发方式（仓库根加载 .env 后）：
 * <pre>
 *   mvn test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=ModerationGateEvalIT" \
 *       "-Dzhiyu.moderation.mode=full" "-Dzhiyu.moderation.tag=<tag>" \
 *       "-Dzhiyu.moderation.datasets=<逗号分隔的数据集路径>" "-Dzhiyu.moderation.budget=<本臂调用上限>"
 * </pre>
 *
 * <p>断点续跑（2026-11-06）：每条完成即 append 到
 * {@code ../.ai-tmp/moderation-eval/runs/<tag>-<mode>.jsonl}（含 case id、判定、tendency、
 * narrative、耗时、per-case tokens/模型/回落标记、原始输出、时间戳）；重启读取已有 jsonl，
 * 跳过已完成 id，不重复、不跳号。预算为 provider 调用口径：每条样本记 1 次，
 * strong→standard 回落按实际消费 2 次计入，达到预算即停（未跑完的 id 如实列出）。
 *
 * <p>口径：直接调 {@link AiClient#moderationSuggest(String)}（与
 * {@code AdminModerationService} 同一门面方法），prompt 渲染走服务端静态 render（开关裁切一致）。
 * 判定映射：HAS_CONCERNS→拦截（BLOCK）、UNCERTAIN→关注（REVIEW）、SOFT_CONCERN→软档（SOFT）、
 * NO_OBVIOUS_ISSUE→放行（PASS）、raw==null→降级（UNAVAILABLE）、JSON 解析失败→降级（PARSE_FAIL）。
 * 降级/异常不进任何比例分母，单列报告；latencyMs>60s 额外打 timeout 标记。
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = "^(?!sk-placeholder$).+$")
@DisplayName("内容审核门槛评测（真实 provider）：高危漏放率")
class ModerationGateEvalIT {

    /** 合成评测用户 id（无对应用户行；审计/指标行以此归集，便于台账核对）。 */
    static final long EVAL_USER_ID = 2990000000000000099L;

    /** 单条墙钟超过该值打 timeout 标记（DeepSeek reasoner P95 ~6s，60s 为异常量级）。 */
    private static final long TIMEOUT_FLAG_MS = 60_000L;

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
        long budget = Long.parseLong(System.getProperty("zhiyu.moderation.budget", "100000"));

        // ── 数据集：支持逗号分隔多文件合并；pendingReview=true 的一律不执行 ──
        String datasetsProp = System.getProperty("zhiyu.moderation.datasets",
                System.getProperty("zhiyu.moderation.dataset", "../.ai-tmp/moderation-eval/dataset.json"));
        List<JsonNode> poolList = new ArrayList<>();
        List<String> versions = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (String p : datasetsProp.split(",")) {
            Path dp = Path.of(p.trim()).toAbsolutePath().normalize();
            JsonNode ds = json.readTree(Files.readString(dp, StandardCharsets.UTF_8));
            versions.add(ds.get("version").asText());
            ArrayNode arr = "probe".equals(mode)
                    ? (ArrayNode) ds.get("probes")
                    : (ArrayNode) ds.get("samples");
            for (JsonNode s : arr) {
                if (s.has("pendingReview") && s.get("pendingReview").asBoolean()) {
                    continue;
                }
                String id = s.get("id").asText();
                if (!seenIds.add(id)) {
                    throw new IllegalStateException("数据集 id 重复: " + id);
                }
                poolList.add(s);
            }
        }
        String datasetDesc = datasetsProp + " versions=" + String.join("+", versions);

        // ── 显式 id 清单驱动（2026-11-07 口径事故后改为清单驱动，禁用预算截断定界） ──
        String idFile = System.getProperty("zhiyu.moderation.idfile", "");
        if (!idFile.isBlank()) {
            Set<String> allowed = new LinkedHashSet<>(
                    Files.readAllLines(Path.of(idFile.trim()).toAbsolutePath().normalize(),
                            StandardCharsets.UTF_8));
            allowed.removeIf(String::isBlank);
            int before = poolList.size();
            poolList.removeIf(s -> !allowed.contains(s.get("id").asText()));
            System.out.println("[MOD-EVAL] idfile 驱动: " + idFile + " 清单 " + allowed.size()
                    + " 条，池 " + before + " → " + poolList.size()
                    + (allowed.size() != poolList.size()
                            ? "（警告：清单与池数量不一致，有 id 未命中或重复）" : ""));
        }

        System.out.println("[MOD-EVAL] dataset=" + datasetDesc + " mode=" + mode + " pool=" + poolList.size());
        boolean softTier = Boolean.parseBoolean(
                System.getProperty("zhiyu.moderation.soft-tier.enabled", "false"));
        System.out.println("[MOD-EVAL] llmClientImpl=" + llmClient.getClass().getSimpleName()
                + " modelMode=" + aiClient.modelMode()
                + " standardModel=" + aiClient.modelName()
                + " routing=" + modelRouter.describe()
                + " temperature=" + runtimeSettings.number("model", "temperature")
                + " maxTokens=" + (int) runtimeSettings.number("model", "maxTokens")
                + " softTier=" + softTier + " budget=" + budget);
        if (!"EXTERNAL".equals(aiClient.modelMode())) {
            throw new IllegalStateException("评测必须走真实模型，当前是 Mock");
        }

        // ── 断点续跑：读取已有 jsonl，已完成 id 直接复用 ──
        Path runsDir = Path.of("../.ai-tmp/moderation-eval/runs").toAbsolutePath().normalize();
        Files.createDirectories(runsDir);
        Path jsonl = runsDir.resolve(tag + "-" + mode + ".jsonl");
        List<Map<String, Object>> doneRows = new ArrayList<>();
        Set<String> doneIds = new LinkedHashSet<>();
        long doneCalls = 0;
        if (Files.exists(jsonl)) {
            for (String line : Files.readAllLines(jsonl, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> row = json.readValue(line, Map.class);
                doneRows.add(row);
                doneIds.add(row.get("id").toString());
                doneCalls += ((Number) row.getOrDefault("calls", 1)).longValue();
            }
            System.out.println("[MOD-EVAL] resume: 已完成 " + doneIds.size() + " 条（calls=" + doneCalls
                    + "），从断点续跑: " + jsonl);
        }

        LocalDateTime runStart = LocalDateTime.now();
        String startedAt = runStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        List<Map<String, Object>> newRows = new ArrayList<>();
        long callsMade = doneCalls;
        long lastTraceId = 0;
        int i = 0;
        int skipped = 0;
        for (JsonNode sample : poolList) {
            String id = sample.get("id").asText();
            if (doneIds.contains(id)) {
                skipped++;
                continue;
            }
            i++;
            if (callsMade >= budget) {
                System.out.println("[MOD-EVAL] 预算已达上限（" + budget + "），停止。未执行 id: " + id
                        + " 及后续 " + (poolList.size() - skipped - newRows.size() - 1) + " 条");
                break;
            }
            String targetType = sample.get("targetType").asText();
            boolean isWork = "work".equals(targetType);
            String title = isWork ? sample.get("title").asText() : "（评论，无标题）";
            String content = sample.get("content").asText();
            String prompt = com.zhiyu.modules.admin.service.AdminModerationService.render(
                    promptTemplate, softTier, isWork ? "作品" : "评论", title, content, "无");

            long t0 = System.nanoTime();
            String raw = aiClient.moderationSuggest(prompt);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
            callsMade++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("arm", softTier ? "on" : "off");
            row.put("category", sample.get("category").asText());
            row.put("polarity", sample.get("polarity").asText());
            row.put("riskLevel", sample.get("riskLevel").asInt());
            row.put("targetType", targetType);
            row.put("latencyMs", latencyMs);
            row.put("timeout", latencyMs > TIMEOUT_FLAG_MS);
            row.put("ts", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            if (raw == null) {
                row.put("verdict", "UNAVAILABLE");
                row.put("raw", null);
            } else {
                try {
                    JsonNode nv = json.readTree(raw).path("narrative");
                    row.put("narrative", nv.isMissingNode() ? null
                            : nv.isTextual() ? nv.asText() : nv.toString());
                } catch (Exception e) {
                    row.put("narrative", null);
                }
                Map<String, Object> parsed = parse(raw);
                if (parsed == null) {
                    row.put("verdict", "PARSE_FAIL");
                    row.put("rawPreview", raw.substring(0, Math.min(300, raw.length())));
                } else {
                    row.put("verdict", switch (parsed.get("tendency").toString()) {
                        case "HAS_CONCERNS" -> "BLOCK";
                        case "UNCERTAIN" -> "REVIEW";
                        case "SOFT_CONCERN" -> "SOFT";
                        default -> "PASS";
                    });
                    row.put("tendency", parsed.get("tendency"));
                    row.put("riskPoints", parsed.get("riskPoints"));
                    row.put("uncertainty", parsed.get("uncertainty"));
                }
                row.put("raw", raw);
            }

            // ── 逐条 ai_trace 消费（异步写入，短重试；per-case tokens/模型/回落） ──
            Map<String, Object> tr = consumeTrace(lastTraceId, runStart);
            if (tr != null) {
                lastTraceId = ((Number) tr.get("maxId")).longValue();
                row.put("inputTokens", tr.get("tin"));
                row.put("outputTokens", tr.get("tout"));
                row.put("models", tr.get("models"));
                int caseCalls = ((Number) tr.get("rows")).intValue();
                row.put("calls", caseCalls);
                row.put("fallback", caseCalls > 1);
                callsMade += caseCalls - 1;
            } else {
                row.put("inputTokens", null);
                row.put("outputTokens", null);
                row.put("models", null);
                row.put("calls", 1);
                row.put("fallback", false);
            }

            newRows.add(row);
            Files.writeString(jsonl, json.writeValueAsString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("[MOD-EVAL] " + (doneIds.size() + newRows.size()) + "/" + poolList.size()
                    + " " + id + " verdict=" + row.get("verdict")
                    + " calls=" + callsMade + " latency=" + latencyMs + "ms");
        }

        List<Map<String, Object>> rows = new ArrayList<>(doneRows);
        rows.addAll(newRows);

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
        out.put("datasetVersion", String.join("+", versions));
        out.put("mode", mode);
        out.put("tag", tag);
        out.put("startedAt", startedAt);
        out.put("evalUserId", EVAL_USER_ID);
        out.put("modelMode", aiClient.modelMode());
        out.put("standardModel", aiClient.modelName());
        out.put("routing", modelRouter.describe());
        out.put("temperature", runtimeSettings.number("model", "temperature"));
        out.put("maxTokens", (int) runtimeSettings.number("model", "maxTokens"));
        out.put("softTier", softTier);
        out.put("budget", budget);
        out.put("callsMade", callsMade);
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
        System.out.println("[MOD-EVAL] rows=" + rows.size() + " calls=" + callsMade
                + " results written: " + outPath);
        System.out.println("[MOD-EVAL] trace=" + traceStats + " byStatus=" + traceByStatus
                + " byModel=" + traceByModel);
        System.out.println("[MOD-EVAL] audit=" + auditStats + " byAction=" + auditByAction);
    }

    /**
     * 消费本调用产生的 ai_trace 新行（id 单调，按 id 游标取数）。
     * 异步写入线程通常毫秒级落库；最多重试 3×800ms，仍无则返回 null（tokens 记 null，不估算）。
     */
    private Map<String, Object> consumeTrace(long afterId, LocalDateTime runStart) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                Thread.sleep(800);
            }
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, model, input_tokens, output_tokens FROM ai_trace"
                            + " WHERE user_id=? AND scene='llm-moderation' AND id>? AND create_time>=?"
                            + " ORDER BY id",
                    EVAL_USER_ID, afterId, runStart);
            if (!rows.isEmpty()) {
                long maxId = 0;
                long tin = 0;
                long tout = 0;
                List<String> models = new ArrayList<>();
                boolean tokensNull = false;
                for (Map<String, Object> r : rows) {
                    long id = ((Number) r.get("id")).longValue();
                    maxId = Math.max(maxId, id);
                    models.add(String.valueOf(r.get("model")));
                    if (r.get("input_tokens") == null || r.get("output_tokens") == null) {
                        tokensNull = true;
                    } else {
                        tin += ((Number) r.get("input_tokens")).longValue();
                        tout += ((Number) r.get("output_tokens")).longValue();
                    }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("rows", rows.size());
                out.put("maxId", maxId);
                out.put("models", models);
                out.put("tin", tokensNull ? null : tin);
                out.put("tout", tokensNull ? null : tout);
                return out;
            }
        }
        return null;
    }

    private Map<String, Object> stats(String sql, LocalDateTime runStart) {
        return jdbc.queryForMap(sql, EVAL_USER_ID, runStart);
    }

    /**
     * 直接复用服务端静态解析（含软档窄门），评测口径与生产逐字节一致；
     * 允许集合恒含 SOFT_CONCERN——评测需观察软档行为，不受生产开关影响
     *（生产开关只控制提示词是否含软档指引，由渲染侧保证）。
     */
    private Map<String, Object> parse(String raw) {
        return com.zhiyu.modules.admin.service.AdminModerationService.parse(raw,
                java.util.Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN", "SOFT_CONCERN"));
    }
}
