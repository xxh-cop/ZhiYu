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
    note: string;
    resilienceStore: string;
    speechConfigured: boolean;
    vectorMode: 'LOCAL_FILE' | 'UNCONFIGURED' | string;
    vectorVerified: boolean;
  }

  export interface Overview {
    actionCounts: CountItem[];
    config: Config;
    generatedAt: string;
    privacyNote: string;
    reasonCounts: CountItem[];
    recentEvents: RecentEvent[];
    sceneCounts: CountItem[];
    totalEvents: number;
    windowDays: number;
  }
}

export async function getAiGovernanceOverview(days = 30) {
  return requestClient.get<AdminAiGovernanceApi.Overview>(
    '/admin/ai-governance/overview',
    { params: { days } },
  );
}
