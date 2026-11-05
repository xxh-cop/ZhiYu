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

    private final JdbcTemplate jdbc;
    private final AiClient aiClient;
    private final AiAuditLogService auditLog;
    private final ContentSafetyFilter contentSafetyFilter;
    private final String promptTemplate;
    private final ObjectMapper json = new ObjectMapper();

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

        String prompt = promptTemplate
                .replace("{targetType}", "work".equals(targetType) ? "作品" : "评论")
                .replace("{title}", title)
                .replace("{content}", content)
                .replace("{mediaNote}", mediaNote);

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
        try {
            String text = raw.trim()
                    .replaceAll("(?s)^```(?:json)?\\s*", "")
                    .replaceAll("(?s)\\s*```$", "")
                    .trim();
            var node = json.readTree(text);
            String tendency = node.path("tendency").asText("");
            if (!TENDENCIES.contains(tendency)) {
                return null;
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

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
