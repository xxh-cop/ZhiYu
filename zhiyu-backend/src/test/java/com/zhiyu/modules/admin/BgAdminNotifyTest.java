package com.zhiyu.modules.admin;

import com.zhiyu.modules.admin.service.impl.AdminGroupServiceImpl;
import com.zhiyu.modules.admin.service.impl.AdminInheritorServiceImpl;
import com.zhiyu.modules.group.entity.SupportGroup;
import com.zhiyu.modules.group.mapper.SupportGroupMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BG（2026-11-05）：传承人核验通知 + 小组停用/恢复通知。
 *
 * <p>覆盖：四个动作的触达（通过/撤销、停用/恢复）、接收人正确（传承人本人 / 组长而非成员）、
 * dedupe（同结果去重、不同结果触达、小时窗口）、状态未变化不打扰、通知失败不阻断主流程。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BgAdminNotifyTest {

    @Autowired
    private AdminInheritorServiceImpl inheritorService;
    @Autowired
    private AdminGroupServiceImpl groupService;
    @Autowired
    private JdbcTemplate jdbc;

    private static final long INHERITOR_USER = 940001L;
    private static final long PROFILE = 940100L;
    private static final long LEADER = 940002L;
    private static final long MEMBER = 940003L;
    private static final long GROUP = 940200L;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", INHERITOR_USER, "bg_inh", "传承人", 1);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", LEADER, "bg_leader", "组长", 0);
        jdbc.update("INSERT INTO user(id,username,password,nickname,role,status,privacy_agreed,can_contact,deleted)"
                + " VALUES(?,?, 'unused',?,?,1,1,0,0)", MEMBER, "bg_member", "成员", 0);
        jdbc.update("INSERT INTO inheritor_profile(id,user_id,skill,verified,create_time,update_time,deleted)"
                + " VALUES(?,?,'扎染',0,NOW(),NOW(),0)", PROFILE, INHERITOR_USER);
        jdbc.update("INSERT INTO support_group(id,name,description,max_count,member_count,status,create_time,update_time,deleted)"
                + " VALUES(?,'BG小组','测试',50,2,1,NOW(),NOW(),0)", GROUP);
        jdbc.update("INSERT INTO group_member(id,group_id,user_id,role,create_time,update_time,deleted)"
                + " VALUES(940300,?,?,'组长',NOW(),NOW(),0)", GROUP, LEADER);
        jdbc.update("INSERT INTO group_member(id,group_id,user_id,role,create_time,update_time,deleted)"
                + " VALUES(940301,?,?,'成员',NOW(),NOW(),0)", GROUP, MEMBER);
    }

    // ── BG1：传承人核验 ──

    @Test
    @DisplayName("BG1：通过/撤销都通知传承人本人；同结果去重、不同结果各自触达")
    void inheritorVerifyNotifies() {
        inheritorService.verifyInheritor(PROFILE, 1);
        var n1 = jdbc.queryForMap(
                "SELECT type,ref_type,title FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                INHERITOR_USER, PROFILE);
        assertEquals("CAMPUS_AUDIT", n1.get("type"));
        assertEquals("inheritor_profile", n1.get("ref_type"));
        assertTrue(String.valueOf(n1.get("title")).contains("已通过"));

        inheritorService.verifyInheritor(PROFILE, 1); // 同结果重复
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                Integer.class, INHERITOR_USER, PROFILE), "同结果重复核验不得重复推送");

        inheritorService.verifyInheritor(PROFILE, 0); // 撤销 → 不同结果
        var titles = jdbc.queryForList(
                "SELECT title FROM notification WHERE user_id=? AND ref_id=? AND deleted=0 ORDER BY id",
                String.class, INHERITOR_USER, PROFILE);
        assertEquals(2, titles.size());
        assertTrue(titles.get(1).contains("未通过"), "撤销应通知「未通过」: " + titles);
    }

    @Test
    @DisplayName("BG1：通知失败不阻断核验（状态仍更新）")
    void inheritorVerifySurvivesNotifyFailure() {
        InheritorProfileMapper profiles = mock(InheritorProfileMapper.class);
        InheritorProfile p = new InheritorProfile();
        p.setId(PROFILE);
        p.setUserId(INHERITOR_USER);
        p.setVerified(0);
        when(profiles.selectById(PROFILE)).thenReturn(p);
        NotificationService failing = mock(NotificationService.class);
        when(failing.notifyInheritorVerify(any(), any(), anyBoolean())).thenThrow(new RuntimeException("通知存储挂了"));
        var svc = new AdminInheritorServiceImpl(profiles, null, failing);
        assertDoesNotThrow(() -> svc.verifyInheritor(PROFILE, 1), "通知失败不得阻断核验");
        assertEquals(1, p.getVerified());
    }

    // ── BG2：小组停用/恢复 ──

    @Test
    @DisplayName("BG2：停用/恢复通知组长（含小组名），成员不收；同状态重复操作不打扰")
    void groupStatusNotifiesLeaderOnly() {
        groupService.updateGroupStatus(GROUP, 0);
        var n1 = jdbc.queryForMap(
                "SELECT type,ref_type,title,content FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                LEADER, GROUP);
        assertEquals("GROUP_JOIN", n1.get("type"));
        assertEquals("group", n1.get("ref_type"));
        assertTrue(String.valueOf(n1.get("title")).contains("已被停用"));
        assertTrue(String.valueOf(n1.get("content")).contains("BG小组"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND deleted=0", Integer.class, MEMBER),
                "成员不得收到小组处置通知");

        groupService.updateGroupStatus(GROUP, 0); // 同状态重复 → 不打扰
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                Integer.class, LEADER, GROUP));

        groupService.updateGroupStatus(GROUP, 1); // 恢复
        var titles = jdbc.queryForList(
                "SELECT title FROM notification WHERE user_id=? AND ref_id=? AND deleted=0 ORDER BY id",
                String.class, LEADER, GROUP);
        assertEquals(2, titles.size());
        assertTrue(titles.get(1).contains("已恢复使用"));
    }

    @Test
    @DisplayName("BG2：小时窗口去重——同小时内重复停用到同一状态只推一条")
    void groupStatusHourWindowDedupe() {
        groupService.updateGroupStatus(GROUP, 0);
        groupService.updateGroupStatus(GROUP, 1);
        groupService.updateGroupStatus(GROUP, 0); // 同小时内第二次停用 → dedupe
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND ref_id=? AND deleted=0",
                Integer.class, LEADER, GROUP), "窗口内重复停用应去重（1 停用 + 1 恢复）");
    }

    @Test
    @DisplayName("BG2：通知失败不阻断状态变更")
    void groupStatusSurvivesNotifyFailure() {
        SupportGroupMapper groups = mock(SupportGroupMapper.class);
        SupportGroup g = new SupportGroup();
        g.setId(GROUP);
        g.setName("BG小组");
        g.setStatus(1);
        when(groups.selectById(GROUP)).thenReturn(g);
        NotificationService failing = mock(NotificationService.class);
        when(failing.notifyGroupStatus(any(), any(), any(), anyBoolean())).thenThrow(new RuntimeException("通知存储挂了"));
        var svc = new AdminGroupServiceImpl(groups, jdbc, failing);
        assertDoesNotThrow(() -> svc.updateGroupStatus(GROUP, 0), "通知失败不得阻断状态变更");
        assertEquals(0, g.getStatus());
    }
}
