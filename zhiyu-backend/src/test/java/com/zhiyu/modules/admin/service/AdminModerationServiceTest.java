package com.zhiyu.modules.admin.service;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.safety.ContentSafetyFilter;
import com.zhiyu.ai.safety.SafetyResult;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.audit.service.AiAuditLogService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BL（2026-11-05）：{@link AdminModerationService} 单测（全 mock，不打真模型）。
 *
 * <p>覆盖：作品/评论建议结构、降级（AI 不可用/格式不可用）、频控、1004、
 * 审计落库携带 refType/refId、输入检测不改写证据、disclaimer 恒在。
 */
class AdminModerationServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiAuditLogService auditLog = mock(AiAuditLogService.class);
    private final ContentSafetyFilter filter = mock(ContentSafetyFilter.class);
    private AdminModerationService service;

    private static final String TPL = "类型:{targetType}|标题:{title}|正文:{content}|媒体:{mediaNote}";

    @BeforeEach
    void setup() {
        UserContext.set(new LoginUser(960001L, 3));
        lenient().when(jdbc.queryForObject(contains("ai_audit_log"), eq(Long.class), any(), any()))
                .thenReturn(0L);
        lenient().when(filter.checkOutput(any(), anyString(), anyString())).thenReturn(SafetyResult.pass("ok"));
        service = new AdminModerationService(jdbc, aiClient, auditLog, filter, TPL);
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private void stubWork() {
        when(jdbc.queryForList(contains("FROM work WHERE"), eq(960100L)))
                .thenReturn(List.of(Map.of("title", "扎染方巾", "message", "第一次尝试扎染",
                        "video_url", "/api/media/1/content")));
    }

    @Test
    @DisplayName("BL：作品建议结构完整（tendency/riskPoints/uncertainty/disclaimer），审计带 ref 落库")
    @SuppressWarnings("unchecked")
    void workSuggestionHappyPath() {
        stubWork();
        when(aiClient.moderationSuggest(anyString())).thenReturn(
                "```json\n{\"tendency\":\"HAS_CONCERNS\",\"riskPoints\":[{\"location\":\"media\",\"note\":\"视频内容需人工核对\"}],\"uncertainty\":\"未看到视频画面\"}\n```");
        Map<String, Object> out = service.suggest("work", 960100L);
        assertEquals(true, out.get("available"));
        assertEquals("HAS_CONCERNS", out.get("tendency"));
        List<Map<String, String>> rps = (List<Map<String, String>>) out.get("riskPoints");
        assertEquals(1, rps.size());
        assertEquals("media", rps.get(0).get("location"));
        assertEquals("视频内容需人工核对", rps.get(0).get("note"));
        assertEquals("未看到视频画面", out.get("uncertainty"));
        assertEquals(AdminModerationService.DISCLAIMER, out.get("disclaimer"));
        // 审计：scene=moderation，action=PASS，refType=work，refId=作品 id
        verify(auditLog).log(eq(960001L), eq("moderation"), anyString(), anyString(),
                eq("PASS"), anyString(), eq("work"), eq(960100L));
        // 输入检测发生过（不改写证据）
        verify(filter).checkOutput(eq(960001L), eq("moderation"), contains("扎染方巾"));
    }

    @Test
    @DisplayName("BL：AI 不可用 → available=false + note + disclaimer 恒在；审计记 FALLBACK")
    void degradeWhenAiUnavailable() {
        stubWork();
        when(aiClient.moderationSuggest(anyString())).thenReturn(null);
        Map<String, Object> out = service.suggest("work", 960100L);
        assertEquals(false, out.get("available"));
        assertEquals("UNCERTAIN", out.get("tendency"));
        assertNotNull(out.get("note"));
        assertEquals(AdminModerationService.DISCLAIMER, out.get("disclaimer"));
        verify(auditLog).log(any(), eq("moderation"), anyString(), isNull(),
                eq("FALLBACK"), anyString(), eq("work"), eq(960100L));
    }

    @Test
    @DisplayName("BL：建议格式不可用（坏 JSON / 非法 tendency）→ 降级不误导")
    void degradeOnUnparsable() {
        stubWork();
        when(aiClient.moderationSuggest(anyString())).thenReturn("这不是 JSON");
        assertEquals(false, service.suggest("work", 960100L).get("available"));
        when(aiClient.moderationSuggest(anyString())).thenReturn("{\"tendency\":\"APPROVE\",\"riskPoints\":[]}");
        assertEquals(false, service.suggest("work", 960100L).get("available"),
                "裁决词倾向值必须视为格式不可用");
    }

    @Test
    @DisplayName("BL：频控——审核员 1 小时 30 次后 → 1001；对象不存在 → 1004；类型非法 → 1001")
    void rateLimitAndNotFound() {
        when(jdbc.queryForObject(contains("ai_audit_log"), eq(Long.class), any(), any())).thenReturn(30L);
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.suggest("work", 960100L)).getCode());

        when(jdbc.queryForObject(contains("ai_audit_log"), eq(Long.class), any(), any())).thenReturn(0L);
        when(jdbc.queryForList(contains("FROM work WHERE"), eq(969999L))).thenReturn(List.of());
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.suggest("work", 969999L)).getCode());
        when(jdbc.queryForList(contains("FROM work_comment WHERE"), eq(969998L))).thenReturn(List.of());
        assertEquals(1004, assertThrows(BusinessException.class,
                () -> service.suggest("comment", 969998L)).getCode());
        assertEquals(1001, assertThrows(BusinessException.class,
                () -> service.suggest("group", 1L)).getCode());
    }

    @Test
    @DisplayName("BL：评论建议正常路径（refType=comment）")
    void commentSuggestionHappyPath() {
        when(jdbc.queryForList(contains("FROM work_comment WHERE"), eq(960200L)))
                .thenReturn(List.of(Map.of("content", "这个作品真好看")));
        when(aiClient.moderationSuggest(anyString())).thenReturn(
                "{\"tendency\":\"NO_OBVIOUS_ISSUE\",\"riskPoints\":[],\"uncertainty\":\"\"}");
        Map<String, Object> out = service.suggest("comment", 960200L);
        assertEquals(true, out.get("available"));
        assertEquals("NO_OBVIOUS_ISSUE", out.get("tendency"));
        verify(auditLog).log(any(), eq("moderation"), anyString(), anyString(),
                eq("PASS"), anyString(), eq("comment"), eq(960200L));
    }
}
