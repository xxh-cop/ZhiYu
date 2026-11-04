import { requestClient } from '#/api/request';

/**
 * 导出审计登记（2026-11-04 N2）。
 * 契约：POST /api/admin/export-logs → data { batchNo, fileName, alert }。
 * - purpose 必填（审计追溯）；评论导出服务端一律 3001（不开放）；
 * - 字段黑名单（密码/密钥/密文等）服务端拒收；
 * - recordCount 超阈值时 alert=true 并落库告警。
 */
export namespace AdminExportApi {
  export type TargetType = 'comments' | 'users' | 'works';

  export interface RegisterResult {
    alert: boolean;
    batchNo: string;
    fileName: string;
  }
}

export async function registerExportLog(params: {
  targetType: AdminExportApi.TargetType;
  filterSummary?: string;
  fieldList: string;
  recordCount: number;
  purpose: string;
}) {
  return requestClient.post<AdminExportApi.RegisterResult>('/admin/export-logs', params);
}
