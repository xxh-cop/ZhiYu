package com.zhiyu.modules.growth;

import com.zhiyu.ai.AiClient;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 创作回顾（20261028）。
 *
 * <p>只使用用户自填的作品名、项目名、步骤数、前后感受、情绪词与备注；
 * 不读取情绪对话原文。AI 不可用、输出越界或过长时使用固定模板。
 */
@Slf4j
@Service
public class ReflectionSummaryService {

    private static final List<String> MOODS = List.of("很低落", "有些低落", "平静", "还不错", "很开心");

    private final JdbcTemplate db;
    private final AiClient aiClient;
    private final Resource promptResource;

    public ReflectionSummaryService(
            JdbcTemplate db,
            AiClient aiClient,
            @Value("classpath:prompts/reflection.st") Resource promptResource) {
        this.db = db;
        this.aiClient = aiClient;
        this.promptResource = promptResource;
    }

    public Map<String, Object> generate(GrowthRequests.ReflectionSummary req) {
        long userId = consentedUser();
        if (!req.consent()) {
            throw new BusinessException(1001, "请确认 AI 生成授权");
        }

        Map<String, Object> session = one("""
                SELECT c.status,c.current_step,p.title,
                       (SELECT COUNT(*) FROM craft_step s
                        WHERE s.project_id=c.project_id AND s.deleted=0) AS step_count
                FROM craft_session c
                JOIN craft_project p ON p.id=c.project_id AND p.deleted=0
                WHERE c.id=? AND c.user_id=? AND c.deleted=0
                """, req.sessionId(), userId);
        if (number(session.get("status")) != 3) {
            throw new BusinessException(3001, "请完成创作后再生成回顾");
        }

        String projectTitle = Objects.toString(session.get("title"), "手作");
        int steps = Math.max(1, (int) Math.min(999, number(session.get("step_count"))));
        String fallback = fallback(projectTitle, steps);
        try {
            String prompt = promptResource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{projectTitle}}", projectTitle)
                    .replace("{{steps}}", String.valueOf(steps))
                    .replace("{{title}}", safe(req.title()))
                    .replace("{{beforeMood}}", moodLabel(req.beforeMood()))
                    .replace("{{afterMood}}", moodLabel(req.afterMood()))
                    .replace("{{emotion}}", safe(req.emotion()))
                    .replace("{{note}}", safe(req.note()));
            String userText = String.join("\n", safe(req.title()), safe(req.emotion()), safe(req.note()));
            String generated = aiClient.reflectionSummary(prompt, userText);
            if (generated == null || generated.isBlank() || generated.length() > 300) {
                return result(fallback, false);
            }
            return result(generated.trim(), true);
        } catch (Exception e) {
            log.warn("创作回顾生成失败，使用固定模板: {}", e.getMessage());
            return result(fallback, false);
        }
    }

    private Map<String, Object> result(String summary, boolean generated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("generated", generated);
        return result;
    }

    private long consentedUser() {
        Long id = UserContext.getUserId();
        if (id == null) {
            throw new BusinessException(1002, "请先登录");
        }
        Map<String, Object> user = one(
                "SELECT status,privacy_agreed FROM user WHERE id=? AND deleted=0", id);
        if (number(user.get("status")) != 1) {
            throw new BusinessException(1003, "账号不可用");
        }
        if (number(user.get("privacy_agreed")) != 1) {
            throw new BusinessException(1001, "请先同意隐私政策");
        }
        return id;
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = db.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new BusinessException(1004, "记录不存在");
        }
        return rows.get(0);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String moodLabel(Integer mood) {
        return mood == null || mood < 1 || mood > MOODS.size() ? "未记录" : MOODS.get(mood - 1);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }

    private String fallback(String projectTitle, int steps) {
        return "你完成了《" + projectTitle + "》的 " + steps
                + " 个步骤，并记录下创作前后的感受。愿意为自己留出这段时间，本身就值得被看见。";
    }
}
