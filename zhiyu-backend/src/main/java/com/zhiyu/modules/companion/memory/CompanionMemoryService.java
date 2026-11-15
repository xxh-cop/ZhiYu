package com.zhiyu.modules.companion.memory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.security.BusinessAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小织记忆 /「小织记得的」（2026-11-15 扩展）。
 *
 * <p>两类数据分开处理：
 * <ul>
 *   <li><b>你告诉我的</b>：三个练习偏好（原有契约不变）+ 回避话题（新增），用户显式填写，
 *       沿用 {@code user_ai_memory} AES 加密存储、180 天过期、可删除，无新表/新列。</li>
 *   <li><b>我注意到的</b>：派生陪伴档案（安定方式、常做手作时段、完成数、低落天数），
 *       <b>不持久化</b>，读取时按 emotion_note / craft_session / craft_session_event 实时计算；
 *       只有计数与时段，没有便签原文、没有情绪标签、没有推断画像。</li>
 * </ul>
 *
 * <p>授权：默认关闭。{@code memory-v2} 起授权范围从「练习偏好」扩大到「陪伴档案」，
 * 旧版本授权行视为未授权，需要用户重新确认。关闭后 {@link #chatProfile} 返回空，对话不注入。
 * 注入最多 {@value #MAX_PROMPT_LINES} 条且不注入原句；低落天数只展示、不注入。
 */
@Slf4j
@Service
public class CompanionMemoryService {

    public static final String KEY_MINUTES = "preferred_minutes";
    public static final String KEY_MATERIAL = "preferred_material";
    public static final String KEY_FAMILY = "preferred_pattern_family";
    public static final String KEY_AVOID_TOPICS = "avoid_topics";
    public static final String CONTROL_KEY = "memory_enabled";
    public static final String CONSENT_VERSION = "memory-v2";
    public static final int TTL_DAYS = 180;
    public static final int MAX_PROMPT_LINES = 3;
    private static final int MAX_AVOID_TOPICS = 3;
    private static final int MAX_TOPIC_LENGTH = 12;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> PREFERENCE_KEYS = List.of(KEY_MINUTES, KEY_MATERIAL, KEY_FAMILY);
    private static final List<String> USER_KEYS = List.of(KEY_MINUTES, KEY_MATERIAL, KEY_FAMILY, KEY_AVOID_TOPICS);
    private static final Set<String> MATERIALS = Set.of("bamboo", "thread", "paper", "porcelain");
    private static final Set<String> FAMILIES = Set.of("stitch", "weave", "cutout", "porcelain");
    private static final Map<String, String> MATERIAL_LABELS = Map.of(
            "bamboo", "竹",
            "thread", "线",
            "paper", "纸",
            "porcelain", "瓷");
    private static final Map<String, String> FAMILY_LABELS = Map.of(
            "stitch", "针线",
            "weave", "编织",
            "cutout", "剪刻",
            "porcelain", "器面");
    private static final Map<String, String> KEY_LABELS = Map.of(
            KEY_MINUTES, "常用练习时长",
            KEY_MATERIAL, "偏好材料",
            KEY_FAMILY, "偏好纹样族");
    private static final String DISCLAIMER =
            "小织记忆默认关闭，只保存你明确选择的练习偏好和不想聊的话题；不保存对话原文、情绪细节或高危信息；半年后自动忘记，可随时删除。";
    public static final String PROFILE_NOTE =
            "「我注意到的」由你的手作和便签记录即时算出，只含次数和时段，不含便签内容；关闭记忆后不再使用。";

    private final JdbcTemplate db;
    private final AesEncryptor crypto;
    private final BusinessAccess access;

    public CompanionMemoryService(JdbcTemplate db, AesEncryptor crypto, BusinessAccess access) {
        this.db = db;
        this.crypto = crypto;
        this.access = access;
    }

    public Map<String, Object> get() {
        long userId = access.consentedUser();
        Map<String, String> rows = loadRows(userId);
        boolean enabled = isEnabled(rows);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String key : PREFERENCE_KEYS) {
            String value = rows.get(key);
            if (value == null || value.isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", KEY_LABELS.get(key));
            item.put("value", value);
            item.put("valueLabel", valueLabel(key, value));
            items.add(item);
        }
        Map<String, Object> result = result(enabled, items);
        result.put("avoidTopics", parseTopicsQuiet(rows.get(KEY_AVOID_TOPICS)));
        result.put("derived", enabled ? derived(userId) : List.of());
        return result;
    }

    @Transactional
    public Map<String, Object> enable(Boolean enabled, Boolean consent) {
        long userId = access.consentedUser();
        boolean next = Boolean.TRUE.equals(enabled);
        if (next && !Boolean.TRUE.equals(consent)) {
            throw new BusinessException(1001, "请确认小织记忆授权");
        }
        upsert(userId, CONTROL_KEY, next ? "true" : "false", null);
        return get();
    }

    @Transactional
    public Map<String, Object> save(Boolean consent, Map<String, String> preferences) {
        long userId = access.consentedUser();
        if (!Boolean.TRUE.equals(consent)) {
            throw new BusinessException(1001, "请确认保存授权");
        }
        if (!isEnabled(loadRows(userId))) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "请先开启小织记忆");
        }
        if (preferences == null || preferences.isEmpty()) {
            throw new BusinessException(1001, "请至少选择一项偏好");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : preferences.entrySet()) {
            String key = entry.getKey();
            if (!USER_KEYS.contains(key)) {
                throw new BusinessException(1001, "不支持的记忆字段");
            }
            normalized.put(key, normalize(key, entry.getValue()));
        }
        LocalDateTime expiresAt = LocalDateTime.now(ZONE).plusDays(TTL_DAYS);
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            upsert(userId, entry.getKey(), entry.getValue(), expiresAt);
        }
        return get();
    }

    /** 删除单条「你告诉我的」；派生档案不持久化，无需删除（关闭记忆即不再使用）。 */
    @Transactional
    public Map<String, Object> deleteItem(String key) {
        long userId = access.consentedUser();
        if (!USER_KEYS.contains(key)) {
            throw new BusinessException(1001, "只能删除你填写的偏好或不想聊的话题");
        }
        db.update("""
                UPDATE user_ai_memory SET deleted=1, update_time=?
                WHERE user_id=? AND memory_key=? AND deleted=0
                """, LocalDateTime.now(ZONE), userId, key);
        return get();
    }

    @Transactional
    public Map<String, Object> deleteAll() {
        long userId = access.consentedUser();
        db.update("""
                UPDATE user_ai_memory SET deleted=1, update_time=?
                WHERE user_id=? AND deleted=0
                """, LocalDateTime.now(ZONE), userId);
        return result(false, List.of());
    }

    /** 供编排器读取；未开启、已过期或没有偏好时返回空 Map。只含三个练习偏好，不含话题。 */
    public Map<String, String> preferences(Long userId) {
        if (userId == null) return Map.of();
        Map<String, String> rows = loadRows(userId);
        if (!isEnabled(rows)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : PREFERENCE_KEYS) {
            String value = rows.get(key);
            if (value != null && !value.isBlank()) result.put(key, value);
        }
        return result;
    }

    public String promptSummary(Long userId) {
        Map<String, String> values = preferences(userId);
        if (values.isEmpty()) return "无";
        List<String> parts = new ArrayList<>();
        values.forEach((key, value) -> parts.add(KEY_LABELS.get(key) + " " + valueLabel(key, value)));
        return "用户授权保存的练习偏好：" + String.join("；", parts)
                + "。仅用于练习推荐，不作情绪或疗效判断。";
    }

    /**
     * 「小织记得的」对话注入：最多 {@value #MAX_PROMPT_LINES} 条、不含原句。
     * 回避话题 > 安定方式 > 练习偏好；低落天数只展示、不注入，避免给用户贴标签。
     */
    public Map<String, Object> chatProfile(long userId) {
        Map<String, String> rows = loadRows(userId);
        if (!isEnabled(rows)) {
            return Map.of("enabled", false, "lines", List.of(), "avoidTopics", List.of());
        }
        List<String> topics = parseTopicsQuiet(rows.get(KEY_AVOID_TOPICS));
        List<String> lines = new ArrayList<>();
        if (!topics.isEmpty()) {
            lines.add("对方明确不想聊：" + String.join("、", topics) + "，不要主动提起。");
        }
        derived(userId).stream().filter(d -> "calm_strategy".equals(d.get("key"))).findFirst()
                .ifPresent(d -> lines.add("对对方有效的安定方式：" + d.get("value") + "（只在对方需要时提一次）。"));
        Map<String, String> prefs = preferences(userId);
        if (lines.size() < MAX_PROMPT_LINES && !prefs.isEmpty()) {
            String practice = prefs.entrySet().stream()
                    .map(e -> KEY_LABELS.get(e.getKey()) + " " + valueLabel(e.getKey(), e.getValue()))
                    .collect(Collectors.joining("；"));
            lines.add("做练习时的偏好：" + practice + "。");
        }
        List<String> limited = lines.size() > MAX_PROMPT_LINES
                ? List.copyOf(lines.subList(0, MAX_PROMPT_LINES)) : lines;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", true);
        result.put("lines", limited);
        result.put("avoidTopics", topics);
        return result;
    }

    /** 派生陪伴档案：实时统计，只有计数与时段；任何一步失败都降级为空，不影响对话与设置页。 */
    public List<Map<String, String>> derived(long userId) {
        List<Map<String, String>> out = new ArrayList<>();
        try {
            Integer completed = count("SELECT COUNT(*) FROM craft_session WHERE user_id=? AND deleted=0 "
                    + "AND status=3 AND end_time>=DATE_SUB(NOW(), INTERVAL 30 DAY)", userId);
            Integer breathing = count("SELECT COUNT(*) FROM craft_session_event WHERE user_id=? AND deleted=0 "
                    + "AND event_type='breathing_responded' AND occurred_at>=DATE_SUB(NOW(), INTERVAL 30 DAY)", userId);
            if (breathing != null && breathing >= 2) {
                out.add(entry("calm_strategy", "有效的安定方式", "先做一次呼吸引导，再决定要不要继续",
                        "近 30 天你跟着呼吸引导放松了 " + breathing + " 次"));
            } else if (completed != null && completed > 0) {
                out.add(entry("calm_strategy", "有效的安定方式", "从一件 10 分钟的小手作开始",
                        "近 30 天你完成了 " + completed + " 件手作"));
            }
            if (completed != null && completed > 0) {
                out.add(entry("completed_crafts", "这一个月完成的手作", completed + " 件", "手作完成记录"));
            }
            Map<String, Object> window = timeWindow(userId);
            if (window != null) {
                out.add(entry("preferred_time_window", "常做手作的时间",
                        String.valueOf(window.get("label")), "近 30 天 " + window.get("total") + " 次手作会话"));
            }
            Integer lowDays = count("SELECT COUNT(DISTINCT note_date) FROM emotion_note WHERE user_id=? AND deleted=0 "
                    + "AND mood IN ('tired','down','有点累','不太好') "
                    + "AND note_date>=DATE_SUB(CURDATE(), INTERVAL 14 DAY)", userId);
            if (lowDays != null && lowDays >= 2) {
                out.add(entry("recent_low_days", "这两周有点累的日子", lowDays + " 天",
                        "你的心情便签，只统计天数，不读内容"));
            }
        } catch (RuntimeException e) {
            log.warn("小织派生档案统计失败，已降级为空");
            return List.of();
        }
        return out;
    }

    private Map<String, Object> timeWindow(long userId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT HOUR(start_time) h, COUNT(*) c FROM craft_session "
                + "WHERE user_id=? AND deleted=0 AND start_time>=DATE_SUB(NOW(), INTERVAL 30 DAY) "
                + "GROUP BY HOUR(start_time)", userId);
        int total = rows.stream().mapToInt(r -> ((Number) r.get("c")).intValue()).sum();
        if (total < 3) {
            return null;
        }
        int bestHour = -1;
        long best = -1;
        for (Map<String, Object> row : rows) {
            long c = ((Number) row.get("c")).longValue();
            if (c > best) {
                best = c;
                bestHour = ((Number) row.get("h")).intValue();
            }
        }
        String label = bestHour < 6 ? "凌晨（0–5 点）" : bestHour < 11 ? "上午（6–10 点）"
                : bestHour < 14 ? "中午（11–13 点）" : bestHour < 18 ? "下午（14–17 点）" : "晚上（18–23 点）";
        return Map.of("label", label, "total", total);
    }

    private Integer count(String sql, long userId) {
        return db.queryForObject(sql, Integer.class, userId);
    }

    private Map<String, String> entry(String key, String label, String value, String source) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("label", label);
        map.put("value", value);
        map.put("source", source);
        return map;
    }

    private Map<String, Object> result(boolean enabled, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("ttlDays", TTL_DAYS);
        result.put("items", items);
        result.put("disclaimer", DISCLAIMER);
        result.put("avoidTopics", List.of());
        result.put("derived", List.of());
        result.put("consentVersion", CONSENT_VERSION);
        result.put("profileNote", PROFILE_NOTE);
        result.put("maxPromptLines", MAX_PROMPT_LINES);
        return result;
    }

    private boolean isEnabled(Map<String, String> rows) {
        return "true".equals(rows.get(CONTROL_KEY))
                && CONSENT_VERSION.equals(rows.get(CONTROL_KEY + ":consent"));
    }

    private Map<String, String> loadRows(long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : db.queryForList("""
                SELECT memory_key, value_cipher, consent_version, expires_at
                FROM user_ai_memory
                WHERE user_id=? AND deleted=0
                """, userId)) {
            Object expires = row.get("expires_at");
            if (expires != null) {
                LocalDateTime expiresAt = expires instanceof java.sql.Timestamp timestamp
                        ? timestamp.toLocalDateTime()
                        : LocalDateTime.parse(expires.toString());
                if (expiresAt.isBefore(now)) continue;
            }
            String key = String.valueOf(row.get("memory_key"));
            if (!CONTROL_KEY.equals(key) && !USER_KEYS.contains(key)) continue;
            if (CONTROL_KEY.equals(key)) {
                values.put(CONTROL_KEY + ":consent", String.valueOf(row.get("consent_version")));
            }
            try {
                values.put(key, crypto.decrypt(String.valueOf(row.get("value_cipher"))));
            } catch (RuntimeException e) {
                log.warn("小织记忆读取失败，已跳过一条记录");
            }
        }
        return values;
    }

    private void upsert(long userId, String key, String value, LocalDateTime expiresAt) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE", Long.class, userId);
        String cipher = crypto.encrypt(value);
        int updated = db.update("""
                UPDATE user_ai_memory SET value_cipher=?, consent_version=?, source=?,
                    expires_at=?, deleted=0, update_time=?
                WHERE user_id=? AND memory_key=?
                """, cipher, CONSENT_VERSION, "manual", expiresAt, now, userId, key);
        if (updated == 0) {
            db.update("""
                    INSERT INTO user_ai_memory(id, user_id, memory_key, value_cipher,
                        consent_version, source, expires_at, create_time, update_time, deleted)
                    VALUES(?,?,?,?,?,?,?,?,?,0)
                    """, IdWorker.getId(), userId, key, cipher, CONSENT_VERSION, "manual",
                    expiresAt, now, now);
        }
    }

    private String normalize(String key, String raw) {
        if (KEY_AVOID_TOPICS.equals(key)) {
            return String.join(",", parseTopics(raw));
        }
        if (raw == null || raw.isBlank()) throw new BusinessException(1001, "偏好值不能为空");
        String value = raw.trim();
        if (KEY_MINUTES.equals(key)) {
            try {
                int minutes = Integer.parseInt(value);
                if (minutes < 1 || minutes > 180) {
                    throw new BusinessException(1001, "练习时长需在 1 到 180 分钟之间");
                }
                return String.valueOf(minutes);
            } catch (NumberFormatException e) {
                throw new BusinessException(1001, "练习时长必须是整数");
            }
        }
        if (KEY_MATERIAL.equals(key)) {
            String code = switch (value) {
                case "竹" -> "bamboo";
                case "线" -> "thread";
                case "纸" -> "paper";
                case "瓷" -> "porcelain";
                default -> value.toLowerCase(Locale.ROOT);
            };
            if (!MATERIALS.contains(code)) throw new BusinessException(1001, "不支持的材料偏好");
            return code;
        }
        String code = switch (value) {
            case "针线" -> "stitch";
            case "编织" -> "weave";
            case "剪刻" -> "cutout";
            case "器面" -> "porcelain";
            default -> value.toLowerCase(Locale.ROOT);
        };
        if (!FAMILIES.contains(code)) throw new BusinessException(1001, "不支持的纹样族偏好");
        return code;
    }

    /** 回避话题：最多 3 个、每个 1–12 字、去重、去空；拒绝换行（防止注入多行 Prompt）。 */
    private List<String> parseTopics(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return List.of();
        }
        Set<String> topics = new LinkedHashSet<>();
        for (String part : raw.split("[,，]")) {
            String topic = part.trim();
            if (topic.isEmpty()) {
                continue;
            }
            if (topic.length() > MAX_TOPIC_LENGTH || topic.contains("\n") || topic.contains("\r")) {
                throw new BusinessException(1001, "每个话题不超过 12 字，且不能换行");
            }
            topics.add(topic);
            if (topics.size() > MAX_AVOID_TOPICS) {
                throw new BusinessException(1001, "最多设置 3 个不想聊的话题");
            }
        }
        return List.copyOf(topics);
    }

    private List<String> parseTopicsQuiet(String raw) {
        try {
            return parseTopics(raw);
        } catch (BusinessException e) {
            log.warn("小织记忆话题格式异常，已忽略");
            return List.of();
        }
    }

    private String valueLabel(String key, String value) {
        if (KEY_MINUTES.equals(key)) return value + " 分钟";
        if (KEY_MATERIAL.equals(key)) return MATERIAL_LABELS.getOrDefault(value, value);
        return FAMILY_LABELS.getOrDefault(value, value);
    }
}
