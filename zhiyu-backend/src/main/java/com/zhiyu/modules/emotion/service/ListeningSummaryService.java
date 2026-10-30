package com.zhiyu.modules.emotion.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.security.BusinessAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 独立倾听小结：只读本人已结束会话中的用户发言，生成与保存分离。 */
@Service
public class ListeningSummaryService {

    public static final String KIND = "LISTENING_SUMMARY";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SAFETY_HOLD =
            "这次对话里出现了需要优先照顾自己的信号。AI 小结不会覆盖这段经历；如果你愿意，可以在支持页找到学校心理中心与全国热线。本系统不能替代专业心理咨询。";
    private static final String FALLBACK =
            "这次倾听已经结束。AI 小结暂时没有生成，你仍可以在下方写下自己此刻想留下的一句话。";
    private static final String TOO_SHORT =
            "这段对话还比较短，可以只留下一句此刻的感受。";

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AesEncryptor crypto;
    private final AiClient aiClient;
    private final EmotionService emotionService;
    private final BusinessAccess access;
    private final Resource promptResource;

    public ListeningSummaryService(JdbcTemplate db, ObjectMapper json, AesEncryptor crypto,
                                   AiClient aiClient, EmotionService emotionService,
                                   BusinessAccess access,
                                   @Value("classpath:prompts/listening-summary.st") Resource promptResource) {
        this.db = db;
        this.json = json;
        this.crypto = crypto;
        this.aiClient = aiClient;
        this.emotionService = emotionService;
        this.access = access;
        this.promptResource = promptResource;
    }

    public Map<String, Object> generate(long sessionId, boolean consent) {
        long uid = access.consentedUser();
        if (!consent) throw new BusinessException(1001, "请确认 AI 生成授权");
        requireEnded(uid, sessionId);
        if (hasCareReminder(uid, sessionId)) return result(SAFETY_HOLD, false, true);

        List<String> userTexts = new ArrayList<>();
        for (ChatMessage message : emotionService.loadRecentHistory(sessionId, 40)) {
            if (!"user".equals(message.role())) continue;
            String text = message.content() == null ? "" : message.content().trim();
            if (!text.isBlank()) userTexts.add(text.length() > 300 ? text.substring(0, 300) : text);
            if (userTexts.size() >= 12) break;
        }
        String transcript = userTexts.stream()
                .map(text -> "用户：" + text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (userTexts.size() < 2 || transcript.length() < 20) return result(TOO_SHORT, false, false);
        if (transcript.length() > 3000) transcript = transcript.substring(0, 3000);

        try {
            String prompt = promptResource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{turns}}", String.valueOf(userTexts.size()))
                    .replace("{{transcript}}", transcript);
            AiClient.ListeningResult generated = aiClient.listeningSummary(sessionId, prompt, transcript);
            if (generated.blocked()) return result(SAFETY_HOLD, false, true);
            if (!generated.generated() || generated.text() == null || generated.text().isBlank()) {
                return result(FALLBACK, false, false);
            }
            String summary = generated.text().trim();
            return result(summary.length() > 300 ? summary.substring(0, 300) : summary, true, false);
        } catch (Exception e) {
            return result(FALLBACK, false, false);
        }
    }

    @Transactional
    public Map<String, Object> save(long sessionId, String summary, boolean generated, boolean consent) {
        long uid = access.consentedUser();
        if (!consent) throw new BusinessException(1001, "请确认保存授权");
        requireEnded(uid, sessionId);
        if (hasCareReminder(uid, sessionId)) throw new BusinessException(3001, "这段对话暂不生成或保存小结");
        String text = summary == null ? "" : summary.trim();
        if (text.isBlank()) throw new BusinessException(1001, "小结不能为空");
        if (text.length() > 300) throw new BusinessException(1001, "小结不得超过 300 字");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", String.valueOf(sessionId));
        payload.put("summary", text);
        payload.put("generated", generated);
        payload.put("savedAt", LocalDateTime.now(ZONE).toString());
        String cipher = encode(payload);
        LocalDateTime now = LocalDateTime.now(ZONE);
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE", Long.class, uid);
        int updated = db.update("""
                UPDATE wellbeing_record SET payload=?,deleted=0,update_time=?
                WHERE user_id=? AND kind=? AND record_key=?
                """, cipher, now, uid, KIND, String.valueOf(sessionId));
        if (updated == 0) {
            db.update("""
                    INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted)
                    VALUES(?,?,?,?,?,?,?,0)
                    """, IdWorker.getId(), uid, KIND, String.valueOf(sessionId), cipher, now, now);
        }
        return get(sessionId);
    }

    public Map<String, Object> get(long sessionId) {
        long uid = access.user();
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT payload,update_time FROM wellbeing_record
                WHERE user_id=? AND kind=? AND record_key=? AND deleted=0
                """, uid, KIND, String.valueOf(sessionId));
        if (rows.isEmpty()) return Map.of("saved", false);
        Map<String, Object> payload = decode(rows.get(0).get("payload").toString());
        payload.put("saved", true);
        payload.put("savedAt", rows.get(0).get("update_time").toString());
        return payload;
    }

    public void delete(long sessionId) {
        long uid = access.user();
        db.update("""
                UPDATE wellbeing_record SET deleted=1,update_time=?
                WHERE user_id=? AND kind=? AND record_key=? AND deleted=0
                """, LocalDateTime.now(ZONE), uid, KIND, String.valueOf(sessionId));
    }

    private void requireEnded(long uid, long sessionId) {
        access.emotionSession(sessionId, uid);
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT status FROM emotion_session WHERE id=? AND user_id=? AND deleted=0", sessionId, uid);
        if (rows.isEmpty() || number(rows.get(0).get("status")) != 1) {
            throw new BusinessException(3001, "请先结束这次倾听");
        }
    }

    private boolean hasCareReminder(long uid, long sessionId) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT COUNT(*) AS count FROM care_reminder WHERE user_id=? AND session_id=? AND deleted=0",
                uid, sessionId);
        return !rows.isEmpty() && number(rows.get(0).get("count")) > 0;
    }

    private Map<String, Object> result(String summary, boolean generated, boolean safetyHold) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("generated", generated);
        result.put("safetyHold", safetyHold);
        return result;
    }

    private String encode(Map<String, Object> payload) {
        try {
            return crypto.encrypt(json.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("小结加密失败", e);
        }
    }

    private Map<String, Object> decode(String cipher) {
        try {
            return json.readValue(crypto.decrypt(cipher), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException(5000, "小结暂时无法读取");
        }
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
