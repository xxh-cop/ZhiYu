package com.zhiyu.ai.safety;

import com.zhiyu.modules.audit.service.AiAuditLogService;
import com.zhiyu.modules.audit.service.CareReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 基于关键词 + 简单规则的内容安全过滤器（T1.3b）。
 *
 * <p>输入侧：命中 {@link HighRiskLexicon} → {@link SafetyAction#BLOCK}，
 * 写 care_reminder + ai_audit_log，返回关怀话术。
 *
 * <p>输出侧：命中越界词（"你患有"、"抑郁症"、"焦虑症"、"诊断"、"病情"等）→
 * {@link SafetyAction#REWRITE}，改写为安全话术，写 ai_audit_log。
 *
 * <p>决策（T1.3b 已确认）：
 * <ul>
 *   <li>不做 LLM 语义判断，只做本地词库 + 正则</li>
 *   <li>流式对话由 {@code AiClient.streamChat} 在调用 LLM 前调 {@code checkInput}，
 *       并在完整回复结束后调 {@code checkOutput} 做整段改写（token 无法逐字改写）；
 *       同步接口在返回前调用 {@code checkOutput}</li>
 *   <li>每次检查写一条带 userId 的 ai_audit_log；空输入 → PASS，不写审计</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordContentSafetyFilter implements ContentSafetyFilter {

    private final HighRiskLexicon highRiskLexicon;
    private final CareReminderService careReminderService;
    private final AiAuditLogService aiAuditLogService;

    /**
     * 输出侧越界词正则数组。
     * 命中即改写为安全话术，避免诊断性结论（产品定位红线）。
     */
    private static final Pattern[] BOUNDARY_PATTERNS = {
            Pattern.compile("你患有"),
            Pattern.compile("抑郁症"),
            Pattern.compile("焦虑症"),
            Pattern.compile("诊断"),
            Pattern.compile("病情"),
            Pattern.compile("病症"),
            Pattern.compile("确诊"),
            Pattern.compile("心理疾病")
    };

    /** 输出越界时的安全替换话术。 */
    private static final String SAFE_REWRITE =
            "我在这里陪你。如果愿意，可以跟我聊聊你现在的感受。";

    @Value("${zhiyu.crisis.psych-center-phone:0791-88120129}")
    private String psychCenterPhone;

    @Value("${zhiyu.crisis.national-hotline:12356}")
    private String nationalHotline;

    @Override
    public SafetyResult checkInput(Long userId, Long sessionId, String scene, String text) {
        // 1. 空输入 → PASS，不写审计
        if (text == null || text.isBlank()) {
            return SafetyResult.pass("空输入");
        }

        // 2. 高危词库命中 → BLOCK
        if (highRiskLexicon.containsRisk(text)) {
            String matchedKeyword = highRiskLexicon.firstMatch(text);
            String reason = "关键词命中: " + matchedKeyword;
            String careMessage = buildCareMessage();

            // 写 care_reminder（脱敏 summary，不加密）
            String summary = buildSummary(sessionId, matchedKeyword);
            careReminderService.trigger(userId, sessionId, 2, summary);

            // 写 ai_audit_log（raw_input 加密）
            aiAuditLogService.log(userId, scene, text, null, "BLOCK", reason);

            log.warn("输入侧高危命中: userId={}, sessionId={}, scene={}, keyword={}, action=BLOCK",
                    userId, sessionId, scene, matchedKeyword);
            return SafetyResult.block(reason, careMessage);
        }

        // 3. 普通 → PASS + 写审计
        aiAuditLogService.log(userId, scene, text, null, "PASS", "输入检查通过");
        return SafetyResult.pass("输入检查通过");
    }

    @Override
    public SafetyResult checkOutput(Long userId, String scene, String text) {
        // 1. 空输出 → PASS，不写审计
        if (text == null || text.isBlank()) {
            return SafetyResult.pass("空输出");
        }

        // 2. 越界词检测
        for (Pattern p : BOUNDARY_PATTERNS) {
            if (p.matcher(text).find()) {
                String reason = "输出越界词命中: " + p.pattern();
                aiAuditLogService.log(userId, scene, null, text, "REWRITE", reason);
                log.warn("输出侧越界命中: userId={}, scene={}, pattern={}, action=REWRITE",
                        userId, scene, p.pattern());
                return SafetyResult.rewrite(reason, SAFE_REWRITE);
            }
        }

        // 3. 正常 → PASS + 写审计
        aiAuditLogService.log(userId, scene, null, text, "PASS", "输出检查通过");
        return SafetyResult.pass("输出检查通过");
    }

    /**
     * 构造关怀话术（含热线号码）。
     */
    private String buildCareMessage() {
        return "我听到你现在的感受，你并不孤单。如果需要，可以拨打"
                + "学校心理中心：" + psychCenterPhone
                + "，或全国心理援助热线：" + nationalHotline
                + "。我会一直在这里陪你。";
    }

    /**
     * 构造脱敏 summary（不含原始对话原文）。
     * 格式：「在会话 #{sessionId} 中表达了高危信号：{命中关键词}」
     * sessionId 为 null 时：「在情绪对话中表达了高危信号：{命中关键词}」
     */
    private String buildSummary(Long sessionId, String keyword) {
        if (sessionId != null) {
            return "在会话 #" + sessionId + " 中表达了高危信号：" + keyword;
        }
        return "在情绪对话中表达了高危信号：" + keyword;
    }
}
