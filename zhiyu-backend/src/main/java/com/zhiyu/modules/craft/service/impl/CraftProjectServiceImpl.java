package com.zhiyu.modules.craft.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.modules.craft.entity.CraftProject;
import com.zhiyu.modules.craft.entity.CraftStep;
import com.zhiyu.modules.craft.mapper.CraftProjectMapper;
import com.zhiyu.modules.craft.mapper.CraftStepMapper;
import com.zhiyu.modules.craft.service.CraftProjectService;
import com.zhiyu.modules.craft.vo.CraftStepVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 手作项目服务实现（T1.9b-backend）。
 *
 * <p>提供项目步骤查询等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraftProjectServiceImpl implements CraftProjectService {

    private final CraftProjectMapper craftProjectMapper;
    private final CraftStepMapper craftStepMapper;
    private final com.zhiyu.modules.file.MediaService mediaService;

    @Override
    public List<CraftStepVO> listSteps(Long projectId) {
        // 1. 校验项目存在且已上架
        CraftProject project = craftProjectMapper.selectById(projectId);
        if (project == null || project.getStatus() == null || project.getStatus() != 1) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 查步骤（按 stepNo 升序，逻辑删除自动过滤）
        LambdaQueryWrapper<CraftStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CraftStep::getProjectId, projectId)
                .orderByAsc(CraftStep::getStepNo);
        List<CraftStep> steps = craftStepMapper.selectList(wrapper);

        // 3. 转 VO
        return steps.stream().map(this::toVO).collect(Collectors.toList());
    }

    private CraftStepVO toVO(CraftStep step) {
        CraftStepVO vo = new CraftStepVO();
        vo.setId(step.getId());
        vo.setStepNo(step.getStepNo());
        vo.setTitle(step.getTitle());
        vo.setGuide(step.getGuide());
        // AU3：私有媒体引用换 1h 签名播放地址（受众授权）；外链/静态资源原样下发
        vo.setAudioUrl(mediaService.playbackIfMediaRef(
                com.zhiyu.security.UserContext.getUserId(), step.getAudioUrl()));
        return vo;
    }
}
