import { requestClient } from '#/api/request';

export namespace AdminAiGovernanceApi {
  export interface CountItem {
    name: string;
    value: number;
  }

  export interface RecentEvent {
    action: string;
    createTime: string;
    id: string;
    reasonCategory: string;
    scene: string;
    sceneLabel: string;
  }

  export interface Config {
    chatMode: 'EXTERNAL' | 'MOCK';
    chatVerified: boolean;
    maxTokens: number;
    modelConfigRevision: number;
    modelName: string;
    note: string;
    promptConfigRevision: number;
    promptVersions: CountItem[];
    resilienceStore: string;
    speechConfigured: boolean;
    temperature: number;
    vectorMode: 'LOCAL_FILE' | 'UNCONFIGURED' | string;
    vectorVerified: boolean;
  }

  export interface TraceFailure {
    createTime: string;
    fallbackReason: string;
    latencyMs: number | null;
    resultStatus: string;
    scene: string;
    sceneLabel: string;
    toolName: string | null;
  }

  export interface TraceSummary {
    avgLatencyMs: number;
    costConfigured: boolean;
    costNote: string;
    currency: string;
    estimatedCost: number;
    inputTokens: number | string;
    modelVersions: CountItem[];
    note: string;
    outputTokens: number | string;
    p95LatencyMs: number;
    promptVersions: CountItem[];
    recentFailures: TraceFailure[];
    statusCounts: CountItem[];
    toolCalls: number;
    toolSuccessRate: number;
    total: number;
  }

  export interface EvalCase {
    expectation: string;
    id: string;
    question: string;
    safe: boolean;
    type: string;
  }

  export interface EvalSummary {
    byType: CountItem[];
    cases: EvalCase[];
    e2eByType: CountItem[];
    e2eTasks: EvalCase[];
    e2eTotal: number;
    notes: string;
    safetyByType: CountItem[];
    safetyRedTeam: EvalCase[];
    safetyTotal: number;
    total: number;
    version: string;
  }

  export interface Overview {
    actionCounts: CountItem[];
    config: Config;
    eval: EvalSummary;
    generatedAt: string;
    privacyNote: string;
    reasonCounts: CountItem[];
    recentEvents: RecentEvent[];
    sceneCounts: CountItem[];
    totalEvents: number;
    trace: TraceSummary;
    windowDays: number;
  }
}

export async function getAiGovernanceOverview(days = 30) {
  return requestClient.get<AdminAiGovernanceApi.Overview>(
    '/admin/ai-governance/overview',
    { params: { days } },
  );
}
