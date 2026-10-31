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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 小织偏好记忆：默认关闭，只保存三个白名单偏好，加密存储、180 天 TTL、可查看可删除。
 * 不保存原始对话、情绪细节或高危信息。
 */
@Slf4j
@Service
public class CompanionMemoryService {

    public static final String KEY_MINUTES = "preferred_minutes";
    public static final String KEY_MATERIAL = "preferred_material";
    public static final String KEY_FAMILY = "preferred_pattern_family";
    public static final String CONTROL_KEY = "memory_enabled";
    public static final String CONSENT_VERSION = "memory-v1";
    public static final int TTL_DAYS = 180;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> PREFERENCE_KEYS = List.of(KEY_MINUTES, KEY_MATERIAL, KEY_FAMILY);
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
            "小织记忆默认关闭，只保存你明确选择的练习偏好；不保存对话原文、情绪细节或高危信息；180 天后自动失效，可随时删除。";

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
        boolean enabled = "true".equals(rows.get(CONTROL_KEY));
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
        return result(enabled, items);
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
        if (!"true".equals(loadRows(userId).get(CONTROL_KEY))) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "请先开启小织记忆");
        }
        if (preferences == null || preferences.isEmpty()) {
            throw new BusinessException(1001, "请至少选择一项偏好");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : preferences.entrySet()) {
            String key = entry.getKey();
            if (!PREFERENCE_KEYS.contains(key)) {
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

    @Transactional
    public Map<String, Object> deleteAll() {
        long userId = access.consentedUser();
        db.update("""
                UPDATE user_ai_memory SET deleted=1, update_time=?
                WHERE user_id=? AND deleted=0
                """, LocalDateTime.now(ZONE), userId);
        return result(false, List.of());
    }

    /** 供编排器读取；未开启、已过期或没有偏好时返回空 Map。 */
    public Map<String, String> preferences(Long userId) {
        if (userId == null) return Map.of();
        Map<String, String> rows = loadRows(userId);
        if (!"true".equals(rows.get(CONTROL_KEY))) return Map.of();
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

    private Map<String, Object> result(boolean enabled, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("ttlDays", TTL_DAYS);
        result.put("items", items);
        result.put("disclaimer", DISCLAIMER);
        return result;
    }

    private Map<String, String> loadRows(long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : db.queryForList("""
                SELECT memory_key, value_cipher, expires_at
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
            if (!CONTROL_KEY.equals(key) && !PREFERENCE_KEYS.contains(key)) continue;
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

    private String valueLabel(String key, String value) {
        if (KEY_MINUTES.equals(key)) return value + " 分钟";
        if (KEY_MATERIAL.equals(key)) return MATERIAL_LABELS.getOrDefault(value, value);
        return FAMILY_LABELS.getOrDefault(value, value);
    }
}
