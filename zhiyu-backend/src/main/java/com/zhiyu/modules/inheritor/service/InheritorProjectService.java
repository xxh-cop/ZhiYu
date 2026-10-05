package com.zhiyu.modules.inheritor.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.inheritor.dto.MaterialCreateDTO;
import com.zhiyu.modules.inheritor.dto.ProjectCreateDTO;
import com.zhiyu.modules.inheritor.dto.ProjectUpdateDTO;
import com.zhiyu.modules.inheritor.dto.StepCreateDTO;
import com.zhiyu.modules.inheritor.vo.InheritorProjectDetailVO;
import com.zhiyu.modules.inheritor.vo.InheritorProjectListItemVO;

/**
 * 传承人项目服务（M2 批 5A）。
 */
public interface InheritorProjectService {

    Long createProject(Long userId, ProjectCreateDTO dto);

    PageVO<InheritorProjectListItemVO> listProjects(Long userId, Integer page, Integer pageSize, Integer status);

    InheritorProjectDetailVO getProjectDetail(Long userId, Long projectId);

    void updateProject(Long userId, Long projectId, ProjectUpdateDTO dto);

    void deleteProject(Long userId, Long projectId);

    Long addStep(Long userId, Long projectId, StepCreateDTO dto);

    void updateStep(Long userId, Long projectId, Long stepId, StepCreateDTO dto);

    void deleteStep(Long userId, Long projectId, Long stepId);

    Long addMaterial(Long userId, Long projectId, MaterialCreateDTO dto);

    void deleteMaterial(Long userId, Long projectId, Long materialId);
}
