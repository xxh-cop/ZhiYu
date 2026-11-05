package com.zhiyu.modules.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审核软档（SOFT_CONCERN）开关语义单测（2026-11-05 批次）。
 * 不启 Spring、不调模型：只验证提示词渲染裁切与解析器放行/拒绝。
 * 回滚语义：开关关闭时提示词与解析器行为必须与三档时代完全一致。
 */
class AdminModerationSoftTierTest {

    private static final String TEMPLATE = """
            # 铁律
            - 纪律甲
            {{SOFT_TIER_BEGIN}}
            # 倾向判定指引（何时落 SOFT_CONCERN）
            - 指引乙
            {{SOFT_TIER_END}}
            # 输出
            {"tendency":"{tendencyEnum}",{narrativeField}"riskPoints":[]}
            对象类型：{targetType}
            标题：{title}
            正文/内容：{content}
            媒体情况：{mediaNote}
            """;

    @Test
    @DisplayName("开关关闭：软档指引块整体裁掉，tendency 枚举回三档，narrative 字段不出现，占位符正常渲染")
    void render_softTierDisabled_stripsBlockAndUsesBaseEnum() {
        String out = AdminModerationService.render(TEMPLATE, false, "评论", "（评论，无标题）", "正文", "无");
        assertThat(out).doesNotContain("SOFT_CONCERN", "{{SOFT_TIER_BEGIN}}", "{{SOFT_TIER_END}}", "{tendencyEnum}", "{narrativeField}", "self_disclosure");
        assertThat(out).contains("NO_OBVIOUS_ISSUE 或 HAS_CONCERNS 或 UNCERTAIN");
        assertThat(out).contains("纪律甲").doesNotContain("指引乙");
        assertThat(out).contains("对象类型：评论", "正文/内容：正文");
    }

    @Test
    @DisplayName("开关开启：指引块保留（标记裁掉），tendency 枚举含 SOFT_CONCERN，narrative 枚举片段出现")
    void render_softTierEnabled_keepsGuideAndSoftEnum() {
        String out = AdminModerationService.render(TEMPLATE, true, "作品", "标题", "正文", "无");
        assertThat(out).contains("指引乙", "NO_OBVIOUS_ISSUE 或 SOFT_CONCERN 或 HAS_CONCERNS 或 UNCERTAIN", "self_disclosure 或 risk_action 或 other_directed 或 none");
        assertThat(out).doesNotContain("{{SOFT_TIER_BEGIN}}", "{{SOFT_TIER_END}}", "{tendencyEnum}", "{narrativeField}");
    }

    @Test
    @DisplayName("回滚语义：三档集合拒绝 SOFT_CONCERN（按格式不可用降级）；软档集合下无 narrative 降 UNCERTAIN")
    void parse_softConcernRejectedWhenDisabled_demotedWithoutNarrativeWhenEnabled() {
        String raw = "{\"tendency\":\"SOFT_CONCERN\",\"riskPoints\":[],\"uncertainty\":\"u\"}";
        assertThat(AdminModerationService.parse(raw,
                Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN"))).isNull();
        Map<String, Object> ok = AdminModerationService.parse(raw,
                Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN", "SOFT_CONCERN"));
        assertThat(ok).isNotNull();
        // 窄档语义：缺 narrative 不静默归软档，保守降为 UNCERTAIN
        assertThat(ok.get("tendency")).isEqualTo("UNCERTAIN");
    }

    @Test
    @DisplayName("解析保持既有口径：markdown 围栏容忍、空 note 拒绝、未知值拒绝")
    void parse_existingSemanticsUnchanged() {
        Set<String> base = Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN");
        assertThat(AdminModerationService.parse(
                "```json\n{\"tendency\":\"HAS_CONCERNS\",\"riskPoints\":[{\"location\":\"content\",\"note\":\"n\"}]}\n```",
                base)).isNotNull();
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"HAS_CONCERNS\",\"riskPoints\":[{\"note\":\"\"}]}", base)).isNull();
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"MAYBE\",\"riskPoints\":[]}", base)).isNull();
    }

    private static final Set<String> SOFT_SET =
            Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN", "SOFT_CONCERN");

    @Test
    @DisplayName("窄门（枚举）：narrative=self_disclosure 时保留软档")
    void softGate_selfDisclosure_keepsSoftConcern() {
        Map<String, Object> ok = AdminModerationService.parse(
                "{\"tendency\":\"SOFT_CONCERN\",\"narrative\":\"self_disclosure\",\"riskPoints\":[]}",
                SOFT_SET);
        assertThat(ok).isNotNull();
        assertThat(ok.get("tendency")).isEqualTo("SOFT_CONCERN");
    }

    @Test
    @DisplayName("窄门（枚举）：risk_action / other_directed / none 一律降为 UNCERTAIN")
    void softGate_otherEnumValues_demoteToUncertain() {
        for (String v : new String[]{"risk_action", "other_directed", "none"}) {
            assertThat(AdminModerationService.parse(
                    "{\"tendency\":\"SOFT_CONCERN\",\"narrative\":\"" + v + "\",\"riskPoints\":[]}",
                    SOFT_SET).get("tendency")).isEqualTo("UNCERTAIN");
        }
    }

    @Test
    @DisplayName("窄门（枚举）：narrative 缺失/非文本/未知枚举值，一律降为 UNCERTAIN（不静默归软档）")
    void softGate_missingOrUnknownNarrative_demotesToUncertain() {
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"SOFT_CONCERN\",\"riskPoints\":[]}", SOFT_SET).get("tendency"))
                .isEqualTo("UNCERTAIN");
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"SOFT_CONCERN\",\"narrative\":true,\"riskPoints\":[]}", SOFT_SET).get("tendency"))
                .isEqualTo("UNCERTAIN");
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"SOFT_CONCERN\",\"narrative\":\"looks_safe\",\"riskPoints\":[]}", SOFT_SET)
                .get("tendency")).isEqualTo("UNCERTAIN");
    }

    @Test
    @DisplayName("窄门不影响其他档：HAS_CONCERNS/UNCERTAIN/NO_OBVIOUS_ISSUE 不读 narrative")
    void softGate_otherTendenciesUnaffected() {
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"HAS_CONCERNS\",\"narrative\":\"risk_action\",\"riskPoints\":[{\"note\":\"n\"}]}",
                SOFT_SET).get("tendency")).isEqualTo("HAS_CONCERNS");
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"UNCERTAIN\",\"riskPoints\":[]}", SOFT_SET).get("tendency"))
                .isEqualTo("UNCERTAIN");
    }

    @Test
    @DisplayName("回滚语义加强：开关关闭时 SOFT_CONCERN 即使带合法 narrative 也被拒绝")
    void softGate_disabled_rejectsSoftConcernEvenWithValidNarrative() {
        assertThat(AdminModerationService.parse(
                "{\"tendency\":\"SOFT_CONCERN\",\"narrative\":\"self_disclosure\",\"riskPoints\":[]}",
                Set.of("NO_OBVIOUS_ISSUE", "HAS_CONCERNS", "UNCERTAIN"))).isNull();
    }
}
