package com.zhiyu.modules.inheritor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.entity.CraftMaterial;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftMaterialMapper;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.inheritor.dto.MaterialCreateDTO;
import com.zhiyu.modules.inheritor.dto.ProjectCreateDTO;
import com.zhiyu.modules.inheritor.dto.ProjectUpdateDTO;
import com.zhiyu.modules.inheritor.dto.StepCreateDTO;
import com.zhiyu.modules.inheritor.service.InheritorProjectService;
import com.zhiyu.modules.inheritor.vo.InheritorProjectDetailVO;
import com.zhiyu.modules.inheritor.vo.InheritorProjectListItemVO;
import com.zhiyu.modules.inheritor.vo.MaterialVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link InheritorProjectService} 实现（M2 批 5A）。
 *
 * <p>craft_project.inheritor_id 指向 user.id（不是 inheritor_profile.id）。
 * 从 {@link com.zhiyu.security.UserContext#getUserId()} 直接取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InheritorProjectServiceImpl implements InheritorProjectService {

    private final CraftProjectMapper craftProjectMapper;
    private final com.zhiyu.modules.file.MediaService media;
    private final CraftStepMapper craftStepMapper;
    private final CraftMaterialMapper craftMaterialMapper;

    // ── 项目 CRUD ──

    @Override
    public Long createProject(Long userId, ProjectCreateDTO dto) {
        CraftProject project = new CraftProject();
        project.setInheritorId(userId);
        project.setTitle(dto.getTitle());
        project.setIntro(dto.getIntro());
        project.setCultureBg(dto.getCultureBg());
        project.setLevel(dto.getLevel());
        project.setEstMinutes(dto.getEstMinutes());
        project.setCategory(dto.getCategory() != null ? dto.getCategory() : "");
        project.setTherapyTags(dto.getTherapyTags());
        project.setCoverUrl(dto.getCoverUrl());
        if(dto.getVideoUrl()!=null && !dto.getVideoUrl().isBlank())project.setVideoUrl(media.requireOwned(userId,dto.getVideoUrl()));
        project.setStatus(0); // 待审
        craftProjectMapper.insert(project);
        log.info("传承人创建项目: projectId={}, inheritorId={}", project.getId(), userId);
        return project.getId();
    }

    @Override
    public PageVO<InheritorProjectListItemVO> listProjects(Long userId, Integer page, Integer pageSize, Integer status) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);

        LambdaQueryWrapper<CraftProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftProject::getInheritorId, userId);
        if (status != null) {
            wrapper.eq(CraftProject::getStatus, status);
        }
        wrapper.orderByDesc(CraftProject::getCreateTime);

        IPage<CraftProject> result = craftProjectMapper.selectPage(new Page<>(pageNum, size), wrapper);

        List<InheritorProjectListItemVO> records = result.getRecords().stream()
                .map(p -> {
                    InheritorProjectListItemVO vo = new InheritorProjectListItemVO();
                    vo.setId(p.getId());
                    vo.setTitle(p.getTitle());
                    vo.setCoverUrl(p.getCoverUrl());
                    vo.setCategory(p.getCategory());
                    vo.setStatus(p.getStatus());
                    vo.setCreateTime(p.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), pageNum, size, records);
    }

    @Override
    public InheritorProjectDetailVO getProjectDetail(Long userId, Long projectId) {
        CraftProject project = checkOwnership(userId, projectId);
        // AY1（2026-11-05）：已下架（status=2）项目内容对传承人也不可见——1004 不泄露存在性；
        // 下架理由经 WORK_AUDIT 通知送达，列表仍可见状态标记。
        if (project.getStatus() != null && project.getStatus() == 2) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        InheritorProjectDetailVO vo = new InheritorProjectDetailVO();
        vo.setId(project.getId());
        vo.setInheritorId(project.getInheritorId());
        vo.setTitle(project.getTitle());
        vo.setIntro(project.getIntro());
        vo.setCultureBg(project.getCultureBg());
        vo.setLevel(project.getLevel());
        vo.setEstMinutes(project.getEstMinutes());
        vo.setCategory(project.getCategory());
        vo.setTherapyTags(project.getTherapyTags());
        vo.setCoverUrl(project.getCoverUrl());
        vo.setVideoUrl(project.getVideoUrl());
        vo.setStatus(project.getStatus());
        vo.setCreateTime(project.getCreateTime());

        // 查步骤
        List<CraftStep> steps = craftStepMapper.selectList(
                new LambdaQueryWrapper<CraftStep>()
                        .eq(CraftStep::getProjectId, projectId)
                        .orderByAsc(CraftStep::getStepNo));
        vo.setSteps(steps.stream().map(s -> {
            InheritorProjectDetailVO.StepVO svo = new InheritorProjectDetailVO.StepVO();
            svo.setId(s.getId());
            svo.setStepNo(s.getStepNo());
            svo.setTitle(s.getTitle());
            svo.setGuide(s.getGuide());
            // AU3：私有媒体引用换签名播放地址（本人上传，owner 授权分支放行）
            svo.setAudioUrl(media.playbackIfMediaRef(userId, s.getAudioUrl()));
            return svo;
        }).collect(Collectors.toList()));

        // 查材料
        List<CraftMaterial> materials = craftMaterialMapper.selectList(
                new LambdaQueryWrapper<CraftMaterial>()
                        .eq(CraftMaterial::getProjectId, projectId));
        vo.setMaterials(materials.stream().map(m -> {
            MaterialVO mvo = new MaterialVO();
            mvo.setId(m.getId());
            mvo.setName(m.getName());
            mvo.setQty(m.getQty());
            return mvo;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    public void updateProject(Long userId, Long projectId, ProjectUpdateDTO dto) {
        CraftProject project = checkOwnershipAndStatus(userId, projectId);

        if (dto.getTitle() != null) project.setTitle(dto.getTitle());
        if (dto.getIntro() != null) project.setIntro(dto.getIntro());
        if (dto.getCultureBg() != null) project.setCultureBg(dto.getCultureBg());
        if (dto.getLevel() != null) project.setLevel(dto.getLevel());
        if (dto.getEstMinutes() != null) project.setEstMinutes(dto.getEstMinutes());
        if (dto.getCategory() != null) project.setCategory(dto.getCategory());
        if (dto.getTherapyTags() != null) project.setTherapyTags(dto.getTherapyTags());
        if (dto.getCoverUrl() != null) project.setCoverUrl(dto.getCoverUrl());

        if(dto.getVideoUrl()!=null)project.setVideoUrl(dto.getVideoUrl().isBlank()?"":media.requireOwned(userId,dto.getVideoUrl()));
        craftProjectMapper.updateById(project);
        log.info("传承人编辑项目: projectId={}, inheritorId={}", projectId, userId);
    }

    @Override
    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        CraftProject project = checkOwnershipAndStatus(userId, projectId);

        // 逻辑删除项目
        craftProjectMapper.deleteById(projectId);

        // 逻辑删除关联步骤
        List<CraftStep> steps = craftStepMapper.selectList(
                new LambdaQueryWrapper<CraftStep>().eq(CraftStep::getProjectId, projectId));
        List<String> stepAudios = steps.stream().map(CraftStep::getAudioUrl)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
        for (CraftStep step : steps) {
            craftStepMapper.deleteById(step.getId());
        }
        // AU3：项目删除后退役不再被引用的步骤音频（须在全部步骤删除后执行，避免同项目共享引用误判）
        for (String audio : stepAudios) {
            media.retireOrphanedMedia(audio);
        }

        // 逻辑删除关联材料
        List<CraftMaterial> materials = craftMaterialMapper.selectList(
                new LambdaQueryWrapper<CraftMaterial>().eq(CraftMaterial::getProjectId, projectId));
        for (CraftMaterial material : materials) {
            craftMaterialMapper.deleteById(material.getId());
        }

        log.info("传承人删除项目: projectId={}, inheritorId={}", projectId, userId);
    }

    // ── 步骤 CRUD ──

    /**
     * AU3（2026-11-04）：步骤音频引用校验。允许：null/空白（不下发播放入口）、
     * 本站私有媒体引用（须为本人上传，防横向引用他人音频）、http(s) 外链（运营自管资源）、
     * /static/ 前端包静态资源（存量种子数据约定）。显式拒绝 /uploads/ 公开路径（§5.1 红线）。
     */
    private String validateStepAudio(Long userId, String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return null;
        }
        String ref = audioUrl.trim();
        if (ref.startsWith("/uploads/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "步骤音频请通过音频上传接口获取私有媒体地址，不支持公开 uploads 路径");
        }
        if (ref.startsWith("/api/media/")) {
            return media.requireOwned(userId, ref);
        }
        if (ref.matches("(?i)^https?://.*") || ref.startsWith("/static/")) {
            return ref;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "音频地址格式不支持：请上传音频或使用 http(s) 外链");
    }

    @Override
    public Long addStep(Long userId, Long projectId, StepCreateDTO dto) {
        checkOwnershipAndStatus(userId, projectId);

        CraftStep step = new CraftStep();
        step.setProjectId(projectId);
        step.setStepNo(dto.getStepNo());
        step.setTitle(dto.getTitle());
        step.setGuide(dto.getGuide());
        step.setAudioUrl(validateStepAudio(userId, dto.getAudioUrl()));
        craftStepMapper.insert(step);
        log.info("传承人添加步骤: stepId={}, projectId={}", step.getId(), projectId);
        return step.getId();
    }

    @Override
    public void updateStep(Long userId, Long projectId, Long stepId, StepCreateDTO dto) {
        checkOwnershipAndStatus(userId, projectId);

        CraftStep step = craftStepMapper.selectById(stepId);
        if (step == null || !step.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        step.setStepNo(dto.getStepNo());
        step.setTitle(dto.getTitle());
        if (dto.getGuide() != null) step.setGuide(dto.getGuide());
        String oldAudio = step.getAudioUrl();
        if (dto.getAudioUrl() != null) step.setAudioUrl(validateStepAudio(userId, dto.getAudioUrl()));
        craftStepMapper.updateById(step);
        // AU3：替换旧音频不留孤儿文件——旧引用不再被任何步骤使用时退役（逻辑注销 + 登记清理）
        if (dto.getAudioUrl() != null && !java.util.Objects.equals(oldAudio, step.getAudioUrl())) {
            media.retireOrphanedMedia(oldAudio);
        }
        log.info("传承人编辑步骤: stepId={}, projectId={}", stepId, projectId);
    }

    @Override
    public void deleteStep(Long userId, Long projectId, Long stepId) {
        checkOwnershipAndStatus(userId, projectId);

        CraftStep step = craftStepMapper.selectById(stepId);
        if (step == null || !step.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        craftStepMapper.deleteById(stepId);
        // AU3：删除步骤后退役孤儿音频（仍被其他步骤引用时内部自动跳过）
        media.retireOrphanedMedia(step.getAudioUrl());
        log.info("传承人删除步骤: stepId={}, projectId={}", stepId, projectId);
    }

    // ── 材料 CRUD ──

    @Override
    public Long addMaterial(Long userId, Long projectId, MaterialCreateDTO dto) {
        checkOwnershipAndStatus(userId, projectId);

        CraftMaterial material = new CraftMaterial();
        material.setProjectId(projectId);
        material.setName(dto.getName());
        material.setQty(dto.getQty());
        craftMaterialMapper.insert(material);
        log.info("传承人添加材料: materialId={}, projectId={}", material.getId(), projectId);
        return material.getId();
    }

    @Override
    public void deleteMaterial(Long userId, Long projectId, Long materialId) {
        checkOwnershipAndStatus(userId, projectId);

        CraftMaterial material = craftMaterialMapper.selectById(materialId);
        if (material == null || !material.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        craftMaterialMapper.deleteById(materialId);
        log.info("传承人删除材料: materialId={}, projectId={}", materialId, projectId);
    }

    // ── 私有校验方法 ──

    /**
     * 校验项目存在 + 属于当前用户。
     */
    private CraftProject checkOwnership(Long userId, Long projectId) {
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!project.getInheritorId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权操作该项目");
        }
        return project;
    }

    /**
     * 校验项目存在 + 属于当前用户 + 状态为待审（status=0）。
     */
    private CraftProject checkOwnershipAndStatus(Long userId, Long projectId) {
        CraftProject project = checkOwnership(userId, projectId);
        if (project.getStatus() == null || project.getStatus() != 0) {
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "仅待审项目可编辑");
        }
        return project;
    }
}
