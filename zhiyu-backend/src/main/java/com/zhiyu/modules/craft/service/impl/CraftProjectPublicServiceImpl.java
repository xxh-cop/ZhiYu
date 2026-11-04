package com.zhiyu.modules.craft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.common.dto.PageVO;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.craft.entity.CraftMaterial;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftMaterialMapper;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftProjectPublicService;
import com.zhiyu.modules.craft.vo.CraftProjectDetailVO;
import com.zhiyu.modules.craft.vo.CraftProjectItemVO;
import com.zhiyu.modules.craft.vo.CraftStepVO;
import com.zhiyu.modules.inheritor.entity.InheritorProfile;
import com.zhiyu.modules.inheritor.mapper.InheritorProfileMapper;
import com.zhiyu.modules.inheritor.vo.MaterialVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 手作项目公开查询 Service 实现（M2 批 6A）。
 */
@Service
@RequiredArgsConstructor
public class CraftProjectPublicServiceImpl implements CraftProjectPublicService {

    private final CraftProjectMapper craftProjectMapper;
    private final CraftStepMapper craftStepMapper;
    private final CraftMaterialMapper craftMaterialMapper;
    private final InheritorProfileMapper inheritorProfileMapper;
    private final UserMapper userMapper;
    private final com.zhiyu.modules.file.MediaService mediaService;

    @Override
    public PageVO<CraftProjectItemVO> listProjectsByInheritor(Long inheritorProfileId, int page, int pageSize) {
        // 1. 根据 inheritor_profile.id 查 user_id
        InheritorProfile profile = inheritorProfileMapper.selectById(inheritorProfileId);
        if (profile == null || profile.getVerified() == null || profile.getVerified() != 1) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 查该传承人已上架项目
        LambdaQueryWrapper<CraftProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftProject::getInheritorId, profile.getUserId())
                .eq(CraftProject::getStatus, 1)
                .orderByDesc(CraftProject::getCreateTime);

        IPage<CraftProject> pageResult = craftProjectMapper.selectPage(
                new Page<>(page, pageSize), wrapper);

        List<CraftProject> projects = pageResult.getRecords();
        if (projects.isEmpty()) {
            return new PageVO<>(pageResult.getTotal(), page, pageSize, Collections.emptyList());
        }

        // 3. 组装 VO
        List<CraftProjectItemVO> items = projects.stream().map(p -> {
            CraftProjectItemVO vo = new CraftProjectItemVO();
            vo.setId(p.getId());
            vo.setTitle(p.getTitle());
            vo.setIntro(p.getIntro());
            vo.setCoverUrl(p.getCoverUrl());
            vo.setCategory(p.getCategory());
            vo.setLevel(p.getLevel());
            vo.setEstMinutes(p.getEstMinutes());
            vo.setTherapyTags(p.getTherapyTags());
            vo.setCreateTime(p.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return new PageVO<>(pageResult.getTotal(), page, pageSize, items);
    }

    @Override
    public CraftProjectDetailVO getProjectDetail(Long projectId) {
        // 1. 查项目
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null || project.getStatus() == null || project.getStatus() != 1) {
            // 不存在或未上架 → 对学生不可见
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 查传承人信息
        User inheritorUser = userMapper.selectById(project.getInheritorId());
        InheritorProfile profile = null;
        if (inheritorUser != null) {
            LambdaQueryWrapper<InheritorProfile> pw = new LambdaQueryWrapper<>();
            pw.eq(InheritorProfile::getUserId, inheritorUser.getId());
            profile = inheritorProfileMapper.selectOne(pw);
        }

        // 3. 查步骤
        LambdaQueryWrapper<CraftStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(CraftStep::getProjectId, projectId)
                .orderByAsc(CraftStep::getStepNo);
        List<CraftStep> steps = craftStepMapper.selectList(stepWrapper);
        List<CraftStepVO> stepVOs = steps.stream().map(s -> {
            CraftStepVO vo = new CraftStepVO();
            vo.setId(s.getId());
            vo.setStepNo(s.getStepNo());
            vo.setTitle(s.getTitle());
            vo.setGuide(s.getGuide());
            // AU3：私有媒体引用换 1h 签名播放地址（受众授权）；外链/静态资源原样下发
            vo.setAudioUrl(mediaService.playbackIfMediaRef(
                    com.zhiyu.security.UserContext.getUserId(), s.getAudioUrl()));
            return vo;
        }).collect(Collectors.toList());

        // 4. 查材料
        LambdaQueryWrapper<CraftMaterial> matWrapper = new LambdaQueryWrapper<>();
        matWrapper.eq(CraftMaterial::getProjectId, projectId);
        List<CraftMaterial> materials = craftMaterialMapper.selectList(matWrapper);
        List<MaterialVO> materialVOs = materials.stream().map(m -> {
            MaterialVO vo = new MaterialVO();
            vo.setId(m.getId());
            vo.setName(m.getName());
            vo.setQty(m.getQty());
            return vo;
        }).collect(Collectors.toList());

        // 5. 组装详情 VO
        CraftProjectDetailVO vo = new CraftProjectDetailVO();
        vo.setId(project.getId());
        vo.setTitle(project.getTitle());
        vo.setIntro(project.getIntro());
        vo.setCultureBg(project.getCultureBg());
        vo.setLevel(project.getLevel());
        vo.setEstMinutes(project.getEstMinutes());
        vo.setCategory(project.getCategory());
        vo.setTherapyTags(project.getTherapyTags());
        vo.setCoverUrl(project.getCoverUrl());
        vo.setVideoUrl(project.getVideoUrl());
        vo.setCreateTime(project.getCreateTime());
        vo.setSteps(stepVOs);
        vo.setMaterials(materialVOs);

        if (inheritorUser != null) {
            vo.setInheritorId(inheritorUser.getId());
            vo.setInheritorNickname(inheritorUser.getNickname());
        }
        if (profile != null) {
            vo.setInheritorSkill(profile.getSkill());
            vo.setInheritorProfileId(profile.getId());
        }

        return vo;
    }
}
