package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.craft.vo.CraftStepVO;

import java.util.List;

/**
 * 手作项目服务（T1.9b-backend）。
 *
 * <p>提供项目步骤查询等能力。
 * T1.5/T1.6 的推荐和会话逻辑分别在 {@link CraftRecommendService} 和 {@link CraftSessionService} 中，
 * 本接口只承载项目维度的查询。
 */
public interface CraftProjectService {

    /**
     * 查询项目的步骤列表（按 stepNo 升序）。
     *
     * @param projectId 项目 ID
     * @return 步骤列表
     * @throws com.zhiyu.common.exception.BusinessException 项目不存在或未上架时抛 1004
     */
    List<CraftStepVO> listSteps(Long projectId);
}
