package com.zhiyu.modules.emotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.modules.emotion.entity.EmotionNote;
import com.zhiyu.modules.emotion.mapper.EmotionNoteMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link com.zhiyu.modules.emotion.controller.EmotionNoteController} 集成测试（M3-A1）。
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>创建便签（含加密落库验证）</li>
 *   <li>创建便签 content 为空 → 1001</li>
 *   <li>未登录 → 401</li>
 *   <li>列表（仅返回本人便签）</li>
 *   <li>查别人的便签 → 1003</li>
 *   <li>查不存在的便签 → 1004</li>
 *   <li>编辑自己的便签</li>
 *   <li>编辑别人的便签 → 1003</li>
 *   <li>删除自己的便签</li>
 *   <li>删除别人的便签 → 1003</li>
 *   <li>加密验证：详情接口返回已解密明文</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmotionNoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AesEncryptor encryptor;

    @Autowired
    private EmotionNoteMapper noteMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token(Long userId, int role) {
        return jwtUtil.createToken(userId, role);
    }

    private Long createNote(Long userId, String mood, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "mood", mood,
                "content", content
        ));
        MvcResult result = mockMvc.perform(post("/api/emotion/notes")
                        .header("Authorization", "Bearer " + token(userId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Map<?, ?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<?, ?> data = (Map<?, ?>) resp.get("data");
        Object idVal = data.get("id");
        if (idVal instanceof Number) {
            return ((Number) idVal).longValue();
        }
        return Long.parseLong(String.valueOf(idVal));
    }

    // ── 创建 ──

    @Test
    @DisplayName("创建便签 → 200 + 返回 id + content 加密落库")
    void testCreateNote_Encrypted() throws Exception {
        Long userId = 9101L;
        String content = "今天做了扎染，心情平静了很多";
        Long noteId = createNote(userId, "平静", content);

        assertNotNull(noteId);

        // 验证 DB 中 content 是密文
        String rawContent = jdbcTemplate.queryForObject(
                "SELECT content FROM emotion_note WHERE id = ?",
                String.class, noteId);
        assertNotNull(rawContent);
        assertNotEquals(content, rawContent, "存储的应该是密文");
        assertEquals(content, encryptor.decrypt(rawContent), "解密后应等于明文");
    }

    @Test
    @DisplayName("创建便签 content 为空 → 400 + code 1001")
    void testCreateNote_EmptyContent() throws Exception {
        mockMvc.perform(post("/api/emotion/notes")
                        .header("Authorization", "Bearer " + token(9102L, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mood\":\"焦虑\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("未登录 → 401")
    void testNoToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/emotion/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 列表 ──

    @Test
    @DisplayName("列表仅返回本人便签")
    void testListOnlyOwnNotes() throws Exception {
        createNote(9103L, "开心", "用户A的便签");
        createNote(9104L, "焦虑", "用户B的便签");
        createNote(9103L, "平静", "用户A的第二条");

        mockMvc.perform(get("/api/emotion/notes")
                        .header("Authorization", "Bearer " + token(9103L, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("列表 excerpt 为解密后前 30 字")
    void testListExcerpt() throws Exception {
        String longContent = "这是一段很长很长很长很长很长很长很长很长很长很长很长很长很长很长的便签内容";
        createNote(9105L, "焦虑", longContent);

        mockMvc.perform(get("/api/emotion/notes")
                        .header("Authorization", "Bearer " + token(9105L, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].excerpt").value(
                        org.hamcrest.Matchers.hasLength(30)));
    }

    // ── 详情 ──

    @Test
    @DisplayName("查详情返回解密后明文")
    void testDetailDecrypted() throws Exception {
        String content = "做了刺绣书签，很专注";
        Long noteId = createNote(9106L, "专注", content);

        mockMvc.perform(get("/api/emotion/notes/" + noteId)
                        .header("Authorization", "Bearer " + token(9106L, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value(content));
    }

    @Test
    @DisplayName("查别人的便签 → 403 + code 1003")
    void testDetailOtherUser_Forbidden() throws Exception {
        Long noteId = createNote(9107L, "焦虑", "我的私密便签");

        mockMvc.perform(get("/api/emotion/notes/" + noteId)
                        .header("Authorization", "Bearer " + token(9999L, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("查不存在的便签 → 404 + code 1004")
    void testDetailNotFound() throws Exception {
        mockMvc.perform(get("/api/emotion/notes/999999999")
                        .header("Authorization", "Bearer " + token(9108L, 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    // ── 编辑 ──

    @Test
    @DisplayName("编辑自己的便签 → 200")
    void testUpdateOwnNote() throws Exception {
        Long noteId = createNote(9109L, "焦虑", "原来的内容");
        String body = "{\"content\":\"修改后的内容\"}";

        mockMvc.perform(put("/api/emotion/notes/" + noteId)
                        .header("Authorization", "Bearer " + token(9109L, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证内容已更新且加密
        EmotionNote note = noteMapper.selectById(noteId);
        assertEquals("修改后的内容", encryptor.decrypt(note.getContent()));
    }

    @Test
    @DisplayName("编辑别人的便签 → 403 + code 1003")
    void testUpdateOtherUser_Forbidden() throws Exception {
        Long noteId = createNote(9110L, "焦虑", "用户A的便签");
        String body = "{\"content\":\"试图修改别人的\"}";

        mockMvc.perform(put("/api/emotion/notes/" + noteId)
                        .header("Authorization", "Bearer " + token(9998L, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ── 删除 ──

    @Test
    @DisplayName("删除自己的便签 → 200")
    void testDeleteOwnNote() throws Exception {
        Long noteId = createNote(9111L, "开心", "要被删除的便签");

        mockMvc.perform(delete("/api/emotion/notes/" + noteId)
                        .header("Authorization", "Bearer " + token(9111L, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 逻辑删除后 selectById 应返回 null
        assertNull(noteMapper.selectById(noteId));
    }

    @Test
    @DisplayName("删除别人的便签 → 403 + code 1003")
    void testDeleteOtherUser_Forbidden() throws Exception {
        Long noteId = createNote(9112L, "焦虑", "用户A的便签");

        mockMvc.perform(delete("/api/emotion/notes/" + noteId)
                        .header("Authorization", "Bearer " + token(9997L, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }
}
