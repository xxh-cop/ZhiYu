package com.zhiyu.modules.practice.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.modules.heritage.service.HeritageCatalogService;
import com.zhiyu.modules.practice.dto.SavePracticeArtifactDTO;
import com.zhiyu.modules.practice.vo.PracticeSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 练习成果：把文化卡、色板与用户备注加密写入既有 wellbeing_record。
 * 生成与保存分离；只有 COMPLETED/ARTIFACT_SAVED 会话可以保存，删除不清会话。
 */
@Slf4j
@Service
public class PracticeArtifactService {

    public static final String KIND = "PRACTICE_ARTIFACT";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Map<String, String> FAMILY_LABELS = Map.of(
            "stitch", "针线",
            "weave", "编织",
            "cutout", "剪刻",
            "porcelain", "器面");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AesEncryptor crypto;
    private final PracticeSessionService sessions;
    private final HeritageCatalogService catalog;

    public PracticeArtifactService(JdbcTemplate db, ObjectMapper json, AesEncryptor crypto,
                                   PracticeSessionService sessions, HeritageCatalogService catalog) {
        this.db = db;
        this.json = json;
        this.crypto = crypto;
        this.sessions = sessions;
        this.catalog = catalog;
    }

    @Transactional
    public Map<String, Object> save(Long userId, Long sessionId, SavePracticeArtifactDTO request) {
        PracticeSessionVO session = sessions.get(userId, sessionId);
        if (request == null || !Boolean.TRUE.equals(request.getConsent())) {
            throw new BusinessException(1001, "请确认保存授权");
        }
        int status = session.getStatus() == null ? -1 : session.getStatus();
        if (status != 5 && status != 6) {
            throw new BusinessException(3001, "请完成练习后再保存成果");
        }
        HeritageCatalog.Pattern pattern = pattern(session.getHeritagePatternId());
        HeritageCatalog.Tradition tradition = tradition(pattern.traditionId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("practiceSessionId", String.valueOf(sessionId));
        payload.put("patternId", pattern.id());
        payload.put("patternName", pattern.name());
        payload.put("traditionName", tradition.name());
        payload.put("region", tradition.region());
        payload.put("family", pattern.family());
        payload.put("familyLabel", FAMILY_LABELS.getOrDefault(pattern.family(), pattern.family()));
        payload.put("level", pattern.level());
        payload.put("minutesBudget", session.getMinutesBudget());
        payload.put("structureLabel", structureLabel(request.getStructureLabel(), pattern));
        payload.put("palette", palette(request.getPalette(), pattern.palette()));
        payload.put("learningFocus", pattern.learningFocus());
        payload.put("designNote", pattern.designNote());
        payload.put("sources", sources(pattern.sourceIds()));
        payload.put("notes", notes(request.getNotes()));
        payload.put("listeningSummary", listeningSummary(userId, session.getEmotionSessionId()));
        payload.put("disclaimer", "原创教学再设计，不是传统原作；本系统不能替代专业心理咨询。");

        LocalDateTime now = LocalDateTime.now(ZONE);
        upsert(userId, sessionId, payload, now);
        if (status == 5) sessions.transition(userId, sessionId, "SAVE_ARTIFACT");
        payload.put("saved", true);
        payload.put("savedAt", now.toString());
        return payload;
    }

    public Map<String, Object> get(Long userId, Long sessionId) {
        sessions.get(userId, sessionId);
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT payload, update_time FROM wellbeing_record
                WHERE user_id=? AND kind=? AND record_key=? AND deleted=0
                """, userId, KIND, String.valueOf(sessionId));
        if (rows.isEmpty()) return Map.of("saved", false);
        Map<String, Object> payload = decode(rows.get(0).get("payload").toString());
        payload.put("saved", true);
        payload.put("savedAt", rows.get(0).get("update_time").toString());
        return payload;
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        sessions.get(userId, sessionId);
        db.update("""
                UPDATE wellbeing_record SET deleted=1, update_time=?
                WHERE user_id=? AND kind=? AND record_key=? AND deleted=0
                """, LocalDateTime.now(ZONE), userId, KIND, String.valueOf(sessionId));
    }

    private void upsert(Long userId, Long sessionId, Map<String, Object> payload, LocalDateTime now) {
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE", Long.class, userId);
        String cipher = encode(payload);
        int updated = db.update("""
                UPDATE wellbeing_record SET payload=?, deleted=0, update_time=?
                WHERE user_id=? AND kind=? AND record_key=?
                """, cipher, now, userId, KIND, String.valueOf(sessionId));
        if (updated == 0) {
            db.update("""
                    INSERT INTO wellbeing_record(id, user_id, kind, record_key, payload, create_time, update_time, deleted)
                    VALUES(?,?,?,?,?,?,?,0)
                    """, IdWorker.getId(), userId, KIND, String.valueOf(sessionId), cipher, now, now);
        }
    }

    private HeritageCatalog.Pattern pattern(String patternId) {
        if (patternId == null || patternId.isBlank()) {
            throw new BusinessException(1004, "这次练习没有关联纹样");
        }
        return catalog.catalog().patterns().stream()
                .filter(item -> item.id().equals(patternId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(1004, "纹样目录不存在"));
    }

    private HeritageCatalog.Tradition tradition(String traditionId) {
        return catalog.catalog().traditions().stream()
                .filter(item -> item.id().equals(traditionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(1004, "工艺目录不存在"));
    }

    private List<Map<String, Object>> sources(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (HeritageCatalog.Source source : catalog.catalog().sources()) {
            if (!sourceIds.contains(source.id())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", source.id());
            item.put("title", source.title());
            item.put("organization", source.organization());
            item.put("url", source.url());
            item.put("mediaReuse", source.mediaReuse());
            result.add(item);
            if (result.size() >= 4) break;
        }
        return result;
    }

    private List<String> palette(List<String> requested, List<String> fallback) {
        List<String> values = requested == null || requested.isEmpty() ? fallback : requested;
        if (values == null || values.size() != 4) {
            throw new BusinessException(1001, "色板需要 4 个十六进制颜色");
        }
        List<String> result = new ArrayList<>(4);
        for (String value : values) {
            if (value == null || !HEX_COLOR.matcher(value).matches()) {
                throw new BusinessException(1001, "色板颜色格式不正确");
            }
            result.add(value.toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private String structureLabel(String value, HeritageCatalog.Pattern pattern) {
        String text = trimToNull(value, 100);
        if (text != null) return text;
        return FAMILY_LABELS.getOrDefault(pattern.family(), pattern.family())
                + " · " + pattern.name() + " · 疏密 " + pattern.detail();
    }

    private String notes(String value) {
        return trimToNull(value, 500) == null ? "" : value.trim();
    }

    private String listeningSummary(Long userId, Long emotionSessionId) {
        if (emotionSessionId == null) return null;
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT payload FROM wellbeing_record
                WHERE user_id=? AND kind='LISTENING_SUMMARY' AND record_key=? AND deleted=0
                """, userId, String.valueOf(emotionSessionId));
        if (rows.isEmpty()) return null;
        try {
            String summary = String.valueOf(decode(rows.get(0).get("payload").toString())
                    .getOrDefault("summary", "")).trim();
            if (summary.isBlank()) return null;
            return summary.length() > 300 ? summary.substring(0, 300) : summary;
        } catch (RuntimeException e) {
            log.warn("倾听小结读取失败，练习成果不包含小结");
            return null;
        }
    }

    private String trimToNull(String value, int max) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isBlank()) return null;
        if (text.length() > max) throw new BusinessException(1001, "字段长度超过限制");
        return text;
    }

    private String encode(Object value) {
        try {
            return crypto.encrypt(json.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException("练习成果加密失败", e);
        }
    }

    private Map<String, Object> decode(String value) {
        try {
            return json.readValue(crypto.decrypt(value), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("练习成果读取失败", e);
        }
    }
}
