import { requestClient } from '#/api/request';

/**
 * AI 模型路由只读总览（2026-11-08；契约 openapi/openapi.yaml
 * 「/api/admin/ai-routing/overview」段，operationId: adminAiRoutingOverviewGet）。
 *
 * 权限：仅 role=3。只读端点——页面不提供任何配置写入，
 * provider 切换走启动参数（刻意如此）。
 *
 * 契约要点：
 * - capabilities 是路由配置呈现（ModelRouter/LlmClientRegistry 生效状态），不是统计；
 * - distribution 是 ai_trace 按 scene+provider+result_status 聚合的纯计数，
 *   聚合计数遵循全局 Long→String 序列化（字符串下发，计算前需 Number()）；
 * - capabilities[].sceneKeys 一个能力可对应多个 scene，一个 scene 也可被多个能力共用
 *   （llm-message 由 message/stuck_guide/listening_summary 回落共用），
 *   按能力汇总时该 scene 计数会在各能力间重叠，归属以 scene 为准不重复拆分；
 * - distribution[].provider 值域 deepseek/ollama/mock，null = 编排层 trace、语音或加列前存量。
 */
export namespace AdminAiRoutingApi {
  /** 装配状态：云/本地/Mock 是否就位与演示开关生效值（不含任何密钥） */
  export interface RegistryInfo {
    cloudAvailable: boolean;
    cloudProvider: null | string;
    localAvailable: boolean;
    localModel: string;
    mockAvailable: boolean;
    localDemoMode: boolean;
    shadowLocalEffective: boolean;
  }

  /** 单个能力的路由配置呈现 */
  export interface Capability {
    task: string;
    /** standard / strong / none（crisis 为 none，不走 LLM） */
    tier: string;
    /** 该档云模型名；none 档为 null */
    cloudModel: null | string;
    llmAllowed: boolean;
    /** cloud-only 硬约束：本地永不接管（无任何配置键） */
    cloudOnly: boolean;
    /** 云不可用 → 本地兜底的生效值（含各自 env 开关与 D8 双开关） */
    fallbackAllowed: boolean;
    /** 记账场景清单；空数组 = 不落 ai_trace（moderation_shadow / crisis） */
    sceneKeys: string[];
  }

  /** scene+provider+period 一行的纯计数（字符串计数，计算前 Number()） */
  export interface DistributionRow {
    scene: string;
    /** deepseek / ollama / mock；null = 编排层 trace、语音或加列前存量 */
    provider: null | string;
    /** preFix = 事故锚点前（历史）；postFix = 修复后窗口（现行） */
    period: 'postFix' | 'preFix';
    calls: number | string;
    okCount: number | string;
    fallbackCount: number | string;
    errorCount: number | string;
    blockedCount: number | string;
    otherCount: number | string;
  }

  /**
   * 事故锚点（声明的运维事实，非自动计算）：锚点前 cloud-only 的 ollama 记录
   * 为已修复事故（LlmClientRegistry 父类型注入歧义）的证据，不计入现行红线。
   */
  export interface IncidentAnchor {
    at: string;
    /** config = 服务端配置默认值；override = 本次请求 anchor 参数临时覆盖 */
    source: 'config' | 'override';
    note: string;
  }

  /**
   * 「cloud-only 是否被本地接管」的权威数字：只统计独占 scene
   * （归属能力全部 cloud-only，无共享歧义）的 ollama 记录。
   * postFixLocalCalls 应为 0（非 0 即 VIOLATION）。
   */
  export interface CloudOnlyLocalCheck {
    anchor: string;
    exclusiveScenes: string[];
    preFixLocalCalls: number | string;
    postFixLocalCalls: number | string;
    verdict: 'CLEAN' | 'VIOLATION';
  }

  export interface Overview {
    window: { from: string; to: string };
    generatedAt: string;
    incidentAnchor: IncidentAnchor;
    registry: RegistryInfo;
    capabilities: Capability[];
    distribution: DistributionRow[];
    cloudOnlyLocalCheck: CloudOnlyLocalCheck;
    note: string;
  }
}

/**
 * 路由总览。from/to 为 ISO-8601 本地日期时间（如 2026-11-01T00:00:00），
 * 区间最长 90 天；缺省 from=to-7天、to=当前时间。
 * anchor 为事故锚点临时覆盖（答辩/排查现场用）；缺省用服务端配置值。
 */
export async function getAiRoutingOverview(params: {
  anchor?: string;
  from: string;
  to: string;
}) {
  return requestClient.get<AdminAiRoutingApi.Overview>(
    '/admin/ai-routing/overview',
    { params },
  );
}
