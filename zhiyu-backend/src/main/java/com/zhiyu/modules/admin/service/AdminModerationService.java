package com.zhiyu.modules.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.audit.service.AiAuditLogService;
import com.zhiyu.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核辅助建议（BL，2026-11-05）。
 *
 * <p><b>产品边界（红线）</b>：AI 只给建议，绝不处置——本服务不写任何审核状态字段、
 * 不触发审核动作；建议仅在响应中返回给审核员。按需调用（审核员显式点击），
 * 不做自动/批量预跑。
 *
 * <p>媒体诚实原则：当前无视觉/OCR 能力，含媒体对象只在 riskPoints 提示
 * 「需人工核对」，绝不把模型编造的媒体描述当依据。
 *
 * <p>频控：按审核员 30 次/小时（计数源直接复用 ai_audit_log，零新增基础设施；
 * 防连点烧钱，且审核本身以人工节奏为主）。
 *
 * <p>降级：AI 不可用/超时/熔断/输出未过安全过滤/格式不可用 → 200 + code=0 +
 * {@code available=false} + 明确 note，不报错挡住人工审核，也不让审核员误以为
 * 「AI 没说话就是没问题」。
 */
@Slf4j
@Service
public class AdminModerationService {

    /** 随响应下发的固定免责声明（前端必须展示）。 */
    public static final String DISCLAIMER = "AI 辅助建议，仅供人工参考，不构成结论";

    /** 审核员维度频控：每小时上限（计数源为 ai_audit_log）。 */
    private static final int HOURLY_LIMIT = 30;

    private static final Set<String> TENDENCIES = Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN");

    /** 误杀软档值（2026-11-05 批次）：仅开关开启时出现在契约里。 */
    static final String SOFT_CONCERN = "SOFT_CONCERN";

    /** 提示词中软档指引块的标记（块文本整体在 moderation-suggest.st 内，Java 只负责按开关裁切）。 */
    private static final java.util.regex.Pattern SOFT_TIER_BLOCK =
            java.util.regex.Pattern.compile("(?s)\\s*\\{\\{SOFT_TIER_BEGIN\\}\\}.*?\\{\\{SOFT_TIER_END\\}\\}");
    private static final String SOFT_BEGIN = "{{SOFT_TIER_BEGIN}}";
    private static final String SOFT_END = "{{SOFT_TIER_END}}";
    private static final String ENUM_BASE = "NO_OBVIOUS_ISSUE 或 HAS_CONCERNS 或 UNCERTAIN";
    private static final String ENUM_SOFT = "NO_OBVIOUS_ISSUE 或 SOFT_CONCERN 或 HAS_CONCERNS 或 UNCERTAIN";
    /** 软档开启时输出契约中的 narrative 字段片段（单选枚举；关闭时整体不出现）。 */
    private static final String NARRATIVE_FIELD =
            "\"narrative\":\"self_disclosure 或 risk_action 或 other_directed 或 none\",";
    /** 窄门唯一放行的 narrative 枚举值（本人体验自述且无风险指代、无指向他人）。 */
    static final String NARRATIVE_SELF_DISCLOSURE = "self_disclosure";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 误杀软档开关（zhiyu.moderation.soft-tier.enabled，默认 false）。
     * 关闭 = 三档行为：提示词不含软档指引，解析器拒绝 SOFT_CONCERN（按格式不可用降级）。
     */
    @org.springframework.beans.factory.annotation.Value("${zhiyu.moderation.soft-tier.enabled:false}")
    private boolean softTierEnabled;

    private final JdbcTemplate jdbc;
    private final AiClient aiClient;
    private final AiAuditLogService auditLog;
    private final ContentSafetyFilter contentSafetyFilter;
    private final String promptTemplate;

    public AdminModerationService(JdbcTemplate jdbc, AiClient aiClient, AiAuditLogService auditLog,
                                  ContentSafetyFilter contentSafetyFilter,
                                  @Qualifier("moderationSuggestPrompt") String promptTemplate) {
        this.jdbc = jdbc;
        this.aiClient = aiClient;
        this.auditLog = auditLog;
        this.contentSafetyFilter = contentSafetyFilter;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 对作品或评论生成审核辅助建议。
     *
     * @param targetType work / comment
     * @param id         对象 ID
     */
    public Map<String, Object> suggest(String targetType, long id) {
        Long actor = UserContext.getUserId();
        rateLimit(actor);

        String title;
        String content;
        String mediaNote;
        if ("work".equals(targetType)) {
            var rows = jdbc.queryForList(
                    "SELECT title,message,video_url,cover_url FROM work WHERE id=? AND deleted=0", id);
            if (rows.isEmpty()) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
            }
            title = str(rows.get(0).get("title"));
            content = str(rows.get(0).get("message"));
            boolean hasMedia = rows.get(0).get("video_url") != null || rows.get(0).get("cover_url") != null;
            mediaNote = hasMedia ? "含图片或视频（AI 不可见，只能提示人工核对）" : "无";
        } else if ("comment".equals(targetType)) {
            var rows = jdbc.queryForList(
                    "SELECT content FROM work_comment WHERE id=? AND deleted=0", id);
            if (rows.isEmpty()) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
            }
            title = "（评论，无标题）";
            content = str(rows.get(0).get("content"));
            mediaNote = "无";
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的对象类型");
        }

        // 输入检测（只检测 + 审计，绝不改写证据原文——审核必须看到真实内容）
        contentSafetyFilter.checkOutput(actor, "moderation", title + "\n" + content);

        String prompt = render(promptTemplate, softTierEnabled,
                "work".equals(targetType) ? "作品" : "评论", title, content, mediaNote);

        String raw = aiClient.moderationSuggest(prompt);
        if (raw == null) {
            auditLog.log(actor, "moderation", prompt, null, "FALLBACK", "AI 不可用或输出未过安全过滤", targetType, id);
            return unavailable("AI 暂时无法给出建议（未配置模型/熔断中/超时），请按人工流程审核");
        }
        Map<String, Object> parsed = parse(raw);
        if (parsed == null) {
            auditLog.log(actor, "moderation", prompt, raw, "FALLBACK", "建议格式不可用", targetType, id);
            return unavailable("AI 建议格式不可用，请按人工流程审核");
        }
        auditLog.log(actor, "moderation", prompt, raw, "PASS", "审核辅助建议", targetType, id);
        parsed.put("available", true);
        parsed.put("disclaimer", DISCLAIMER);
        return parsed;
    }

    /** 审核员维度频控：超限 → 1001（明确报错，不伪装成「暂无建议」）。 */
    private void rateLimit(Long actor) {
        Long recent = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_audit_log WHERE user_id=? AND scene='moderation' AND create_time>=?",
                Long.class, actor, LocalDateTime.now().minusHours(1));
        if (recent != null && recent >= HOURLY_LIMIT) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "AI 建议调用较频繁，请稍后再试（每小时限 " + HOURLY_LIMIT + " 次）");
        }
    }

    private Map<String, Object> unavailable(String note) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", false);
        out.put("tendency", "UNCERTAIN");
        out.put("riskPoints", List.of());
        out.put("uncertainty", "");
        out.put("note", note);
        out.put("disclaimer", DISCLAIMER);
        return out;
    }

    /** 解析模型 JSON（容忍 markdown 围栏）；结构不合法返回 null。 */
    private Map<String, Object> parse(String raw) {
        return parse(raw, allowedTendencies());
    }

    /** 当前允许的 tendency 集合：软档开关开启时含 {@link #SOFT_CONCERN}。 */
    Set<String> allowedTendencies() {
        return softTierEnabled
                ? Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN", SOFT_CONCERN)
                : TENDENCIES;
    }

    /**
     * 渲染审核提示词（public 静态：供评测 harness 在不同包复用，保证评测与生产逐字节一致）。
     * 软档关闭时整体裁掉 {{SOFT_TIER_BEGIN}}..{{SOFT_TIER_END}} 标记块（含前导空行），
     * 并把输出契约的 tendency 枚举替换为当前生效值列表。
     */
    public static String render(String template, boolean softTierEnabled,
                                String targetTypeText, String title, String content, String mediaNote) {
        String t = softTierEnabled
                ? template.replace(SOFT_BEGIN, "").replace(SOFT_END, "")
                        .replace("{tendencyEnum}", ENUM_SOFT)
                        .replace("{narrativeField}", NARRATIVE_FIELD)
                : SOFT_TIER_BLOCK.matcher(template).replaceAll("")
                        .replace("{tendencyEnum}", ENUM_BASE)
                        .replace("{narrativeField}", "");
        return t.replace("{targetType}", targetTypeText)
                .replace("{title}", title)
                .replace("{content}", content)
                .replace("{mediaNote}", mediaNote);
    }

    /**
     * 解析模型 JSON（public 静态：供评测 harness 复用同一口径，容忍 markdown 围栏）；
     * tendency 不在允许集合或结构不合法返回 null；软档值额外过窄门（见 {@link #applySoftGate}）。
     */
    public static Map<String, Object> parse(String raw, Set<String> allowedTendencies) {
        try {
            String text = raw.trim()
                    .replaceAll("(?s)^```(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*```$", "")
                    .trim();
            var node = JSON.readTree(text);
            String tendency = node.path("tendency").asText("");
            if (!allowedTendencies.contains(tendency)) {
                return null;
            }
            // 软档窄门（2026-11-05 窄档批）：tendency=SOFT_CONCERN 且开关开启时，
            // 必须 narrative 三键齐全且 personal=true、riskAction=false、otherDirected=false，
            // 否则保守降为 UNCERTAIN（缺字段/结构不符同样降级，不静默归软档）。
            if (SOFT_CONCERN.equals(tendency) && allowedTendencies.contains(SOFT_CONCERN)) {
                tendency = applySoftGate(node.path("narrative"));
            }
            List<Map<String, String>> riskPoints = new ArrayList<>();
            for (var rp : node.path("riskPoints")) {
                String location = rp.path("location").asText("");
                String note = rp.path("note").asText("");
                if (note.isBlank()) {
                    return null;
                }
                riskPoints.add(Map.of("location", location.isBlank() ? "content" : location, "note", note));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tendency", tendency);
            out.put("riskPoints", riskPoints);
            out.put("uncertainty", node.path("uncertainty").asText(""));
            out.put("note", null);
            return out;
        } catch (Exception e) {
            log.warn("审核建议 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 软档窄门判定（枚举版）：narrative 为文本且恰好取 {@link #NARRATIVE_SELF_DISCLOSURE}
     * 才保留 SOFT_CONCERN；缺失、非文本、未知枚举值或取其他值一律降为 UNCERTAIN。
     */
    static String applySoftGate(com.fasterxml.jackson.databind.JsonNode narrative) {
        if (narrative == null || !narrative.isTextual()) {
            return "UNCERTAIN";
        }
        return NARRATIVE_SELF_DISCLOSURE.equals(narrative.asText()) ? SOFT_CONCERN : "UNCERTAIN";
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
