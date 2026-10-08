package com.zhiyu.modules.file.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器（批次 2）。
 *
 * <p>提供图片和视频的通用上传接口，文件存储在本地文件系统。
 *
 * <p>POST /api/files/upload/image —— 上传图片（jpg/jpeg/png/webp，≤5MB）
 * <br>POST /api/files/upload/video —— 上传视频（mp4/mov，≤50MB）
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    @Value("${zhiyu.file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${zhiyu.file.access-prefix:/uploads}")
    private String accessPrefix;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "webp"));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(
            Arrays.asList("mp4", "mov"));
    private static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;       // 5MB
    private static final long VIDEO_MAX_SIZE = 50L * 1024 * 1024;      // 50MB

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 上传图片。
     *
     * @param file 图片文件（jpg/jpeg/png/webp，≤5MB）
     * @return { url: "/uploads/images/yyyy/MM/dd/xxx.jpg" }
     */
    @PostMapping("/upload/image")
    public Result<java.util.Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        return doUpload(file, "images", IMAGE_EXTENSIONS, IMAGE_MAX_SIZE);
    }

    /**
     * 上传视频。
     *
     * @param file 视频文件（mp4/mov，≤50MB）
     * @return { url: "/uploads/videos/yyyy/MM/dd/xxx.mp4" }
     */
    @PostMapping("/upload/video")
    public Result<java.util.Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        return doUpload(file, "videos", VIDEO_EXTENSIONS, VIDEO_MAX_SIZE);
    }

    // ──────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────

    private Result<java.util.Map<String, String>> doUpload(MultipartFile file,
                                                            String subDir,
                                                            Set<String> allowedExts,
                                                            long maxSize) {
        if (file == null || file.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR.getCode(), "文件不能为空");
        }

        // 大小校验
        if (file.getSize() > maxSize) {
            long maxMB = maxSize / (1024 * 1024);
            return Result.error(ResultCode.PARAM_ERROR.getCode(), "文件大小超过限制（最大 " + maxMB + "MB）");
        }

        // 扩展名校验
        String originalFilename = file.getOriginalFilename();
        String ext = getExtension(originalFilename);
        if (ext == null || !allowedExts.contains(ext.toLowerCase())) {
            return Result.error(ResultCode.PARAM_ERROR.getCode(), "不支持的文件类型，允许：" + String.join("/", allowedExts));
        }

        // 构建存储路径：./uploads/{subDir}/yyyy/MM/dd/{uuid}.{ext}
        String datePath = LocalDate.now().format(DATE_FMT);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
        String relativePath = subDir + "/" + datePath + "/" + fileName;

        Path targetDir = Paths.get(uploadDir, subDir, datePath);
        Path targetFile = targetDir.resolve(fileName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetFile.toFile());
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "文件保存失败");
        }

        // 返回访问 URL：{accessPrefix}/{subDir}/yyyy/MM/dd/{fileName}
        String url = accessPrefix + "/" + relativePath;
        log.info("文件上传成功: type={}, size={}, url={}", subDir, file.getSize(), url);

        return Result.success(java.util.Map.of("url", url));
    }

    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) {
            return null;
        }
        return filename.substring(dotIdx + 1).toLowerCase();
    }
}
