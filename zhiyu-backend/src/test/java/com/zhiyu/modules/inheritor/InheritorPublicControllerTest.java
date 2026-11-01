package com.zhiyu.modules.inheritor;

import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InheritorPublicController} 单测（M2 批 6A）。
 *
 * <p>测试 {@code /api/inheritors}（复数）公开查询接口：
 * <ul>
 *   <li>学生查传承人列表 → 200（只返回 verified=1）</li>
 *   <li>未登录查传承人列表 → 401</li>
 *   <li>查传承人详情 → 200</li>
 *   <li>查不存在的传承人 → 1004</li>
 *   <li>查未认证传承人 → 1004</li>
 *   <li>关键词搜索 → 200</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class InheritorPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private InheritorProfileMapper inheritorProfileMapper;

    private Long studentUserId;
    private Long verifiedProfileId;
    private Long unverifiedProfileId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        // 学生
        User student = new User();
        student.setUsername("student_pub_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        // 已认证传承人
        User verifiedInheritor = new User();
        verifiedInheritor.setUsername("inheritor_verified");
        verifiedInheritor.setPassword("$2a$10$dummyhash");
        verifiedInheritor.setNickname("赣绣大师");
        verifiedInheritor.setRole(1);
        verifiedInheritor.setStatus(1);
        verifiedInheritor.setCanContact(0);
        verifiedInheritor.setSchool("江西艺术学院");
        userMapper.insert(verifiedInheritor);

        InheritorProfile verifiedProfile = new InheritorProfile();
        verifiedProfile.setUserId(verifiedInheritor.getId());
        verifiedProfile.setSkill("赣绣");
        verifiedProfile.setIntro("从事赣绣四十余年");
        verifiedProfile.setHonor("省级非遗传承人");
        verifiedProfile.setVerified(1);
        inheritorProfileMapper.insert(verifiedProfile);
        verifiedProfileId = verifiedProfile.getId();

        // 未认证传承人
        User unverifiedInheritor = new User();
        unverifiedInheritor.setUsername("inheritor_unverified");
        unverifiedInheritor.setPassword("$2a$10$dummyhash");
        unverifiedInheritor.setNickname("陶艺新人");
        unverifiedInheritor.setRole(1);
        unverifiedInheritor.setStatus(1);
        unverifiedInheritor.setCanContact(0);
        userMapper.insert(unverifiedInheritor);

        InheritorProfile unverifiedProfile = new InheritorProfile();
        unverifiedProfile.setUserId(unverifiedInheritor.getId());
        unverifiedProfile.setSkill("陶艺");
        unverifiedProfile.setIntro("刚入门的陶艺爱好者");
        unverifiedProfile.setHonor("");
        unverifiedProfile.setVerified(0);
        inheritorProfileMapper.insert(unverifiedProfile);
        unverifiedProfileId = unverifiedProfile.getId();
    }

    // ── 接口 1：GET /api/inheritors ──

    @Test
    @DisplayName("学生查传承人列表 → 200（只返回 verified=1）")
    void testStudent_ListInheritors_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritors")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].skill").value("赣绣"));
    }

    @Test
    @DisplayName("未登录查传承人列表 → 401")
    void testAnonymous_ListInheritors_Returns401() throws Exception {
        mockMvc.perform(get("/api/inheritors"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("关键词搜索传承人 → 200")
    void testStudent_SearchInheritors_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritors")
                        .param("keyword", "赣绣")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].skill").value("赣绣"));
    }

    // ── 接口 2：GET /api/inheritors/{id} ──

    @Test
    @DisplayName("学生查传承人详情 → 200")
    void testStudent_GetInheritor_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritors/{id}", verifiedProfileId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(verifiedProfileId))
                .andExpect(jsonPath("$.data.skill").value("赣绣"))
                .andExpect(jsonPath("$.data.school").value("江西艺术学院"));
    }

    @Test
    @DisplayName("查不存在的传承人 → 1004")
    void testStudent_GetInheritor_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/inheritors/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("查未认证传承人 → 1004")
    void testStudent_GetUnverifiedInheritor_Returns1004() throws Exception {
        mockMvc.perform(get("/api/inheritors/{id}", unverifiedProfileId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 3：传承人故事内容包 ──

    @Test
    @DisplayName("学生读取故事列表 → 返回 3 条合成故事")
    void testStudent_ListStories_ReturnsThree() throws Exception {
        mockMvc.perform(get("/api/inheritors/stories")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].synthetic").value(true));
    }

    @Test
    @DisplayName("学生读取故事详情 → 包含来源与小节")
    void testStudent_StoryDetail_ReturnsSources() throws Exception {
        mockMvc.perform(get("/api/inheritors/stories/{id}", "xiabu-first-leaf")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.traditionName").value("夏布绣"))
                .andExpect(jsonPath("$.data.sources[0].organization").value("中国非物质文化遗产网·中国非物质文化遗产数字博物馆"))
                .andExpect(jsonPath("$.data.boundary").value(org.hamcrest.Matchers.containsString("合成")));
    }

    @Test
    @DisplayName("不存在的故事 → 1004")
    void testStudent_MissingStory_Returns1004() throws Exception {
        mockMvc.perform(get("/api/inheritors/stories/{id}", "not-exists")
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
