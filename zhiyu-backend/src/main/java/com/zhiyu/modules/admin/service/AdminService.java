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
}
