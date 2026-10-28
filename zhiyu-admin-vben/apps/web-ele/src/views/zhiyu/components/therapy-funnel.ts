export interface FunnelStage {
  fromPrevious: null | number;
  fromStart: null | number;
  key: string;
  label: string;
  users: number;
}

export interface FunnelFlow {
  completedSessions: number;
  flowSeconds: number;
  focusRate: null | number;
}

export interface TherapyFunnelData {
  cohort: number;
  days: number;
  flow: FunnelFlow;
  generatedAt: string;
  insufficient: boolean;
  stages: FunnelStage[];
}

export function toFunnelChartData(stages: FunnelStage[], colors: string[] = []) {
  return stages.map((stage, index) => ({
    name: stage.label,
    value: stage.users,
    ...(colors.length
      ? { itemStyle: { color: colors[index % colors.length] } }
      : {}),
  }));
}

export function formatRate(value: null | number | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }
  return `${(value * 100).toFixed(1)}%`;
}

export function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '—';
  }
  if (seconds < 60) {
    return `${Math.round(seconds)} 秒`;
  }
  return `${Math.round(seconds / 60)} 分钟`;
}
