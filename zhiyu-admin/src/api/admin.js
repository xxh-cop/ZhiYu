import http from '../utils/request'

/**
 * 管理端 API 封装（M2 批 4 第二批）。
 *
 * 对应后端 com.zhiyu.modules.admin.controller.AdminController 的 4 个接口。
 * http 拦截器已统一处理 code=0 返回 data、401/403 跳转、错误码 ElMessage。
 */

/**
 * 获取待审核内容队列。
 * @param {{ type?: string, page?: number }} params
 * @returns {Promise<{ total: number, pageNum: number, pageSize: number, records: Array }>}
 */
export function getAuditQueue(params = {}) {
  return http.get('/api/admin/audit-queue', { params })
}

/**
 * 审核手作项目（通过/驳回）。仅管理员可调用。
 * @param {number|string} id 项目 ID
 * @param {{ action: 'approve'|'reject', reason?: string }} data
 * @returns {Promise<object>} 更新后的项目 VO
 */
export function auditProject(id, data) {
  return http.put(`/api/admin/projects/${id}/audit`, data)
}

/**
 * 获取脱敏关怀提醒列表。
 * @param {{ status?: number, page?: number }} params
 * @returns {Promise<{ total: number, pageNum: number, pageSize: number, records: Array }>}
 */
export function getCareReminders(params = {}) {
  return http.get('/api/admin/care-reminders', { params })
}

/**
 * 获取数据看板统计指标。
 * @returns {Promise<object>} DashboardStatsVO
 */
export function getDashboardStats() {
  return http.get('/api/admin/dashboard/stats')
}
