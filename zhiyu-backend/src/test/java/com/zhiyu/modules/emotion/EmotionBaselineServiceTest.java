package com.zhiyu.modules.emotion;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.emotion.service.EmotionBaselineService;
import com.zhiyu.security.LoginUser;
import com.zhiyu.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AM1（2026-11-04）情绪基线偏离提醒测试。
 * 口径：低落类（depressed）占比位移 ≥0.30 且样本达标才提示；冷却 72h；用户可关。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmotionBaselineServiceTest {

    @Autowired
    private EmotionBaselineService service;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long BASE_ID = 985000L;
    private long seq = 0;

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private long newUser(int privacyAgreed, int nudgeEnabled) {
        long id = BASE_ID + (seq += 100);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,"
                        + "baseline_nudge_enabled,deleted) VALUES(?,?, 'unused','AM1用户',0,1,?,0,?,0)",
                id, "am1_" + id, privacyAgreed, nudgeEnabled);
        return id;
    }

    private void note(long userId, String mood, LocalDateTime at) {
        jdbc.update("INSERT INTO emotion_note(id,user_id,content,mood,note_date,create_time,update_time,deleted)"
                        + " VALUES(?,?,NULL,?,?,?,?,0)",
                com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), userId, mood,
                at.toLocalDate(), at, at);
    }

    /** 造基线：8 条历史（4~20 天前，1 条 depressed）+ 3 条近期（12h/1天/2天前，全 depressed）。 */
    private void seedDecline(long userId) {
        for (int i = 0; i < 7; i++) {
            note(userId, "anxious", LocalDateTime.now().minusDays(4 + i * 2L));
        }
        note(userId, "depressed", LocalDateTime.now().minusDays(10));
        // 近期样本避开窗口边界（now-3d 恰在边界上的记录会因毫秒差被排除）
        note(userId, "depressed", LocalDateTime.now().minusHours(12));
        note(userId, "depressed", LocalDateTime.now().minusDays(1));
        note(userId, "depressed", LocalDateTime.now().minusDays(2));
    }

    private void actAs(long uid) {
        UserContext.set(new LoginUser(uid, 0));
    }

    @Test
    @DisplayName("连续下滑触发提示（happy path）：nudge=true, level=gentle, actionType=craft")
    void declineTriggersNudge() {
        long uid = newUser(1, 1);
        seedDecline(uid);
        actAs(uid);
        Map<String, Object> r = service.nudge();
        assertEquals(true, r.get("nudge"));
        assertEquals("gentle", r.get("level"));
        assertEquals("craft", r.get("actionType"));
    }

    @Test
    @DisplayName("低于阈值不提示：基线与近期都低落（无位移）")
    void noUpliftNoNudge() {
        long uid = newUser(1, 1);
        for (int i = 0; i < 6; i++) {
            note(uid, "depressed", LocalDateTime.now().minusDays(4 + i * 2L));
        }
        for (int i = 0; i < 3; i++) {
            note(uid, "depressed", LocalDateTime.now().minusHours(12 + i * 20L));
        }
        actAs(uid);
        assertEquals(false, service.nudge().get("nudge"));
    }

    @Test
    @DisplayName("冷却期内同档位不重复提示")
    void cooldownSuppressesRepeat() {
        long uid = newUser(1, 1);
        seedDecline(uid);
        actAs(uid);
        assertEquals(true, service.nudge().get("nudge"));
        assertEquals(false, service.nudge().get("nudge"), "冷却期内第二次不得再提示");
    }

    @Test
    @DisplayName("冷启动不误报：数据不足（仅 2 条）不提示")
    void coldStartNoNudge() {
        long uid = newUser(1, 1);
        note(uid, "depressed", LocalDateTime.now().minusDays(1));
        note(uid, "depressed", LocalDateTime.now().minusDays(2));
        actAs(uid);
        assertEquals(false, service.nudge().get("nudge"));
    }

    @Test
    @DisplayName("跨用户隔离：他人严重下滑不影响我")
    void crossUserIsolation() {
        long other = newUser(1, 1);
        seedDecline(other);
        long me = newUser(1, 1);
        // 我只有零星平稳记录（不满足提示条件，也验证他人数据没被算进来）
        for (int i = 0; i < 9; i++) {
            note(me, "anxious", LocalDateTime.now().minusDays(3 + i * 2L));
        }
        note(me, "anxious", LocalDateTime.now().minusDays(1));
        note(me, "stressed", LocalDateTime.now().minusDays(2));
        note(me, "tired", LocalDateTime.now().minusDays(3));
        actAs(me);
        assertEquals(false, service.nudge().get("nudge"));
        // 对照：他人本人确实触发（证明数据差异真实存在）
        actAs(other);
        assertEquals(true, service.nudge().get("nudge"));
    }

    @Test
    @DisplayName("用户关闭后不提示；未同意隐私政策被拒（1003）")
    void disabledAndConsentGate() {
        long off = newUser(1, 0);
        seedDecline(off);
        actAs(off);
        assertEquals(false, service.nudge().get("nudge"));

        long noConsent = newUser(0, 1);
        actAs(noConsent);
        assertThrows(BusinessException.class, () -> service.nudge());
    }
}
