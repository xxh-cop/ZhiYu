package com.zhiyu.modules.craft;

import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftMaterial;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftMaterialMapper;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CraftProjectPublicController} 单测（M2 批 6A）。
 *
 * <p>测试学生端可见的项目公开查询接口：
 * <ul>
 *   <li>查传承人的已上架项目列表 → 200</li>
 *   <li>查未认证传承人的项目 → 1004</li>
 *   <li>查已上架项目详情 → 200（含步骤 + 材料 + 传承人信息）</li>
 *   <li>查未上架项目详情 → 1004</li>
 *   <li>查不存在的项目 → 1004</li>
 *   <li>未登录查项目详情 → 401</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class CraftProjectPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private InheritorProfileMapper inheritorProfileMapper;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftStepMapper craftStepMapper;

    @Autowired
    private CraftMaterialMapper craftMaterialMapper;

    private Long studentUserId;
    private Long inheritorUserId;
    private Long verifiedProfileId;
    private Long publishedProjectId;
    private Long unpublishedProjectId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        // 学生
        User student = new User();
        student.setUsername("student_craft_pub_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生乙");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        // 已认证传承人
        User inheritor = new User();
        inheritor.setUsername("inheritor_craft_pub");
        inheritor.setPassword("$2a$10$dummyhash");
        inheritor.setNickname("陶艺大师");
        inheritor.setRole(1);
        inheritor.setStatus(1);
        inheritor.setCanContact(0);
        userMapper.insert(inheritor);
        inheritorUserId = inheritor.getId();

        InheritorProfile profile = new InheritorProfile();
        profile.setUserId(inheritor.getId());
        profile.setSkill("陶艺");
        profile.setIntro("从事陶艺三十年");
        profile.setHonor("市级非遗传承人");
        profile.setVerified(1);
        inheritorProfileMapper.insert(profile);
        verifiedProfileId = profile.getId();

        // 已上架项目
        CraftProject published = new CraftProject();
        published.setInheritorId(inheritor.getId());
        published.setTitle("入门陶艺杯");
        published.setIntro("手作陶艺入门项目");
        published.setCultureBg("景德镇陶瓷文化");
        published.setLevel(1);
        published.setEstMinutes(60);
        published.setCategory("陶艺");
        published.setTherapyTags("专注,放松");
        published.setCoverUrl("http://example.com/cover.jpg");
        published.setStatus(1);
        craftProjectMapper.insert(published);
        publishedProjectId = published.getId();

        // 步骤
        CraftStep step = new CraftStep();
        step.setProjectId(publishedProjectId);
        step.setStepNo(1);
        step.setTitle("揉泥");
        step.setGuide("将陶泥揉至均匀");
        craftStepMapper.insert(step);

        // 材料
        CraftMaterial material = new CraftMaterial();
        material.setProjectId(publishedProjectId);
        material.setName("陶泥");
        material.setQty("500g");
        craftMaterialMapper.insert(material);

        // 未上架项目（待审）
        CraftProject unpublished = new CraftProject();
        unpublished.setInheritorId(inheritor.getId());
        unpublished.setTitle("待审项目");
        unpublished.setLevel(2);
        unpublished.setCategory("陶艺");
        unpublished.setStatus(0);
        craftProjectMapper.insert(unpublished);
        unpublishedProjectId = unpublished.getId();
    }

    // ── 接口 3：GET /api/inheritors/{id}/projects ──

    @Test
    @DisplayName("学生查传承人的已上架项目列表 → 200")
    void testStudent_ListProjectsByInheritor_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritors/{id}/projects", verifiedProfileId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].title").value("入门陶艺杯"));
    }

    @Test
    @DisplayName("查未认证传承人的项目 → 1004")
    void testStudent_ListProjects_UnverifiedInheritor_Returns1004() throws Exception {
        // 创建未认证传承人
        User unverified = new User();
        unverified.setUsername("inheritor_unverified_craft");
        unverified.setPassword("$2a$10$dummyhash");
        unverified.setNickname("未认证传承人");
        unverified.setRole(1);
        unverified.setStatus(1);
        unverified.setCanContact(0);
        userMapper.insert(unverified);

        InheritorProfile unverifiedProfile = new InheritorProfile();
        unverifiedProfile.setUserId(unverified.getId());
        unverifiedProfile.setSkill("剪纸");
        unverifiedProfile.setVerified(0);
        inheritorProfileMapper.insert(unverifiedProfile);

        mockMvc.perform(get("/api/inheritors/{id}/projects", unverifiedProfile.getId())
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 接口 4：GET /api/craft/projects/{id} ──

    @Test
    @DisplayName("学生查已上架项目详情 → 200（含步骤+材料+传承人信息）")
    void testStudent_GetProjectDetail_Returns200() throws Exception {
        mockMvc.perform(get("/api/craft/projects/{id}", publishedProjectId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(publishedProjectId))
                .andExpect(jsonPath("$.data.title").value("入门陶艺杯"))
                .andExpect(jsonPath("$.data.inheritorNickname").value("陶艺大师"))
                .andExpect(jsonPath("$.data.inheritorSkill").value("陶艺"))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.steps[0].title").value("揉泥"))
                .andExpect(jsonPath("$.data.materials").isArray())
                .andExpect(jsonPath("$.data.materials[0].name").value("陶泥"));
    }

    @Test
    @DisplayName("项目技法与示范者专长不一致时不返回示范者身份")
    void testStudent_GetProjectDetail_SkillMismatch_OmitsInheritorIdentity() throws Exception {
        CraftProject mismatched = new CraftProject();
        mismatched.setInheritorId(inheritorUserId);
        mismatched.setTitle("扎染方巾");
        mismatched.setIntro("扎染入门项目");
        mismatched.setLevel(2);
        mismatched.setEstMinutes(45);
        mismatched.setCategory("扎染");
        mismatched.setStatus(1);
        craftProjectMapper.insert(mismatched);

        mockMvc.perform(get("/api/craft/projects/{id}", mismatched.getId())
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("扎染方巾"))
                .andExpect(jsonPath("$.data.inheritorId").value(nullValue()))
                .andExpect(jsonPath("$.data.inheritorProfileId").value(nullValue()))
                .andExpect(jsonPath("$.data.inheritorNickname").value(nullValue()))
                .andExpect(jsonPath("$.data.inheritorSkill").value(nullValue()));
    }

    @Test
    @DisplayName("查未上架项目详情 → 1004")
    void testStudent_GetUnpublishedProject_Returns1004() throws Exception {
        mockMvc.perform(get("/api/craft/projects/{id}", unpublishedProjectId)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("查不存在的项目 → 1004")
    void testStudent_GetProject_NotFound_Returns1004() throws Exception {
        mockMvc.perform(get("/api/craft/projects/{id}", 99999L)
                        .header("Authorization", "Bearer " + token(studentUserId, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("未登录查项目详情 → 401")
    void testAnonymous_GetProjectDetail_Returns401() throws Exception {
        mockMvc.perform(get("/api/craft/projects/{id}", publishedProjectId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }
}
