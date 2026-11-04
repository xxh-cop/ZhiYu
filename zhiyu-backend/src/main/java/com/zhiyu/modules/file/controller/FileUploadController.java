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
import java.nio.file.StandardCopyOption;
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

    private final com.zhiyu.modules.file.MediaService media;
    private final com.zhiyu.security.BusinessAccess access;

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
    @PostMapping("/upload/private-image")
    public Result<java.util.Map<String,String>> privateImage(@RequestParam("file") MultipartFile file){return Result.success(java.util.Map.of("url",media.uploadImage(access.consentedUser(),file)));}

    @PostMapping("/upload/image")
    public Result<java.util.Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        // S1（2026-11-04）：媒体上传落库个人文件，挂隐私同意门
        access.consentedUser();
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
        return Result.success(java.util.Map.of("url",media.upload(access.consentedUser(),file)));
    }

    /**
     * AU3（2026-11-04）：上传步骤引导音频（MP3/WAV/M4A/OGG，≤10MB，每日 20 次）。
     * 仅传承人（role=1）与管理员（role=3）；走私有媒体机制，返回 {@code /api/media/{id}/content} 引用，
     * 供 craft_step.audio_url 使用；学生侧经授权播放（签名地址）收听。
     */
    @PostMapping("/upload/audio")
    public Result<java.util.Map<String, String>> uploadAudio(@RequestParam("file") MultipartFile file) {
        return Result.success(java.util.Map.of("url", media.uploadAudio(access.consentedUser(), file)));
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

        // 构建存储路径：{uploadDir}/{subDir}/yyyy/MM/dd/{uuid}.{ext}
        // 必须转为绝对路径，否则 MultipartFile.transferTo() 会相对于 Tomcat work 目录解析
        String datePath = LocalDate.now().format(DATE_FMT);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
        String relativePath = subDir + "/" + datePath + "/" + fileName;

        Path targetFile = Paths.get(uploadDir, subDir, datePath, fileName)
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(targetFile.getParent());
            try(var input=file.getInputStream()){Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);}
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "文件保存失败");
        }

        // 返回访问 URL：{accessPrefix}/{subDir}/yyyy/MM/dd/{fileName}
        String url = accessPrefix + "/" + relativePath;
        log.info("文件上传成功: type={}, size={}, url={}, absPath={}", subDir, file.getSize(), url, targetFile);

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
