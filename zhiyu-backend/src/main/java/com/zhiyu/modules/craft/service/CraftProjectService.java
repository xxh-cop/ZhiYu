package com.zhiyu.modules.craft.service;

import com.zhiyu.modules.craft.vo.CraftProjectStepsVO;

/**
 * 手作项目服务（T1.9b-backend）。
 *
 * <p>提供项目步骤查询等能力。
 * T1.5/T1.6 的推荐和会话逻辑分别在 {@link CraftRecommendService} 和 {@link CraftSessionService} 中，
 * 本接口只承载项目维度的查询。
 */
public interface CraftProjectService {

    /**
     * 查询项目步骤的结构化视图（2026-11-05 分层下架）。
     *
     * <p>上架项目：正常下发步骤。已下架/已归档项目：不再 1004，返回
     * 步骤（可回看）+ 会话状态 + readOnly/canExport/canReview + exportUntil/archiveAt
     * + noticeKey/notice，供前端条件渲染。
     *
     * @param projectId 项目 ID
     * @return 结构化步骤视图
     * @throws com.zhiyu.common.exception.BusinessException 项目不存在或待审时抛 1004（不泄露存在性）
     */
    CraftProjectStepsVO listSteps(Long projectId);
}
