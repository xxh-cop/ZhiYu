package com.zhiyu.modules.work;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.work.entity.Work;
import com.zhiyu.modules.work.mapper.WorkMapper;
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
 * {@link com.zhiyu.modules.work.controller.WorkController} 测试（T1.8a + 批次 6-F）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class WorkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkMapper workMapper;

    private String token;
    private Long userId;
    private Long publishedWorkId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("work_test_user_6f");
        user.setPassword("$2a$10$dummyhash");
        user.setNickname("作品测试用户");
        user.setRole(0);
        user.setStatus(1);
        user.setCanContact(0);
        user.setPrivacyAgreed(1);
        userMapper.insert(user);
        userId = user.getId();

        token = jwtUtil.createToken(userId, 0);

        // 插入一个已发布的作品（用于点赞/评论测试）
        Work work = new Work();
        work.setUserId(userId);
        work.setTitle("测试作品");
        work.setMediaType("text");
        work.setVisible(1);
        work.setAnonymous(1);
        work.setLikeCount(0);
        work.setStatus(1);
        workMapper.insert(work);
        publishedWorkId = work.getId();
    }

    // ── 原有 3 个用例 ──

    @Test
    @DisplayName("POST /api/works 无 token → 401")
    void testNoToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/works")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/works 缺 title → 400 + 1001")
    void testMissingTitle_Returns400() throws Exception {
        mockMvc.perform(post("/api/works")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("GET /api/works 有 token → 200")
    void testListWorks_Success() throws Exception {
        mockMvc.perform(get("/api/works")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ── 批次 6-F 新增用例 ──

    @Test
    @DisplayName("发布作品 media_type=image 且 coverUrl 空 → 400 + 1001")
    void testPublishImage_NoCoverUrl_Returns1001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "图片作品",
                "mediaType", "image"
        ));

        mockMvc.perform(post("/api/works")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("发布作品 media_type=video 且 videoUrl 空 → 400 + 1001")
    void testPublishVideo_NoVideoUrl_Returns1001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "视频作品",
                "mediaType", "video"
        ));

        mockMvc.perform(post("/api/works")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("发布作品 media_type=text（无媒体）→ 200")
    void testPublishText_Success() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "纯文字作品",
                "mediaType", "text",
                "message", "手作让人静心"
        ));

        mockMvc.perform(post("/api/works")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value("纯文字作品"))
                .andExpect(jsonPath("$.data.mediaType").value("text"));
    }

    @Test
    @DisplayName("点赞不存在的作品 → 404 + 1004")
    void testLike_NonExistentWork_Returns1004() throws Exception {
        mockMvc.perform(post("/api/works/{id}/like", 99999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("评论空内容 → 400 + 1001")
    void testComment_BlankContent_Returns1001() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "content", ""
        ));

        mockMvc.perform(post("/api/works/{id}/comments", publishedWorkId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("待审作品（status=0）：作者可见详情；他人 404 + 1004（不泄露存在性）")
    void testGetWork_Unpublished_Returns1004() throws Exception {
        // N1（2026-11-04 先审后发）：status=0 仅作者本人可见
        Work unpublished = new Work();
        unpublished.setUserId(userId);
        unpublished.setTitle("未发布作品");
        unpublished.setMediaType("text");
        unpublished.setVisible(1);
        unpublished.setAnonymous(1);
        unpublished.setLikeCount(0);
        unpublished.setStatus(0); // 待审
        workMapper.insert(unpublished);

        // 作者本人可见（200，VO.status=0 表示审核中）
        mockMvc.perform(get("/api/works/{id}", unpublished.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(0));

        // 他人访问 → 1004
        User other = new User();
        other.setUsername("work_test_other");
        other.setPassword("$2a$10$dummyhash");
        other.setNickname("他人");
        other.setRole(0);
        other.setStatus(1);
        other.setCanContact(0);
        other.setPrivacyAgreed(1);
        userMapper.insert(other);
        String otherToken = jwtUtil.createToken(other.getId(), 0);
        mockMvc.perform(get("/api/works/{id}", unpublished.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
