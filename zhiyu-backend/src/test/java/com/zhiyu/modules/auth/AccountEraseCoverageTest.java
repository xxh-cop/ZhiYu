package com.zhiyu.modules.auth;

import com.zhiyu.modules.auth.service.AccountLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O1 注销清理全表覆盖测试（2026-11-04）。
 *
 * <p>逐表断言：注销后用户数据 0 残留——不仅 deleted=1，
 * 加密列与内容列（value_cipher/context_cipher/mood/reason/skill/title/content）
 * 必须被真正清空；并反向断言其他用户数据不受影响。
 * 盘点结论见交付报告「全表盘点」一节。
 */
class AccountEraseCoverageTest {

    private static final long UID = 888001L;
    private static final long VICTIM = 888002L;
    private static final long WORK_UID = 888010L;
    private static final long WORK_VICTIM = 888011L;
    private static final long GROUP_ID = 888020L;

    private JdbcTemplate db;
    private AccountLifecycle lifecycle;
    private TransactionTemplate tx;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:erase" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
                "sa", "");
        try (var c = source.getConnection()) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource("src/test/resources/schema-h2.sql"));
        }
        db = new JdbcTemplate(source);
        lifecycle = new AccountLifecycle(db);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));

        for (long id : new long[]{UID, VICTIM}) {
            db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted) "
                    + "VALUES(?,?,'hash','用户',0,1,1,0)", id, "erase" + id);
        }
        db.update("INSERT INTO support_group(id,name,description,max_count,member_count,status,deleted) "
                + "VALUES(?,' erase组','',10,2,1,0)", GROUP_ID);
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) "
                + "VALUES(?,?,'本人作品','text',1,0,0,1,0)", WORK_UID, UID);
        db.update("INSERT INTO work(id,user_id,title,media_type,visible,anonymous,like_count,status,deleted) "
                + "VALUES(?,?,'他人作品','text',1,0,0,1,0)", WORK_VICTIM, VICTIM);

        // 新补齐的表（含加密/内容列）
        db.update("INSERT INTO user_ai_memory(id,user_id,memory_key,value_cipher,consent_version,source,create_time,update_time,deleted) "
                + "VALUES(900001,?,'duration','密文偏好','v1','CHAT',NOW(),NOW(),0)", UID);
        db.update("INSERT INTO user_ai_memory(id,user_id,memory_key,value_cipher,consent_version,source,create_time,update_time,deleted) "
                + "VALUES(900002,?,'duration','他人密文','v1','CHAT',NOW(),NOW(),0)", VICTIM);
        db.update("INSERT INTO practice_session(id,user_id,kind,status,minutes_budget,current_step,context_cipher,mood_code,create_time,update_time,deleted) "
                + "VALUES(900010,?,'HERITAGE',3,15,1,'密文上下文','anxious',NOW(),NOW(),0)", UID);
        db.update("INSERT INTO practice_session(id,user_id,kind,status,minutes_budget,current_step,context_cipher,mood_code,create_time,update_time,deleted) "
                + "VALUES(900011,?,'HERITAGE',3,15,1,'他人密文','calm',NOW(),NOW(),0)", VICTIM);
        db.update("INSERT INTO notification(id,user_id,type,title,content,dedupe_key,read_flag,create_time,update_time,deleted) "
                + "VALUES(900020,?,'WORK_AUDIT','作品审核已通过','你的作品已通过','w:1:1',0,NOW(),NOW(),0)", UID);
        db.update("INSERT INTO notification(id,user_id,type,title,content,dedupe_key,read_flag,create_time,update_time,deleted) "
                + "VALUES(900021,?,'WORK_AUDIT','他人通知','内容','w:2:1',0,NOW(),NOW(),0)", VICTIM);
        db.update("INSERT INTO craft_recommend_event(id,user_id,recommendation_id,project_id,event_type,source,create_time,update_time,deleted) "
                + "VALUES(900030,?,1,1,'CLICK','HOME',NOW(),NOW(),0)", UID);
        db.update("INSERT INTO craft_recommend_event(id,user_id,recommendation_id,project_id,event_type,source,create_time,update_time,deleted) "
                + "VALUES(900031,?,1,1,'CLICK','HOME',NOW(),NOW(),0)", VICTIM);
        db.update("INSERT INTO group_work(id,group_id,work_id,create_time,update_time,deleted) VALUES(900040,?,?,NOW(),NOW(),0)", GROUP_ID, WORK_UID);
        db.update("INSERT INTO group_work(id,group_id,work_id,create_time,update_time,deleted) VALUES(900041,?,?,NOW(),NOW(),0)", GROUP_ID, WORK_VICTIM);
        db.update("INSERT INTO craft_recommendation(id,user_id,session_id,mood,reason,create_time,update_time,deleted) "
                + "VALUES(900050,?,NULL,'anxious','静心推荐',NOW(),NOW(),0)", UID);
        db.update("INSERT INTO craft_recommendation(id,user_id,session_id,mood,reason,create_time,update_time,deleted) "
                + "VALUES(900051,?,NULL,'calm','他人推荐',NOW(),NOW(),0)", VICTIM);
        db.update("INSERT INTO craft_recommendation_item(id,recommendation_id,project_id,score,reason,create_time,update_time,deleted) "
                + "VALUES(900052,900050,1,0.9,'单项理由',NOW(),NOW(),0)");
        db.update("INSERT INTO craft_recommendation_item(id,recommendation_id,project_id,score,reason,create_time,update_time,deleted) "
                + "VALUES(900053,900051,1,0.8,'他人明细',NOW(),NOW(),0)");
        db.update("INSERT INTO inheritor_profile(id,user_id,skill,intro,honor,verified,create_time,update_time,deleted) "
                + "VALUES(900060,?,'竹编','个人简介','市级荣誉',1,NOW(),NOW(),0)", UID);
        db.update("INSERT INTO inheritor_profile(id,user_id,skill,intro,honor,verified,create_time,update_time,deleted) "
                + "VALUES(900061,?,'剪纸','他人简介','荣誉',1,NOW(),NOW(),0)", VICTIM);
        // P1b 滚动摘要表（密文列必须真正清空）
        db.update("INSERT INTO dialogue_context_summary(id,user_id,session_id,covered_start_turn,covered_end_turn,summary_cipher,model,prompt_version,create_time,update_time,deleted) "
                + "VALUES(900062,?,900070,1,5,'摘要密文','deepseek-chat','v1',NOW(),NOW(),0)", UID);
        db.update("INSERT INTO dialogue_context_summary(id,user_id,session_id,covered_start_turn,covered_end_turn,summary_cipher,model,prompt_version,create_time,update_time,deleted) "
                + "VALUES(900063,?,900073,1,5,'他人摘要密文','deepseek-chat','v1',NOW(),NOW(),0)", VICTIM);

        // 旧覆盖表抽查（emotion 消息链）
        db.update("INSERT INTO emotion_session(id,user_id,mood,summary,status,create_time,update_time,deleted) "
                + "VALUES(900070,?,'anxious','会话小结',0,NOW(),NOW(),0)", UID);
        db.update("INSERT INTO emotion_message(id,session_id,role,content,create_time,update_time,deleted) "
                + "VALUES(900071,900070,'user','我心里的话',NOW(),NOW(),0)");
        db.update("INSERT INTO emotion_note(id,user_id,content,mood,create_time,update_time,deleted) "
                + "VALUES(900072,?,'便签内容','anxious',NOW(),NOW(),0)", UID);

        // AH1（2026-11-04）回归：campus_verification 必须有行才触发 NOT NULL 约束
        // （旧测试没插行，UPDATE 命中 0 行，review_reason=NULL 的缺陷因此漏出到生产）
        db.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,"
                + "review_reason,reviewer_id,create_time,update_time,deleted) "
                + "VALUES(900080,?,'测试学校','测试学院','证件密文','REJECTED','理由：照片不清晰',900003,NOW(),NOW(),0)", UID);
        db.update("INSERT INTO campus_verification(id,user_id,school,college,identity_cipher,status,"
                + "review_reason,reviewer_id,create_time,update_time,deleted) "
                + "VALUES(900081,?,'他人学校','他人学院','他人证件密文','APPROVED','他人理由',900003,NOW(),NOW(),0)", VICTIM);

        // AJ（2026-11-04）危机留存表：清密文列 + 逻辑删除
        db.update("INSERT INTO crisis_session_log(id,user_id,session_id,scene,risk_level,summary_cipher,"
                + "create_time,update_time,deleted) VALUES(900090,?,42,'chat',2,'摘要密文',NOW(),NOW(),0)", UID);
        db.update("INSERT INTO crisis_session_log(id,user_id,session_id,scene,risk_level,summary_cipher,"
                + "create_time,update_time,deleted) VALUES(900091,?,43,'chat',2,'他人摘要密文',NOW(),NOW(),0)", VICTIM);
    }

    private int deletedCount(String table, String where, Object... args) {
        return db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class, args);
    }

    @Test
    @DisplayName("注销后逐表 0 残留（含加密列真正清空），且不误删其他用户数据")
    void eraseCoversAllTables() {
        tx.executeWithoutResult(s -> lifecycle.erase(UID));

        // 新补齐表：deleted=1 且加密/内容列清空
        var memory = db.queryForMap("SELECT value_cipher,deleted FROM user_ai_memory WHERE id=900001");
        assertEquals(1, ((Number) memory.get("deleted")).intValue(), "user_ai_memory 应逻辑删除");
        assertEquals("", memory.get("value_cipher"), "value_cipher 必须真正清空，不只是置 deleted");

        var practice = db.queryForMap("SELECT context_cipher,mood_code,deleted FROM practice_session WHERE id=900010");
        assertEquals(1, ((Number) practice.get("deleted")).intValue());
        assertNull(practice.get("context_cipher"), "context_cipher 必须清空");
        assertNull(practice.get("mood_code"), "敏感情绪标签 mood_code 必须清空");

        var notice = db.queryForMap("SELECT title,content,deleted FROM notification WHERE id=900020");
        assertEquals(1, ((Number) notice.get("deleted")).intValue());
        assertEquals("", notice.get("title"), "通知标题应清空");
        assertNull(notice.get("content"), "通知正文应清空");

        assertEquals(1, deletedCount("craft_recommend_event", "id=900030 AND deleted=1"));
        assertEquals(1, deletedCount("group_work", "id=900040 AND deleted=1"));

        var rec = db.queryForMap("SELECT mood,reason,deleted FROM craft_recommendation WHERE id=900050");
        assertEquals(1, ((Number) rec.get("deleted")).intValue());
        assertNull(rec.get("mood"), "推荐记录的情绪标签必须清空");
        assertNull(rec.get("reason"), "推荐理由必须清空");
        assertEquals(1, deletedCount("craft_recommendation_item", "id=900052 AND deleted=1"),
                "推荐明细须随主单级联逻辑删除");

        var profile = db.queryForMap("SELECT skill,intro,honor,deleted FROM inheritor_profile WHERE id=900060");
        assertEquals(1, ((Number) profile.get("deleted")).intValue());
        assertEquals("", profile.get("skill"));
        assertNull(profile.get("intro"));
        assertNull(profile.get("honor"));

        var dcs = db.queryForMap("SELECT summary_cipher,deleted FROM dialogue_context_summary WHERE id=900062");
        assertEquals(1, ((Number) dcs.get("deleted")).intValue(), "滚动摘要应逻辑删除");
        assertEquals("", dcs.get("summary_cipher"), "summary_cipher 必须真正清空，不只是置 deleted");

        // 旧覆盖表抽查：情绪链与作品
        var msg = db.queryForMap("SELECT content,deleted FROM emotion_message WHERE id=900071");
        assertEquals("", msg.get("content"));
        assertEquals(1, ((Number) msg.get("deleted")).intValue());
        var session = db.queryForMap("SELECT summary,mood,deleted FROM emotion_session WHERE id=900070");
        assertNull(session.get("summary"));
        assertNull(session.get("mood"));
        var workRow = db.queryForMap("SELECT title,deleted FROM work WHERE id=?", WORK_UID);
        assertEquals("已移除作品", workRow.get("title"));
        assertEquals(1, ((Number) workRow.get("deleted")).intValue());

        // ── 反向断言：其他用户数据不受影响 ──
        var victimMemory = db.queryForMap("SELECT value_cipher,deleted FROM user_ai_memory WHERE id=900002");
        assertEquals("他人密文", victimMemory.get("value_cipher"), "不得清空其他用户的加密偏好");
        assertEquals(0, ((Number) victimMemory.get("deleted")).intValue());
        var victimPractice = db.queryForMap("SELECT context_cipher,deleted FROM practice_session WHERE id=900011");
        assertEquals("他人密文", victimPractice.get("context_cipher"));
        assertEquals(0, ((Number) victimPractice.get("deleted")).intValue());
        var victimNotice = db.queryForMap("SELECT content,deleted FROM notification WHERE id=900021");
        assertEquals("内容", victimNotice.get("content"));
        assertEquals(0, ((Number) victimNotice.get("deleted")).intValue());
        assertEquals(1, deletedCount("craft_recommend_event", "id=900031 AND deleted=0"));
        assertEquals(1, deletedCount("group_work", "id=900041 AND deleted=0"),
                "他人作品的小组关联不得删除");
        var victimRec = db.queryForMap("SELECT mood,reason,deleted FROM craft_recommendation WHERE id=900051");
        assertEquals("calm", victimRec.get("mood"));
        assertEquals(0, ((Number) victimRec.get("deleted")).intValue());
        assertEquals(1, deletedCount("craft_recommendation_item", "id=900053 AND deleted=0"),
                "他人推荐明细不得级联删除");
        var victimProfile = db.queryForMap("SELECT skill,intro,deleted FROM inheritor_profile WHERE id=900061");
        assertEquals("剪纸", victimProfile.get("skill"));
        assertEquals("他人简介", victimProfile.get("intro"));
        var victimDcs = db.queryForMap("SELECT summary_cipher,deleted FROM dialogue_context_summary WHERE id=900063");
        assertEquals("他人摘要密文", victimDcs.get("summary_cipher"), "不得清空其他用户的摘要密文");
        assertEquals(0, ((Number) victimDcs.get("deleted")).intValue());
        var victimWork = db.queryForMap("SELECT title,deleted FROM work WHERE id=?", WORK_VICTIM);
        assertEquals("他人作品", victimWork.get("title"));
        assertEquals(0, ((Number) victimWork.get("deleted")).intValue());

        // AH1 回归：有核验记录的账号删除不再 500；证件密文与审核理由均清空（空串，NOT NULL 约束）
        var cv = db.queryForMap("SELECT identity_cipher,review_reason,deleted FROM campus_verification WHERE id=900080");
        assertEquals(1, ((Number) cv.get("deleted")).intValue(), "核验记录应逻辑删除");
        assertEquals("", cv.get("identity_cipher"), "证件密文必须真正清空");
        assertEquals("", cv.get("review_reason"), "审核理由必须清空（NOT NULL 列置空串而非 NULL）");
        var victimCv = db.queryForMap("SELECT identity_cipher,review_reason,deleted FROM campus_verification WHERE id=900081");
        assertEquals("他人证件密文", victimCv.get("identity_cipher"), "不得清空他人的核验记录");
        assertEquals("他人理由", victimCv.get("review_reason"));
        assertEquals(0, ((Number) victimCv.get("deleted")).intValue());

        // AJ：危机留存行清密文 + 逻辑删除；他人不受影响
        var cl = db.queryForMap("SELECT summary_cipher,deleted FROM crisis_session_log WHERE id=900090");
        assertEquals(1, ((Number) cl.get("deleted")).intValue(), "危机留存应逻辑删除");
        assertEquals("", cl.get("summary_cipher"), "危机摘要密文必须真正清空");
        var victimCl = db.queryForMap("SELECT summary_cipher,deleted FROM crisis_session_log WHERE id=900091");
        assertEquals("他人摘要密文", victimCl.get("summary_cipher"));
        assertEquals(0, ((Number) victimCl.get("deleted")).intValue());
    }

    /**
     * AH2（2026-11-04）防静默漏表守卫：以 schema-h2.sql 全表为基准，机器化比对
     * AccountLifecycle 源码接触的表名（含 List.of(...) 循环里的字面量），
     * 差集只允许落在白名单内。新增用户数据表时必须改 erase 或显式加白名单（附理由），
     * 否则本测试失败——campus_verification 事件（AF2 实测 500）不得再发生第二次。
     */
    @Test
    @DisplayName("新增表不得静默漏出注销级联（schema 全表 vs erase 覆盖差集为空）")
    void noSilentMissOfNewTables() throws Exception {
        String schema = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/test/resources/schema-h2.sql"), java.nio.charset.StandardCharsets.UTF_8);
        List<String> tables = new java.util.ArrayList<>();
        var m = java.util.regex.Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+`?(\\w+)`?",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(schema);
        while (m.find()) {
            tables.add(m.group(1));
        }

        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/zhiyu/modules/auth/service/AccountLifecycle.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        Set<String> touched = new HashSet<>();
        var sql = java.util.regex.Pattern.compile("(?:UPDATE|FROM|INTO|JOIN)\\s+`?(\\w+)`?",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(source);
        while (sql.find()) {
            touched.add(sql.group(1));
        }
        var lit = java.util.regex.Pattern.compile("\"(\\w+)\"").matcher(source);
        while (lit.find()) {
            if (tables.contains(lit.group(1))) {
                touched.add(lit.group(1));
            }
        }

        // 白名单：审计类只追加（O1 裁决）；公共内容/字典/系统配置无用户归属列（AH2 逐表核 DDL）
        Set<String> whitelist = Set.of(
                "ai_audit_log", "admin_operation_log", "ai_trace", "admin_export_log",
                "dict_mood", "dict_category", "dict_therapy_tags", "runtime_config",
                "craft_material", "craft_step", "craft_safety", "course_lesson",
                "heritage_catalog_version");

        List<String> missed = tables.stream()
                .filter(t -> !touched.contains(t) && !whitelist.contains(t))
                .toList();
        assertEquals(List.of(), missed,
                "以下表未被 erase/suspend 覆盖且不在白名单（新表必须纳入级联或显式豁免）: " + missed);
    }
}
