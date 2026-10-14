package com.zhiyu.modules.file.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FileUploadController 测试（批次 2）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>上传图片成功 / 过大 / 类型不符</li>
 *   <li>上传视频成功 / 过大 / 类型不符</li>
 *   <li>未登录 → 401</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate db;
    @org.springframework.boot.test.mock.mockito.MockBean private com.zhiyu.modules.file.MediaStorage storage;
    private String token;

    @BeforeEach
    void setUp() {
        db.update("MERGE INTO user(id,username,password,nickname,role,status,deleted) KEY(id) VALUES(8401,'upload_test','test','上传测试',0,1,0)");
        db.update("DELETE FROM media_asset WHERE user_id=8401");
        token = jwtUtil.createToken(8401L, 0);
    }

    // ── 图片上传 ──

    @Test
    @DisplayName("上传图片成功")
    void testUploadImage_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]);

        MvcResult result = mockMvc.perform(multipart("/api/files/upload/image")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String url = objectMapper.readTree(responseBody).at("/data/url").asText();
        assertTrue(url.startsWith("/uploads/images/"));
        assertTrue(url.endsWith(".jpg"));
    }

    @Test
    @DisplayName("上传图片过大 → 1001")
    void testUploadImage_TooLarge() throws Exception {
        // 6MB > 5MB
        byte[] oversized = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", MediaType.IMAGE_JPEG_VALUE, oversized);

        mockMvc.perform(multipart("/api/files/upload/image")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("上传图片类型不符 → 1001")
    void testUploadImage_WrongType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.gif", MediaType.IMAGE_GIF_VALUE, new byte[1024]);

        mockMvc.perform(multipart("/api/files/upload/image")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ── 视频上传 ──

    @Test
    @DisplayName("上传视频成功")
    void testUploadVideo_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", validHeader());

        MvcResult result = mockMvc.perform(multipart("/api/files/upload/video")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String url = objectMapper.readTree(responseBody).at("/data/url").asText();
        assertTrue(url.startsWith("/api/media/"));
        assertTrue(url.endsWith("/content"));
    }

    @Test
    @DisplayName("上传视频过大 → 1001")
    void testUploadVideo_TooLarge() throws Exception {
        // 51MB > 50MB
        byte[] oversized = new byte[51 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.mp4", "video/mp4", oversized);

        mockMvc.perform(multipart("/api/files/upload/video")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("上传视频类型不符 → 1001")
    void testUploadVideo_WrongType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.avi", "video/x-msvideo", new byte[1024]);

        mockMvc.perform(multipart("/api/files/upload/video")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    private byte[] validHeader(){byte[] value=new byte[64];value[3]=24;System.arraycopy("ftypisom".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,value,4,8);return value;}

    @Test void fakeVideoHeaderRejected()throws Exception{mockMvc.perform(multipart("/api/files/upload/video").file(new MockMultipartFile("file","fake.mp4","video/mp4",new byte[64])).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));}

    // ── 鉴权 ──

    @Test
    @DisplayName("未登录上传图片 → 401")
    void testUploadImage_Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[1024]);

        mockMvc.perform(multipart("/api/files/upload/image")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }
}
