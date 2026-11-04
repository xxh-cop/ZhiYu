package com.zhiyu.modules.admin.service;

import com.zhiyu.common.dto.PageVO;
import com.zhiyu.modules.admin.dto.AuditQueueItemVO;
import com.zhiyu.modules.admin.dto.AuditRequest;
import com.zhiyu.modules.admin.dto.CareReminderVO;
import com.zhiyu.modules.admin.dto.DashboardStatsVO;

/**
 * 管理端服务接口（M2 批 4）。
 */
public interface AdminService {

    /**
     * 获取待审核内容队列。
     *
     * @param type 内容类型（project/work/comment，第一版只实现 project）
     * @param page 页码（从 1 开始）
     * @return 待审项目分页结果
     */
    PageVO<AuditQueueItemVO> getAuditQueue(String type, Integer page);

    /**
     * 审核手作项目（通过/驳回）。
     *
     * @param projectId 项目 ID
     * @param req       审核请求
     * @return 更新后的项目 VO
     */
    AuditQueueItemVO auditProject(Long projectId, AuditRequest req);

    /**
     * 获取脱敏关怀提醒列表。
     *
     * @param status 处理状态（0 待处理 / 1 已跟进，null 查全部）
     * @param page   页码（从 1 开始）
     * @return 关怀提醒分页结果
     */
    PageVO<CareReminderVO> getCareReminders(Integer status, Integer page);

    /**
     * 获取数据看板统计指标。
     *
     * @return 看板聚合数据
     */
    DashboardStatsVO getDashboardStats();

    /**
     * 下架已上架项目（AY1，2026-11-05）：status 1→2（DDL 既有「下架」口径），
     * 理由必填并落 {@code craft_project.audit_reason}，落 admin_operation_log，通知传承人。
     * 仅 role=3（与项目审核权限一致）；非上架状态 → 3001；不存在 → 1004。
     */
    void takedownProject(Long projectId, String reason);

    /**
     * 项目问责详情（BC1，2026-11-05）：含 auditReason 与动作来源推导
     * （lastAction 取 admin_operation_log 最近一条 project.*；statusOrigin 区分评审驳回/事后下架）。
     * 仅 role=3；不存在或已删除 → 1004。
     */
    java.util.Map<String, Object> projectDetail(Long projectId);

    /**
     * 逻辑删除项目（AY2，2026-11-05）：deleted=1 + 理由落 audit_reason，
     * 级联逻辑删除步骤/材料，退役不再被引用的私有媒体（复用 AU3 退役机制），
     * 落 admin_operation_log，通知传承人。仅 role=3；不存在 → 1004。
     */
    void deleteProject(Long projectId, String reason);
}
