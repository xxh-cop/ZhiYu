package com.zhiyu.modules.craft;

import com.zhiyu.modules.craft.service.CraftAudioAssetReconciliation;
import com.zhiyu.modules.file.MediaStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 步骤音频启动对账（2026-11-05）测试。
 *
 * <p>只核对本地存储（私有媒体引用）：media_asset 行或物理对象缺失 → ready 降级 missing。
 * 外链（http(s)）与前端包静态资源（/static/）不在后端存储内，不核对、不改动。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CraftAudioAssetReconciliationTest {

    @Autowired
    private CraftAudioAssetReconciliation reconciliation;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MediaStorage storage;

    private static final long INHERITOR = 950002L;
    private static final long PROJECT = 950100L;
    private static final String PRESENT_KEY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.mp3";
    private static final String ABSENT_KEY = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.mp3";

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR, "rc_inh", "传承人", 1);
        jdbc.update("INSERT INTO craft_project(id,inheritor_id,title,level,category,status,create_time,update_time,deleted)"
                + " VALUES(?,?,?,1,'纸艺',1,NOW(),NOW(),0)", PROJECT, INHERITOR, "对账测试项目");
        // A：ready，media_asset 行在但物理文件缺失 → 应降级 missing
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(950410,?,?,100,NOW(),NOW(),0)", INHERITOR, ABSENT_KEY);
        insertStep(950410L, 1, "/api/media/950410/content", "ready");
        // B：ready，行在且物理文件在 → 保持 ready
        storage.put(PRESENT_KEY, new ByteArrayInputStream("fake-audio".getBytes(StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted)"
                + " VALUES(950411,?,?,100,NOW(),NOW(),0)", INHERITOR, PRESENT_KEY);
        insertStep(950411L, 2, "/api/media/950411/content", "ready");
        // C：ready，/static/ 前端包资源 → 不对账，保持 ready
        insertStep(950412L, 3, "/static/audio/x.mp3", "ready");
        // D：ready，http 外链 → 不对账，保持 ready
        insertStep(950413L, 4, "https://cdn.example.com/a.mp3", "ready");
        // E：未登记（asset_status NULL）→ 不在对账范围
        insertStep(950414L, 5, "/api/media/999999/content", null);
    }

    @AfterEach
    void cleanup() throws Exception {
        storage.remove(PRESENT_KEY);
    }

    private void insertStep(long id, int stepNo, String audioUrl, String assetStatus) {
        jdbc.update("INSERT INTO craft_step(id,project_id,step_no,title,guide,audio_url,license_status,asset_status,"
                        + "create_time,update_time,deleted) VALUES(?,?,?,?,'引导',?,'pending_review',?,NOW(),NOW(),0)",
                id, PROJECT, stepNo, "步骤" + stepNo, audioUrl, assetStatus);
    }

    @Test
    @DisplayName("启动对账：存储缺失的 ready 音频降级 missing；存储完好/外链/静态资源/未登记不动")
    void reconcileDemotesMissingOnly() {
        reconciliation.run(null);

        assertEquals("missing", assetStatus(950410L), "物理文件缺失应降级 missing");
        assertEquals("ready", assetStatus(950411L), "存储完好应保持 ready");
        assertEquals("ready", assetStatus(950412L), "/static/ 不在后端存储内，不改动");
        assertEquals("ready", assetStatus(950413L), "外链不访问外网，不改动");
        assertNull(assetStatus(950414L), "未登记资产不在对账范围");

        // 幂等：再跑一轮不再变化
        reconciliation.run(null);
        assertEquals("missing", assetStatus(950410L));
        assertEquals("ready", assetStatus(950411L));
    }

    private String assetStatus(long stepId) {
        return jdbc.queryForObject("SELECT asset_status FROM craft_step WHERE id=?", String.class, stepId);
    }
}
