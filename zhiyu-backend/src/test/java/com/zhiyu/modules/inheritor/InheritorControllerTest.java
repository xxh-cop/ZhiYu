package com.zhiyu.modules.inheritor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftMaterial;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftMaterialMapper;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link InheritorController} 单测（M2 批 5A）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.transaction.annotation.Transactional
class InheritorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CraftProjectMapper craftProjectMapper;

    @Autowired
    private CraftStepMapper craftStepMapper;

    @Autowired
    private CraftMaterialMapper craftMaterialMapper;

    private Long inheritorUserId;
    private Long otherInheritorUserId;
    private Long studentUserId;
    private Long projectId;
    private Long publishedProjectId;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    @BeforeEach
    void setUp() {
        User inheritor = new User();
        inheritor.setUsername("inheritor_m2_test");
        inheritor.setPassword("$2a$10$dummyhash");
        inheritor.setNickname("传承人甲");
        inheritor.setRole(1);
        inheritor.setStatus(1);
        inheritor.setCanContact(0);
        userMapper.insert(inheritor);
        inheritorUserId = inheritor.getId();

        User otherInheritor = new User();
        otherInheritor.setUsername("inheritor_other_test");
        otherInheritor.setPassword("$2a$10$dummyhash");
        otherInheritor.setNickname("传承人乙");
        otherInheritor.setRole(1);
        otherInheritor.setStatus(1);
        otherInheritor.setCanContact(0);
        userMapper.insert(otherInheritor);
        otherInheritorUserId = otherInheritor.getId();

        User student = new User();
        student.setUsername("student_m2_test");
        student.setPassword("$2a$10$dummyhash");
        student.setNickname("学生甲");
        student.setRole(0);
        student.setStatus(1);
        student.setCanContact(0);
        userMapper.insert(student);
        studentUserId = student.getId();

        // 创建一个待审项目（属于当前传承人）
        CraftProject project = new CraftProject();
        project.setInheritorId(inheritorUserId);
        project.setTitle("测试项目");
        project.setCategory("赣绣");
        project.setLevel(1);
        project.setStatus(0);
        craftProjectMapper.insert(project);
        projectId = project.getId();

        // 创建一个已上架项目（属于当前传承人）
        CraftProject published = new CraftProject();
        published.setInheritorId(inheritorUserId);
        published.setTitle("已上架项目");
        published.setCategory("陶艺");
        published.setLevel(2);
        published.setStatus(1);
        craftProjectMapper.insert(published);
        publishedProjectId = published.getId();

        // 添加一个步骤到待审项目
        CraftStep step = new CraftStep();
        step.setProjectId(projectId);
        step.setStepNo(1);
        step.setTitle("第一步");
        craftStepMapper.insert(step);

        // 添加一个材料到待审项目
        CraftMaterial material = new CraftMaterial();
        material.setProjectId(projectId);
        material.setName("绣布");
        material.setQty("1块");
        craftMaterialMapper.insert(material);
    }

    // ── 接口 1：POST /api/inheritor/projects ──

    @Test
    @DisplayName("传承人创建项目 → 200")
    void testInheritor_CreateProject_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "新项目", "level", 1, "category", "扎染"));

        mockMvc.perform(post("/api/inheritor/projects")
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @DisplayName("学生访问传承人接口 → 403")
    void testStudent_CreateProject_Returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", "hack", "level", 1));

        mockMvc.perform(post("/api/inheritor/projects")
                        .header("Authorization", "Bearer " + token(studentUserId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── 接口 2：GET /api/inheritor/projects ──

    @Test
    @DisplayName("传承人查看自己的项目列表 → 200")
    void testInheritor_ListProjects_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritor/projects")
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ── 接口 3：GET /api/inheritor/projects/{id} ──

    @Test
    @DisplayName("传承人查看项目详情（含步骤+材料） → 200")
    void testInheritor_GetProjectDetail_Returns200() throws Exception {
        mockMvc.perform(get("/api/inheritor/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(projectId))
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.materials").isArray());
    }

    @Test
    @DisplayName("传承人查看别人的项目 → 1003")
    void testInheritor_GetOthersProject_Returns1003() throws Exception {
        mockMvc.perform(get("/api/inheritor/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token(otherInheritorUserId, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 接口 4：PUT /api/inheritor/projects/{id} ──

    @Test
    @DisplayName("传承人编辑待审项目 → 200")
    void testInheritor_UpdateProject_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", "新标题"));

        mockMvc.perform(put("/api/inheritor/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("传承人编辑已上架项目 → 3001")
    void testInheritor_UpdatePublishedProject_Returns3001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("title", "hack"));

        mockMvc.perform(put("/api/inheritor/projects/{id}", publishedProjectId)
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001));
    }

    // ── 接口 5：DELETE /api/inheritor/projects/{id} ──

    @Test
    @DisplayName("传承人删除待审项目 → 200")
    void testInheritor_DeleteProject_Returns200() throws Exception {
        mockMvc.perform(delete("/api/inheritor/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ── 接口 6：POST /api/inheritor/projects/{id}/steps ──

    @Test
    @DisplayName("传承人添加步骤 → 200")
    void testInheritor_AddStep_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "stepNo", 2, "title", "第二步", "guide", "引导语"));

        mockMvc.perform(post("/api/inheritor/projects/{id}/steps", projectId)
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @DisplayName("传承人给别人项目添加步骤 → 1003")
    void testInheritor_AddStep_OthersProject_Returns1003() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("stepNo", 1, "title", "hack"));

        mockMvc.perform(post("/api/inheritor/projects/{id}/steps", projectId)
                        .header("Authorization", "Bearer " + token(otherInheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 接口 9：POST /api/inheritor/projects/{id}/materials ──

    @Test
    @DisplayName("传承人添加材料 → 200")
    void testInheritor_AddMaterial_Returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "绣线", "qty", "10根"));

        mockMvc.perform(post("/api/inheritor/projects/{id}/materials", projectId)
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists());
    }

    // ── 接口 10：DELETE /api/inheritor/projects/{id}/materials/{materialId} ──

    @Test
    @DisplayName("传承人删除材料 → 200")
    void testInheritor_DeleteMaterial_Returns200() throws Exception {
        // 先查出已有材料的 id
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CraftMaterial> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(CraftMaterial::getProjectId, projectId);
        CraftMaterial material = craftMaterialMapper.selectOne(wrapper);

        mockMvc.perform(delete("/api/inheritor/projects/{id}/materials/{materialId}", projectId, material.getId())
                        .header("Authorization", "Bearer " + token(inheritorUserId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
