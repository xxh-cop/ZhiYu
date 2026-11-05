package com.zhiyu.modules.craft;

import com.zhiyu.ai.AiClient;
import com.zhiyu.modules.craft.service.CraftRecommendService;
import com.zhiyu.modules.craft.service.RecommendFeedbackBoost;
import com.zhiyu.modules.craft.vo.RecommendVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AM2（2026-11-04）推荐反馈回灌加权测试。
 *
 * <p>确定性口径：CLICK 正 / DISMISS 负，按事件标签映射回候选；
 * 反馈不足 min-events（3）或开关关闭时排序与规则层逐条一致。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class RecommendFeedbackBoostTest {

    @Autowired
    private RecommendFeedbackBoost boost;
    @Autowired
    private CraftRecommendService recommendService;
    @Autowired
    private JdbcTemplate db;

    @MockBean
    private AiClient aiClient;

    private static final long USER = 986100L;
    private static final long OTHER = 986200L;
    // 两个都不命中 anxious 优先标签的项目（触觉安抚/物理宣泄 ≠ 静心/专注/安抚），同 level → 规则分相同
    private static final long P_A = 986001L; // 触觉安抚
    private static final long P_B = 986002L; // 物理宣泄

    @AfterEach
    void restore() {
        ReflectionTestUtils.setField(boost, "enabled", true);
        ReflectionTestUtils.setField(boost, "dismissSpreadEnabled", true);
    }

    private void seedProjects() {
        for (long[] p : new long[][]{{P_A}, {P_B}}) {
            db.update("INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,"
                            + "category,therapy_tags,status,create_time,update_time,deleted)"
                            + " VALUES(?,1,?,?,NULL,1,30,'其他',?,1,NOW(),NOW(),0)",
                    p[0], "项目" + p[0], "简介", p[0] == P_A ? "触觉安抚" : "物理宣泄");
            db.update("INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,"
                            + "create_time,update_time,deleted) VALUES(?,?,'测试材料','LOW','','按步骤操作','夹具',NOW(),NOW(),0)",
                    p[0] + 10000, p[0]);
        }
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted)"
                + " VALUES(?,?,'unused','AM2用户',0,1,1,0)", USER, "am2_" + USER);
        db.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,deleted)"
                + " VALUES(?,?,'unused','AM2他人',0,1,1,0)", OTHER, "am2_" + OTHER);
    }

    private long recSeq = 0;

    private void event(long userId, long projectId, String type) {
        // 唯一键 (user_id, recommendation_id, project_id, event_type)：同一项目多次反馈用不同推荐单
        db.update("INSERT INTO craft_recommend_event(id,user_id,recommendation_id,project_id,event_type,source,"
                        + "create_time,update_time,deleted) VALUES(?,?,?,?,?,'HOME',NOW(),NOW(),0)",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), userId, ++recSeq, projectId, type);
    }

    @Test
    @DisplayName("稀疏不生效：反馈不足 3 条时返回空表（不改排序）")
    void sparseDataNoBoost() {
        seedProjects();
        event(USER, P_A, "CLICK");
        event(USER, P_B, "CLICK");
        assertTrue(boost.boosts(USER).isEmpty(), "少于 min-events 不得加权");
    }

    @Test
    @DisplayName("正反馈提升同标签项目、负反馈压低；跨用户不串")
    void positiveAndNegativeBoost() {
        seedProjects();
        event(USER, P_A, "CLICK");
        event(USER, P_A, "CLICK");
        event(USER, P_A, "CLICK");
        Map<Long, Double> b = boost.boosts(USER);
        assertTrue(b.getOrDefault(P_A, 0.0) > 0, "CLICK 应为正加权");
        assertTrue(b.getOrDefault(P_B, 0.0) == 0.0, "未反馈项目不加权");

        // 负反馈
        event(OTHER, P_B, "DISMISS");
        event(OTHER, P_B, "DISMISS");
        event(OTHER, P_B, "DISMISS");
        Map<Long, Double> otherB = boost.boosts(OTHER);
        assertTrue(otherB.getOrDefault(P_B, 0.0) < 0, "DISMISS 应为负加权");
        // 跨用户：USER 的加权不含 OTHER 的 DISMISS
        assertTrue(boost.boosts(USER).getOrDefault(P_B, 0.0) == 0.0, "跨用户不得串扰");
    }

    @Test
    @DisplayName("回灌改变推荐排序；开关关闭逐条一致")
    void boostChangesOrderAndOffIsIdentical() {
        seedProjects();
        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("理由");
        db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted)"
                + " VALUES(986500,?,'anxious',0,NOW(),NOW(),0)", USER);

        // 基线（无反馈）：两项目同分，按稳定排序（插入序）A 在前
        RecommendVO base = recommendService.recommend(USER, 986500L, 3, 60);
        List<Long> baseOrder = base.getItems().stream().map(i -> i.getProjectId()).toList();

        // 3 次 CLICK P_B → B 加权前移
        event(USER, P_B, "CLICK");
        event(USER, P_B, "CLICK");
        event(USER, P_B, "CLICK");
        RecommendVO boosted = recommendService.recommend(USER, 986500L, 3, 60);
        List<Long> boostedOrder = boosted.getItems().stream().map(i -> i.getProjectId()).toList();
        assertTrue(boostedOrder.indexOf(P_B) < boostedOrder.indexOf(P_A),
                "正反馈应把 P_B 提到 P_A 前: base=" + baseOrder + " boosted=" + boostedOrder);

        // 开关关闭：即使有反馈，排序与基线逐条一致
        ReflectionTestUtils.setField(boost, "enabled", false);
        RecommendVO off = recommendService.recommend(USER, 986500L, 3, 60);
        assertEquals(baseOrder, off.getItems().stream().map(i -> i.getProjectId()).toList(),
                "开关关闭时排序必须与基线逐条一致");
    }

    // ── 2026-11-05 DISMISS 强化（自降权 + 同品类降权） ──

    private void seedSpreadProjects() {
        // C：无标签（缺口场景主角），D：与 C 同品类，E：异品类；三者均不命中 anxious 优先标签，规则分同为 0.4
        Object[][] rows = {
                {986003L, "触觉安抚", "布艺"},   // C 无标签场景另行覆盖（见下）
                {986004L, "物理宣泄", "布艺"},
                {986005L, "物理宣泄", "编织"},
        };
        for (Object[] p : rows) {
            db.update("INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,"
                            + "category,therapy_tags,status,create_time,update_time,deleted)"
                            + " VALUES(?,1,?,?,NULL,1,30,?,?,1,NOW(),NOW(),0)",
                    p[0], "项目" + p[0], "简介", p[2], p[1]);
            db.update("INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,"
                            + "create_time,update_time,deleted) VALUES(?,?,'测试材料','LOW','','按步骤操作','夹具',NOW(),NOW(),0)",
                    ((Long) p[0]) + 10000, p[0]);
        }
        // C 改写成无标签（覆盖缺口场景）
        db.update("UPDATE craft_project SET therapy_tags='' WHERE id=986003");
    }

    @Test
    @DisplayName("DISMISS 强化：无标签项目自身被降权（修复免疫缺口），同品类连带降权，异品类不动")
    void dismissSpreadSelfAndCategory() {
        seedProjects();
        seedSpreadProjects();
        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("理由");
        db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted)"
                + " VALUES(986501,?,'anxious',0,NOW(),NOW(),0)", USER);

        // 基线：五项目同分（0.4），按稳定排序 C(986003) 在第三位
        RecommendVO base = recommendService.recommend(USER, 986501L, 3, 60);
        List<Long> baseOrder = base.getItems().stream().map(i -> i.getProjectId()).toList();
        assertEquals(List.of(986001L, 986002L, 986003L), baseOrder, "基线应同分按 id 序");

        // 3 次 DISMISS C（无标签项目）→ C 自降权 + 布艺品类降权
        event(USER, 986003L, "DISMISS");
        event(USER, 986003L, "DISMISS");
        event(USER, 986003L, "DISMISS");

        Map<Long, Double> b = boost.boosts(USER);
        assertTrue(b.getOrDefault(986003L, 0.0) < 0, "无标签项目自身必须被降权（缺口修复）");
        assertTrue(b.get(986003L) <= b.getOrDefault(986004L, 0.0), "被点项目降权不弱于同品类连带");
        assertTrue(b.getOrDefault(986004L, 0.0) < 0, "同品类（布艺）连带降权");
        assertEquals(0.0, b.getOrDefault(986005L, 0.0), "异品类（编织）不受影响");

        // 端到端：C 掉出前三，E（异品类）补位
        RecommendVO after = recommendService.recommend(USER, 986501L, 3, 60);
        List<Long> afterOrder = after.getItems().stream().map(i -> i.getProjectId()).toList();
        assertTrue(!afterOrder.contains(986003L), "被 DISMISS 的项目应掉出推荐: " + afterOrder);
        assertTrue(afterOrder.contains(986005L), "异品类项目应补位: " + afterOrder);
    }

    @Test
    @DisplayName("DISMISS 强化可回滚：dismiss-spread-enabled=false 时排序与 AM2 原始口径逐条一致")
    void dismissSpreadOffRestoresBaseline() {
        seedProjects();
        seedSpreadProjects();
        when(aiClient.recommendReason(anyString(), anyList())).thenReturn("理由");
        db.update("INSERT INTO emotion_session(id,user_id,mood,status,create_time,update_time,deleted)"
                + " VALUES(986502,?,'anxious',0,NOW(),NOW(),0)", USER);

        // 强化开启时的基线（无反馈）
        RecommendVO base = recommendService.recommend(USER, 986502L, 3, 60);
        List<Long> baseOrder = base.getItems().stream().map(i -> i.getProjectId()).toList();

        // 关掉强化开关后再 DISMISS：C 无标签 → 标签映射空 → 排序必须回到原始口径
        ReflectionTestUtils.setField(boost, "dismissSpreadEnabled", false);
        event(USER, 986003L, "DISMISS");
        event(USER, 986003L, "DISMISS");
        event(USER, 986003L, "DISMISS");
        Map<Long, Double> b = boost.boosts(USER);
        assertTrue(b.isEmpty() || b.values().stream().allMatch(v -> v == 0.0),
                "强化关闭且无标签时不得产生任何加权");
        RecommendVO off = recommendService.recommend(USER, 986502L, 3, 60);
        assertEquals(baseOrder, off.getItems().stream().map(i -> i.getProjectId()).toList(),
                "强化关闭时排序必须与基线逐条一致");
    }
}
