import { requestClient } from '#/api/request';

/**
 * 软档处置链·抽检池复核（2026-11-07；契约 openapi/openapi.yaml「软档处置链」段，
 * 设计 docs/moderation-soft-tier-disposition-chain-20261107.md）。
 *
 * 权限：仅 role=3（池内含内容原文，咨询师不进池）。
 * 开关：zhiyu.moderation.soft-tier.disposition.enabled 默认 false，
 * 关闭时各端点统一 1001「软档处置链未开启」——页面按「未启用」态呈现，不当故障。
 *
 * 契约现状（2026-11-07 联调对齐，F2/F3 修复）：
 * - 申诉列表有独立端点 GET /api/admin/soft-tier-appeals（含 appealReason 与作者昵称）；
 * - 复核详情直接下发 authorNickname 与 appeal_reason（r.* 全字段，snake_case）；
 * - 看板聚合端点 GET /api/admin/soft-tier-pool/stats 已存在（当日入池/待审/超时/申诉/裁决分布），
 *   页面看板当前仍按状态分查 total（可用该端点收敛，留作后续优化）；
 * - 详情不下发 rid/tid/aid/alid 别名，一律用 r.* 原键（target_id/author_id/audit_log_id）。
 */
export namespace AdminSoftTierApi {
  /** 复核单（列表行）。雪花 id/targetId/authorId 均为字符串，禁止 Number() 转换。 */
  export interface PoolRecord {
    id: string;
    targetType: 'comment' | 'work' | string;
    targetId: string;
    authorId: string;
    sampleLayer: string;
    poolStatus: string;
    /** 队列端点下发；申诉列表端点不下发（其全部行恒为 FILED，前端不依赖） */
    appealStatus?: string;
    slaDueAt?: null | string;
    demoteUntil?: null | string;
    createTime?: null | string;
  }

  export interface PoolListResult {
    records?: PoolRecord[];
    total?: number | string;
    page?: number;
    /** 服务端固定 20，列表无 pageSize 参数 */
    size?: number;
  }

  /** 状态筛选值（OPEN = PENDING + ESCALATED，缺省） */
  export type PoolStatusFilter =
    | 'BLOCK'
    | 'CLEAR'
    | 'ESCALATED'
    | 'NOT_SAMPLED'
    | 'OPEN'
    | 'PENDING'
    | 'UPHOLD';

  /** 申诉单（列表行，GET /admin/soft-tier-appeals）：含理由与作者昵称，雪花字段为字符串 */
  export interface AppealRecord {
    id: string;
    targetType: 'comment' | 'work' | string;
    targetId: string;
    authorId: string;
    authorNickname?: null | string;
    appealReason?: null | string;
    sampleLayer: string;
    poolStatus: string;
    slaDueAt?: null | string;
    demoteUntil?: null | string;
    createTime?: null | string;
    appealFiledAt?: null | string;
  }

  export interface AppealListResult {
    records?: AppealRecord[];
    total?: number | string;
    page?: number;
    size?: number;
  }

  /** 详情：台账 r.* 全字段（snake_case，无 rid/tid/aid/alid 别名）+ authorNickname + work/comment 节选 */
  export interface PoolDetail {
    id?: string;
    target_id?: string;
    author_id?: string;
    audit_log_id?: null | string;
    authorNickname?: null | string;
    appeal_reason?: null | string;
    target_type?: string;
    ai_tendency?: string;
    narrative?: null | string;
    sampled?: number;
    sample_layer?: string;
    pool_status?: string;
    appeal_status?: string;
    reviewer_id?: null | string;
    reason?: null | string;
    sla_due_at?: null | string;
    reviewed_at?: null | string;
    demote_until?: null | string;
    create_time?: null | string;
    work?: {
      title?: string;
      message?: string;
      status?: number;
      soft_demote_until?: null | string;
    } | null;
    comment?: {
      content?: string;
      status?: number;
      workId?: string;
      soft_demote_until?: null | string;
    } | null;
    [key: string]: any;
  }

  /** 裁决动作：CLEAR 恢复展示 / UPHOLD 维持至窗口结束 / BLOCK 拦截下架（理由必填） */
  export type VerdictAction = 'BLOCK' | 'CLEAR' | 'UPHOLD';
}

/** 复核队列（ESCALATED 服务端置顶、sla_due_at 升序；固定每页 20） */
export async function getSoftTierPoolList(params: {
  page: number;
  status?: AdminSoftTierApi.PoolStatusFilter;
}) {
  const res = await requestClient.get<AdminSoftTierApi.PoolListResult>(
    '/admin/soft-tier-pool',
    { params },
  );
  return { ...res, total: Number(res.total ?? 0) };
}

/** 复核详情（含内容节选、authorNickname、appeal_reason 与 ai_audit_log 关联 id） */
export async function getSoftTierPoolDetail(id: string) {
  return requestClient.get<AdminSoftTierApi.PoolDetail>(
    `/admin/soft-tier-pool/${id}`,
  );
}

/** 申诉单分页列表（appeal_status=FILED，按申诉时间倒序；含 appealReason 与作者昵称） */
export async function getSoftTierAppeals(params: { page: number }) {
  const res = await requestClient.get<AdminSoftTierApi.AppealListResult>(
    '/admin/soft-tier-appeals',
    { params },
  );
  return { ...res, total: Number(res.total ?? 0) };
}

/**
 * 裁决。BLOCK 理由必填（复用既有下架状态机 + 审核结果通知）；
 * 已终裁复核单重复裁决返回 3001（幂等防并发），调用方收到后应刷新列表。
 */
export async function postSoftTierVerdict(
  id: string,
  body: { action: AdminSoftTierApi.VerdictAction; reason?: string },
) {
  return requestClient.post(`/admin/soft-tier-pool/${id}/verdict`, body);
}
