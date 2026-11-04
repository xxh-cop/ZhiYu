import { requestClient } from '#/api/request';

/**
 * 危机路径留存（AN 2026-11-04 落地；AS 适配 AQ 契约变更 2026-11-04）。
 *
 * 契约口径（openapi: adminCrisisLogsGet / adminCrisisLogsByIdGet，AQ1 起统一 camelCase）：
 * - 仅 role ∈ {2,3}；咨询师仅见本人认领 care_case 关联用户的记录；
 * - 列表只出元数据、不含摘要；详情返回服务端解密后的脱敏摘要（不含对话原文）；
 * - 详情自 AQ1 起也带 userId（雪花字符串；后端刻意不下发昵称，最小权限——
 *   关怀角色按个案归属定位用户已足够，前端不得拼接/伪造昵称）；
 * - 每次查看详情写 admin_operation_log（crisis_log.view）——前端必须在用户明确确认后再请求，
 *   不做预取、不做批量查看，避免无意留痕；
 * - 越权与不存在统一 1004（不泄露存在性）。
 *
 * riskLevel（AQ2 起真实分级）：1 关注 / 2 高危 / 3 紧急（由词表分组得出，不再恒定 2）。
 */
export namespace CrisisLogApi {
  /** 列表元数据行（AQ1 起 camelCase） */
  export interface CrisisLogItem {
    id: string;
    userId: string;
    sessionId?: null | string;
    scene: string;
    riskLevel: number;
    createTime: string;
  }
  /** 详情（summary 为服务端解密后的规则式脱敏摘要） */
  export interface CrisisLogDetail {
    id: string;
    userId: string;
    sessionId: string;
    scene: string;
    riskLevel: number;
    summary: string;
    createTime: string;
  }
}

/** 列表（固定每页 20 条，仅元数据） */
export async function getCrisisLogs(page: number) {
  return requestClient.get<{
    records: CrisisLogApi.CrisisLogItem[];
    total: number;
  }>('/admin/crisis-logs', { params: { page } });
}

/** 详情（每次调用服务端都会落 crisis_log.view 操作审计；勿预取） */
export async function getCrisisLogDetail(id: string) {
  return requestClient.get<CrisisLogApi.CrisisLogDetail>(`/admin/crisis-logs/${id}`);
}
