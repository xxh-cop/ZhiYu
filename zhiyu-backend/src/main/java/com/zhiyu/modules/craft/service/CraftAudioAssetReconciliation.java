package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.file.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 步骤引导音频启动对账（2026-11-05）。
 *
 * <p>仿 {@code SchemaCompatibilityCheck} 思路：启动时逐条核对标为 {@code ready} 的
 * 音频在存储中是否存在，缺失则降级为 {@code missing} 并记 warn（不阻断启动；
 * listSteps 对 missing/disabled 不下发播放入口）。
 *
 * <p>对账范围仅限<b>本地存储</b>（私有媒体引用 {@code /api/media/{id}/content}）：
 * 核 media_asset 行 + 物理对象。外链（http(s)）与前端包静态资源（/static/）不在
 * 后端存储内，不访问外网、不猜前端打包路径；已知 /static/ 死链由
 * 20261105_craft_takedown_state.sql 迁移在数据层清理。
 *
 * <p>整个对账包 try/catch：结构未升级（缺列）时只记 error，启动是否中止交给
 * SchemaCompatibilityCheck 裁决，避免两处重复失败语义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraftAudioAssetReconciliation implements ApplicationRunner {

    private static final Pattern MEDIA_REF = Pattern.compile("/api/media/(\\d+)/content");

    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    @Override
    public void run(ApplicationArguments args) {
        try {
            reconcile();
        } catch (Exception e) {
            log.error("步骤音频资产对账执行失败（不阻断启动，结构问题由 SchemaCompatibilityCheck 裁决）: {}", e.getMessage());
        }
    }

    void reconcile() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, audio_url FROM craft_step"
                        + " WHERE deleted=0 AND asset_status='ready' AND audio_url IS NOT NULL");
        int demoted = 0;
        for (Map<String, Object> row : rows) {
            String ref = String.valueOf(row.get("audio_url"));
            var m = MEDIA_REF.matcher(ref);
            if (!m.matches()) {
                continue; // 外链/前端静态资源：非后端存储，不在对账范围
            }
            if (!mediaRefExists(Long.parseLong(m.group(1)))) {
                int n = jdbc.update(
                        "UPDATE craft_step SET asset_status='missing', update_time=CURRENT_TIMESTAMP"
                                + " WHERE id=? AND asset_status='ready' AND deleted=0",
                        row.get("id"));
                if (n > 0) {
                    demoted++;
                    log.warn("步骤音频标为 ready 但存储中不存在，已降级 missing: stepId={}, ref={}", row.get("id"), ref);
                }
            }
        }
        if (demoted > 0 || !rows.isEmpty()) {
            log.info("步骤音频资产对账完成: ready 共 {} 条，降级 missing {} 条", rows.size(), demoted);
        }
    }

    /** media_asset 行存在（未删除）且物理对象在存储中。 */
    private boolean mediaRefExists(long mediaId) {
        var assets = jdbc.queryForList(
                "SELECT storage_key FROM media_asset WHERE id=? AND deleted=0", mediaId);
        if (assets.isEmpty()) {
            return false;
        }
        return storage.exists(String.valueOf(assets.get(0).get("storage_key")));
    }
}
