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

/**
 * 管理端非遗目录维护：整份目录作为受校验 JSON 存入 runtime_config。
 *
 * <p>这是 4 门工艺/24 份纹样规模下的最小维护方案：不新增表，复用运行时配置的
 * revision 乐观锁；学生端读取覆盖后立即生效。后续内容规模扩大再迁移到关系表。
 */
@Service
@RequiredArgsConstructor
public class HeritageAdminContentService {

    private static final String CONFIG_KEY = "heritage_catalog";

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final BusinessAccess access;
    private final HeritageCatalogService catalogService;

    public record Snapshot(HeritageCatalog catalog, int revision) {
    }

    /** 管理员读取当前目录与 revision；没有覆盖时返回内置目录、revision=0。 */
    public Snapshot snapshot() {
        access.role(3);
        var rows = db.queryForList(
                "SELECT payload, revision FROM runtime_config WHERE config_key=? AND deleted=0", CONFIG_KEY);
        if (rows.isEmpty()) return new Snapshot(catalogService.baseCatalog(), 0);
        try {
            var catalog = json.readValue(rows.get(0).get("payload").toString(), HeritageCatalog.class);
            HeritageCatalogService.validate(catalog);
            return new Snapshot(catalog, ((Number) rows.get(0).get("revision")).intValue());
        } catch (Exception e) {
            throw new IllegalStateException("非遗目录覆盖无法解析，请在管理端恢复内置目录", e);
        }
    }

    /** 保存整份目录；revision 冲突返回 3001，校验失败返回 1001。 */
    @Transactional
    public Snapshot save(int revision, HeritageCatalog catalog) {
        long uid = access.role(3);
        try {
            HeritageCatalogService.validate(catalog);
        } catch (IllegalStateException e) {
            throw new BusinessException(1001, e.getMessage());
        }
        String payload;
        try {
            payload = json.writeValueAsString(catalog);
        } catch (Exception e) {
            throw new BusinessException(1001, "非遗目录无法编码");
        }
        db.queryForObject(
                "SELECT id FROM user WHERE role=3 AND deleted=0 ORDER BY id LIMIT 1 FOR UPDATE", Long.class);
        var rows = db.queryForList(
                "SELECT revision FROM runtime_config WHERE config_key=? FOR UPDATE", Integer.class, CONFIG_KEY);
        int current = rows.isEmpty() ? 0 : rows.get(0);
        if (current != revision) throw new BusinessException(3001, "目录已由其他管理员更新，请重新读取后修改");
        int next = revision + 1;
        if (rows.isEmpty()) {
            db.update("INSERT INTO runtime_config(id,config_key,payload,revision) VALUES(?,?,?,?)",
                    IdWorker.getId(), CONFIG_KEY, payload, next);
        } else {
            db.update("UPDATE runtime_config SET payload=?,revision=?,update_time=CURRENT_TIMESTAMP "
                    + "WHERE config_key=? AND deleted=0", payload, next, CONFIG_KEY);
        }
        audit(uid, "HERITAGE_CATALOG_UPDATE_v" + next);
        return new Snapshot(catalog, next);
    }

    /** 删除覆盖，恢复内置目录。 */
    @Transactional
    public Snapshot reset() {
        long uid = access.role(3);
        db.queryForObject(
                "SELECT id FROM user WHERE role=3 AND deleted=0 ORDER BY id LIMIT 1 FOR UPDATE", Long.class);
        db.update("DELETE FROM runtime_config WHERE config_key=?", CONFIG_KEY);
        audit(uid, "HERITAGE_CATALOG_RESET");
        return new Snapshot(catalogService.baseCatalog(), 0);
    }

    private void audit(long uid, String action) {
        db.update("INSERT INTO admin_operation_log(id,actor_id,action,target_id,create_time,update_time,deleted) "
                + "VALUES(?,?,?,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", IdWorker.getId(), uid, action);
    }
}
