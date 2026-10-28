import { describe, expect, it } from 'vitest';

import {
  formatDuration,
  formatRate,
  toFunnelChartData,
} from './therapy-funnel';

describe('therapy funnel helpers', () => {
  it('formats null rates as a placeholder and ratios as percentages', () => {
    expect(formatRate(null)).toBe('—');
    expect(formatRate(0.5)).toBe('50.0%');
    expect(formatRate(1)).toBe('100.0%');
  });

  it('formats duration without inventing precision', () => {
    expect(formatDuration(0)).toBe('0 秒');
    expect(formatDuration(90)).toBe('2 分钟');
    expect(formatDuration(Number.NaN)).toBe('—');
  });

  it('keeps stage order for the funnel', () => {
    expect(
      toFunnelChartData([
        { fromPrevious: null, fromStart: 1, key: 'emotion', label: '情绪会话', users: 6 },
        { fromPrevious: 0.5, fromStart: 0.5, key: 'recommended', label: '获得推荐', users: 3 },
      ]),
    ).toEqual([
      { name: '情绪会话', value: 6 },
      { name: '获得推荐', value: 3 },
    ]);
  });
});
