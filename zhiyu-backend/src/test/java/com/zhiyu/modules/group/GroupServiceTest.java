package com.zhiyu.modules.group;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.group.dto.GroupCreateReq;
import com.zhiyu.modules.group.entity.GroupMember;
import com.zhiyu.modules.group.entity.SupportGroup;
import com.zhiyu.modules.group.mapper.GroupMemberMapper;
import com.zhiyu.modules.group.mapper.SupportGroupMapper;
import com.zhiyu.modules.group.service.GroupService;
import com.zhiyu.modules.group.vo.GroupVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GroupService} 集成测试（T1.8b）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class GroupServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private SupportGroupMapper supportGroupMapper;

    @Autowired
    private GroupMemberMapper groupMemberMapper;

    @Autowired
    private UserMapper userMapper;

    private static final Long USER_ID = 8401L;
    private static final Long OTHER_USER_ID = 8402L;
    private Long openGroupId;
    private Long closedGroupId;

    @BeforeEach
    void setUp() {
        insertUser(USER_ID, "组长小织");
        insertUser(OTHER_USER_ID, "成员小愈");

        // 开放小组（memberCount=0）
        openGroupId = insertGroup("开放小组", "测试用", 50, 0, 1);

        // 关闭小组
        closedGroupId = insertGroup("关闭小组", "已关闭", 50, 0, 0);
    }

    @Test
    @DisplayName("小组列表：无小组 → 空列表")
    void testListGroups_Empty() {
        // 删除 setUp 中创建的小组
        supportGroupMapper.deleteById(openGroupId);
        supportGroupMapper.deleteById(closedGroupId);

        List<GroupVO> result = groupService.listGroups(USER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("小组列表：用户已加入 1 个小组 → joined=true, myRole=组长")
    void testListGroups_WithJoined() {
        // USER_ID 加入 openGroup（作为组长）
        insertMember(openGroupId, USER_ID, "组长");

        List<GroupVO> result = groupService.listGroups(USER_ID);

        assertEquals(1, result.size()); // 只有 1 个 status=1 的小组
        GroupVO vo = result.get(0);
        assertEquals(openGroupId, vo.getId());
        assertTrue(vo.getJoined());
        assertEquals("组长", vo.getMyRole());
    }

    @Test
    @DisplayName("创建小组：成功，memberCount=1, myRole=组长")
    void testCreateGroup_Success() {
        GroupCreateReq req = new GroupCreateReq();
        req.setName("新小组");
        req.setDescription("测试创建");
        req.setMaxCount(30);

        GroupVO vo = groupService.createGroup(USER_ID, req);

        assertNotNull(vo.getId());
        assertEquals("新小组", vo.getName());
        assertEquals(30, vo.getMaxCount());
        assertEquals(1, vo.getMemberCount());
        assertEquals(1, vo.getStatus());
        assertTrue(vo.getJoined());
        assertEquals("组长", vo.getMyRole());

        // 确认 member 落库
        Long count = groupMemberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, vo.getId())
                        .eq(GroupMember::getUserId, USER_ID));
        assertEquals(1, count.intValue());
    }

    @Test
    @DisplayName("创建小组：不传 maxCount → 默认 50")
    void testCreateGroup_MaxCountDefault() {
        GroupCreateReq req = new GroupCreateReq();
        req.setName("默认上限");

        GroupVO vo = groupService.createGroup(USER_ID, req);

        assertEquals(50, vo.getMaxCount());
    }

    @Test
    @DisplayName("加入小组：成功，memberCount+1，myRole=成员")
    void testJoinGroup_Success() {
        GroupVO vo = groupService.joinGroup(OTHER_USER_ID, openGroupId);

        assertTrue(vo.getJoined());
        assertEquals("成员", vo.getMyRole());
        assertEquals(1, vo.getMemberCount());
    }

    @Test
    @DisplayName("重复加入 → 3001")
    void testJoinGroup_AlreadyJoined() {
        groupService.joinGroup(OTHER_USER_ID, openGroupId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.joinGroup(OTHER_USER_ID, openGroupId));

        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("小组不存在 → 1004")
    void testJoinGroup_GroupNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.joinGroup(OTHER_USER_ID, 99999L));

        assertEquals(1004, ex.getCode());
    }

    @Test
    @DisplayName("小组已关闭 → 3001")
    void testJoinGroup_GroupClosed() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.joinGroup(OTHER_USER_ID, closedGroupId));

        assertEquals(3001, ex.getCode());
    }

    @Test
    @DisplayName("小组已满 → 3001（并发保护）")
    void testJoinGroup_GroupFull() {
        // memberCount = maxCount = 1
        Long fullGroupId = insertGroup("满员小组", "已满", 1, 1, 1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.joinGroup(OTHER_USER_ID, fullGroupId));

        assertEquals(3001, ex.getCode());
    }

    // ──────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────

    private void insertUser(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setUsername("test_" + id);
        u.setPassword("$2a$10$dummy");
        u.setNickname(nickname);
        u.setRole(0);
        u.setStatus(1);
        u.setCanContact(0);
        u.setCreateTime(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        u.setDeleted(0);
        userMapper.insert(u);
    }

    private Long insertGroup(String name, String description, int maxCount, int memberCount, int status) {
        SupportGroup g = new SupportGroup();
        g.setName(name);
        g.setDescription(description);
        g.setMaxCount(maxCount);
        g.setMemberCount(memberCount);
        g.setStatus(status);
        g.setCreateTime(LocalDateTime.now());
        g.setUpdateTime(LocalDateTime.now());
        g.setDeleted(0);
        supportGroupMapper.insert(g);
        return g.getId();
    }

    private void insertMember(Long groupId, Long userId, String role) {
        GroupMember m = new GroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setRole(role);
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        m.setDeleted(0);
        groupMemberMapper.insert(m);
    }
}
