package com.zhiyu.modules.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.group.entity.GroupMember;
import com.zhiyu.modules.group.entity.SupportGroup;
import com.zhiyu.modules.group.mapper.GroupMemberMapper;
import com.zhiyu.modules.group.mapper.SupportGroupMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.group.controller.GroupController} 单测（批次 6-F）。
 *
 * <p>覆盖 3 个接口：GET /api/groups、POST /api/groups、POST /api/groups/{id}/join
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SupportGroupMapper supportGroupMapper;

    @Autowired
    private GroupMemberMapper groupMemberMapper;

    private Long studentUserId;
    private Long groupId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setUsername("student_grp_ctrl_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        SupportGroup group = new SupportGroup();
        group.setName("测试互助小组");
        group.setDescription("一个测试用互助小组");
        group.setMaxCount(50);
        group.setMemberCount(1);
        group.setStatus(1);
        supportGroupMapper.insert(group);
        groupId = group.getId();
    }

    // ── GET /api/groups ──

    @Test
    @DisplayName("未登录访问小组列表 → 401")
    void testListGroups_NoToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生查小组列表 → 200 + 返回 status=1 的小组")
    void testListGroups_Student_Returns200() throws Exception {
        mockMvc.perform(get("/api/groups")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("测试互助小组"))
                .andExpect(jsonPath("$.data[0].status").value(1));
    }

    // ── POST /api/groups ──

    @Test
    @DisplayName("学生创建小组 → 200 + 数据库验证")
    void testCreateGroup_Student_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "新创建小组",
                "description", "描述",
                "maxCount", 30
        ));

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token(studentUserId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("新创建小组"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.myRole").value("组长"));
    }

    @Test
    @DisplayName("创建小组时名称为空 → 400 + 1001")
    void testCreateGroup_BlankName_Returns1001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "description", "描述"
        ));

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + token(studentUserId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ── POST /api/groups/{id}/join ──

    @Test
    @DisplayName("加入不存在的组 → 404 + 1004")
    void testJoinGroup_NotFound_Returns1004() throws Exception {
        mockMvc.perform(post("/api/groups/{id}/join", 99999L)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("重复加入同一小组 → 200 + 3001")
    void testJoinGroup_Duplicate_Returns3001() throws Exception {
        // 先加入一次
        mockMvc.perform(post("/api/groups/{id}/join", groupId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 再加入一次 → 3001
        mockMvc.perform(post("/api/groups/{id}/join", groupId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    @DisplayName("学生加入开放小组 → 200 + 返回成员角色")
    void testJoinGroup_Success_Returns200() throws Exception {
        // 使用另一个学生加入
        User student2 = new User();
        student2.setUsername("student_grp_ctrl_test2");
        student2.setPassword("$2a$10$dummyhash");
        student2.setNickname("学生乙");
        student2.setRole(0);
        student2.setStatus(1);
        student2.setCanContact(0);
        userMapper.insert(student2);

        mockMvc.perform(post("/api/groups/{id}/join", groupId)
                        .header("Authorization", "Bearer " + token(student2.getId(), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.joined").value(true))
                .andExpect(jsonPath("$.data.myRole").value("成员"))
                .andExpect(jsonPath("$.data.memberCount").value(2));
    }
}
