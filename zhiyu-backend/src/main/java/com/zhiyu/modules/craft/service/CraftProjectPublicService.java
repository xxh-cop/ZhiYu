package com.zhiyu.modules.craft.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.craft.vo.CraftProjectDetailVO;
import com.zhiyu.modules.craft.vo.CraftProjectItemVO;

/**
 * 手作项目公开查询 Service（M2 批 6A）。
 *
 * <p>学生端可见的项目查询，只返回 {@code status=1}（已上架）的项目。
 */
public interface CraftProjectPublicService {

    /**
     * 分页查询传承人的已上架项目。
     *
     * @param inheritorProfileId inheritor_profile.id
     * @param page               页码（从 1 开始）
     * @param pageSize           每页大小
     * @return 分页结果
     */
    PageVO<CraftProjectItemVO> listProjectsByInheritor(Long inheritorProfileId, int page, int pageSize);

    /**
     * 查询已上架项目详情（含步骤 + 材料 + 传承人信息）。
     *
     * @param projectId craft_project.id
     * @return 项目详情
     */
    CraftProjectDetailVO getProjectDetail(Long projectId);
}
