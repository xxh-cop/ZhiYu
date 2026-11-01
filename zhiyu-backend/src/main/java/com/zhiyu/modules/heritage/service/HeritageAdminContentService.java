package com.zhiyu.modules.heritage.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.heritage.model.HeritageCatalog;
import com.zhiyu.security.BusinessAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 管理端非遗内容工作流：草稿保存在 runtime_config，学生端只读已发布覆盖；
 * 每次发布追加 heritage_catalog_version 快照，支持历史查看与回滚。
 *
 * <p>草稿与已发布内容分离，避免管理员编辑过程中直接影响学生端；
 * revision 乐观锁用于多管理员并发编辑冲突。
 */
@Service
@RequiredArgsConstructor
public class HeritageAdminContentService {

    private static final String DRAFT_KEY = "heritage_catalog_draft";
    private static final String PUBLISHED_KEY = "heritage_catalog";
    private static final String VERSION_TABLE = "heritage_catalog_version";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final BusinessAccess access;
    private final HeritageCatalogService catalogService;

    public record DraftSnapshot(HeritageCatalog catalog, int revision, boolean persisted) {
    }

    public record PublishedSnapshot(HeritageCatalog catalog, int revision, int version) {
    }

    public record WorkflowSnapshot(DraftSnapshot draft, PublishedSnapshot published, boolean changed) {
    }

    public record VersionSummary(int version, String note, String actorName, String createdAt) {
    }

    public record VersionDetail(int version, String note, String actorName, String createdAt,
                                HeritageCatalog catalog) {
    }

    private record ConfigRow(HeritageCatalog catalog, int revision) {
    }

    /** 兼容入口：学生端当前使用的已发布目录。 */
    public PublishedSnapshot published() {
        access.role(3);
        return publishedView();
    }

    public WorkflowSnapshot workflow() {
        access.role(3);
        return workflowView();
    }

    /** 保存草稿，不改变学生端内容。 */
    @Transactional
    public WorkflowSnapshot saveDraft(int revision, HeritageCatalog catalog) {
        long uid = access.role(3);
        String payload = encode(catalog);
        lockContent();
        var rows = db.queryForList(
                "SELECT revision FROM runtime_config WHERE config_key=? AND deleted=0 FOR UPDATE",
                Integer.class, DRAFT_KEY);
        int current = rows.isEmpty() ? 0 : rows.get(0);
        if (current != revision) {
            throw new BusinessException(3001, "草稿已被其他管理员更新，请重新读取后再编辑");
        }
        int next = revision + 1;
        if (rows.isEmpty()) {
            db.update("INSERT INTO runtime_config(id,config_key,payload,revision) VALUES(?,?,?,?)",
                    IdWorker.getId(), DRAFT_KEY, payload, next);
        } else {
            db.update("UPDATE runtime_config SET payload=?,revision=?,update_time=CURRENT_TIMESTAMP "
                    + "WHERE config_key=? AND deleted=0", payload, next, DRAFT_KEY);
        }
        audit(uid, "HERITAGE_DRAFT_SAVE_v" + next);
        return workflowView();
    }

    @Transactional
    public WorkflowSnapshot discardDraft() {
        long uid = access.role(3);
        lockContent();
        db.update("DELETE FROM runtime_config WHERE config_key=?", DRAFT_KEY);
        audit(uid, "HERITAGE_DRAFT_DISCARD");
        return workflowView();
    }

    /** 发布草稿：学生端立即生效，并追加不可变版本快照。 */
    @Transactional
    public WorkflowSnapshot publish(int revision, String note) {
        long uid = access.role(3);
        String cleanNote = normalizeNote(note);
        lockContent();
        var draft = readConfig(DRAFT_KEY, true);
        if (draft == null) {
            throw new BusinessException(3001, "没有待发布的草稿，请先保存草稿");
        }
        if (draft.revision() != revision) {
            throw new BusinessException(3001, "草稿已被其他管理员更新，请重新读取后再发布");
        }
        String payload = encode(draft.catalog());
        upsertPublished(payload);
        int version = latestVersion() + 1;
        insertVersion(version, payload, cleanNote == null ? "发布 v" + version : cleanNote, uid);
        db.update("DELETE FROM runtime_config WHERE config_key=?", DRAFT_KEY);
        audit(uid, "HERITAGE_CATALOG_PUBLISH_v" + version);
        return workflowView();
    }

    /** 回滚：把历史版本内容作为新的发布版本，不改写旧版本。 */
    @Transactional
    public WorkflowSnapshot rollback(int version, String note) {
        long uid = access.role(3);
        String cleanNote = normalizeNote(note);
        lockContent();
        var source = readVersion(version);
        String payload = encode(source.catalog());
        upsertPublished(payload);
        int nextVersion = latestVersion() + 1;
        insertVersion(nextVersion, payload,
                cleanNote == null ? "回滚到 v" + version : cleanNote, uid);
        db.update("DELETE FROM runtime_config WHERE config_key=?", DRAFT_KEY);
        audit(uid, "HERITAGE_CATALOG_ROLLBACK_v" + version + "_AS_v" + nextVersion);
        return workflowView();
    }

    /** 下架：学生端回到内置目录，历史版本仍保留可追溯。 */
    @Transactional
    public WorkflowSnapshot unpublish(String note) {
        long uid = access.role(3);
        String cleanNote = normalizeNote(note);
        lockContent();
        if (readConfig(PUBLISHED_KEY, true) == null) {
            throw new BusinessException(3001, "当前没有已发布内容可下架");
        }
        db.update("DELETE FROM runtime_config WHERE config_key=?", PUBLISHED_KEY);
        db.update("DELETE FROM runtime_config WHERE config_key=?", DRAFT_KEY);
        String action = cleanNote == null ? "HERITAGE_CATALOG_UNPUBLISH"
                : "HERITAGE_CATALOG_UNPUBLISH_" + truncate(cleanNote, 40);
        audit(uid, action);
        return workflowView();
    }

    /** 恢复内置目录：清空草稿与已发布覆盖，版本历史保留。 */
    @Transactional
    public WorkflowSnapshot reset() {
        long uid = access.role(3);
        lockContent();
        db.update("DELETE FROM runtime_config WHERE config_key IN (?,?)", PUBLISHED_KEY, DRAFT_KEY);
        audit(uid, "HERITAGE_CATALOG_RESET");
        return workflowView();
    }

    public List<VersionSummary> versions() {
        access.role(3);
        return db.query("SELECT v.version_no,v.note,v.create_time,u.nickname "
                        + "FROM " + VERSION_TABLE + " v LEFT JOIN user u ON u.id=v.actor_id "
                        + "WHERE v.deleted=0 ORDER BY v.version_no DESC LIMIT 50",
                (rs, rowNum) -> new VersionSummary(
                        rs.getInt("version_no"),
                        rs.getString("note"),
                        rs.getString("nickname"),
                        format(rs.getTimestamp("create_time"))));
    }

    public VersionDetail versionDetail(int version) {
        access.role(3);
        var source = readVersion(version);
        var summary = db.query("SELECT v.note,v.create_time,u.nickname FROM " + VERSION_TABLE
                        + " v LEFT JOIN user u ON u.id=v.actor_id WHERE v.version_no=? AND v.deleted=0",
                rs -> {
                    if (!rs.next()) throw new BusinessException(1004, "历史版本不存在");
                    return new VersionSummary(version, rs.getString("note"), rs.getString("nickname"),
                            format(rs.getTimestamp("create_time")));
                }, version);
        return new VersionDetail(summary.version(), summary.note(), summary.actorName(),
                summary.createdAt(), source.catalog());
    }

    private WorkflowSnapshot workflowView() {
        var draft = readConfig(DRAFT_KEY, false);
        var published = publishedView();
        var draftView = draft == null
                ? new DraftSnapshot(published.catalog(), 0, false)
                : new DraftSnapshot(draft.catalog(), draft.revision(), true);
        boolean changed = draft != null && !encode(draft.catalog()).equals(encode(published.catalog()));
        return new WorkflowSnapshot(draftView, published, changed);
    }

    private PublishedSnapshot publishedView() {
        var published = readConfig(PUBLISHED_KEY, false);
        if (published == null) {
            return new PublishedSnapshot(catalogService.baseCatalog(), 0, 0);
        }
        return new PublishedSnapshot(published.catalog(), published.revision(), latestVersion());
    }

    private ConfigRow readConfig(String key, boolean lock) {
        String sql = "SELECT payload,revision FROM runtime_config WHERE config_key=? AND deleted=0"
                + (lock ? " FOR UPDATE" : "");
        var rows = db.queryForList(sql, key);
        if (rows.isEmpty()) return null;
        try {
            var catalog = json.readValue(rows.get(0).get("payload").toString(), HeritageCatalog.class);
            HeritageCatalogService.validate(catalog);
            return new ConfigRow(catalog, ((Number) rows.get(0).get("revision")).intValue());
        } catch (Exception e) {
            throw new IllegalStateException("非遗目录配置无法解析，请恢复内置目录后重新发布", e);
        }
    }

    private ConfigRow readVersion(int version) {
        var rows = db.queryForList(
                "SELECT payload FROM " + VERSION_TABLE + " WHERE version_no=? AND deleted=0", version);
        if (rows.isEmpty()) {
            throw new BusinessException(1004, "历史版本不存在");
        }
        try {
            var catalog = json.readValue(rows.get(0).get("payload").toString(), HeritageCatalog.class);
            HeritageCatalogService.validate(catalog);
            return new ConfigRow(catalog, version);
        } catch (Exception e) {
            throw new BusinessException(1001, "历史版本内容无法解析");
        }
    }

    private void upsertPublished(String payload) {
        var rows = db.queryForList(
                "SELECT revision FROM runtime_config WHERE config_key=? AND deleted=0 FOR UPDATE",
                Integer.class, PUBLISHED_KEY);
        int current = rows.isEmpty() ? 0 : rows.get(0);
        if (rows.isEmpty()) {
            db.update("INSERT INTO runtime_config(id,config_key,payload,revision) VALUES(?,?,?,?)",
                    IdWorker.getId(), PUBLISHED_KEY, payload, 1);
        } else {
            db.update("UPDATE runtime_config SET payload=?,revision=?,update_time=CURRENT_TIMESTAMP "
                    + "WHERE config_key=? AND deleted=0", payload, current + 1, PUBLISHED_KEY);
        }
    }

    private int latestVersion() {
        Integer latest = db.queryForObject(
                "SELECT MAX(version_no) FROM " + VERSION_TABLE + " WHERE deleted=0", Integer.class);
        return latest == null ? 0 : latest;
    }

    private void insertVersion(int version, String payload, String note, long uid) {
        db.update("INSERT INTO " + VERSION_TABLE
                        + "(id,version_no,payload,note,actor_id,create_time,update_time,deleted) "
                        + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                IdWorker.getId(), version, payload, note, uid);
    }

    private String encode(HeritageCatalog catalog) {
        try {
            HeritageCatalogService.validate(catalog);
            return json.writeValueAsString(catalog);
        } catch (IllegalStateException e) {
            throw new BusinessException(1001, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(1001, "非遗目录无法编码");
        }
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) return null;
        String trimmed = note.trim();
        if (trimmed.length() > 200) {
            throw new BusinessException(1001, "发布说明不能超过 200 字");
        }
        return trimmed;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String format(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(TIME_FORMAT);
    }

    /** 以首个管理员行作为内容写锁，避免草稿/发布并发交叉。 */
    private void lockContent() {
        db.queryForObject(
                "SELECT id FROM user WHERE role=3 AND deleted=0 ORDER BY id LIMIT 1 FOR UPDATE", Long.class);
    }

    private void audit(long uid, String action) {
        db.update("INSERT INTO admin_operation_log(id,actor_id,action,target_id,create_time,update_time,deleted) "
                + "VALUES(?,?,?,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", IdWorker.getId(), uid, action);
    }
}
