import { describe, expect, it } from 'vitest';

import { buildChartTheme } from './chart-theme';

const tokens: Record<string, string> = {
  '--el-bg-color': 'rgb(255, 255, 255)',
  '--el-bg-color-overlay': 'rgb(255, 255, 255)',
  '--el-border-color': 'rgb(220, 223, 230)',
  '--el-border-color-lighter': 'rgb(235, 238, 245)',
  '--el-color-danger': 'rgb(220, 38, 38)',
  '--el-color-danger-dark-2': 'rgb(196, 33, 33)',
  '--el-color-primary': 'rgb(79, 70, 229)',
  '--el-color-primary-dark-2': 'rgb(63, 56, 183)',
  '--el-color-success': 'rgb(4, 120, 87)',
  '--el-color-success-dark-2': 'rgb(4, 108, 79)',
  '--el-color-warning': 'rgb(180, 83, 9)',
  '--el-color-warning-dark-2': 'rgb(161, 74, 8)',
  '--el-text-color-regular': 'rgb(50, 54, 57)',
};

const read = (name: string) => tokens[name] ?? '';

describe('buildChartTheme', () => {
  it('优先读取明暗自适应的语义色令牌', () => {
    const theme = buildChartTheme(read);
    expect(theme.colors).toEqual([
      'rgb(63, 56, 183)',
      'rgb(161, 74, 8)',
      'rgb(4, 108, 79)',
      'rgb(196, 33, 33)',
      'rgb(50, 54, 57)',
    ]);
    expect(theme.text).toBe('rgb(50, 54, 57)');
    expect(theme.grid).toBe('rgb(235, 238, 245)');
    expect(theme.border).toBe('rgb(220, 223, 230)');
    expect(theme.background).toBe('rgb(255, 255, 255)');
    expect(theme.tooltipBackground).toBe('rgb(255, 255, 255)');
    expect(theme.tooltipBorder).toBe('rgb(220, 223, 230)');
  });

  it('缺少 -dark-2 时回退到基础语义色', () => {
    const sparse = { ...tokens };
    delete sparse['--el-color-primary-dark-2'];
    const theme = buildChartTheme((name) => sparse[name] ?? '');
    expect(theme.colors[0]).toBe('rgb(79, 70, 229)');
  });

  it('缺少分线令牌时回退到边框色', () => {
    const sparse = { ...tokens };
    delete sparse['--el-border-color-lighter'];
    expect(buildChartTheme((name) => sparse[name] ?? '').grid).toBe(
      'rgb(220, 223, 230)',
    );
  });

  it('令牌全部缺失时返回空字符串而不抛错', () => {
    const theme = buildChartTheme(() => '');
    expect(theme.colors).toEqual(['', '', '', '', '']);
    expect(theme.background).toBe('');
  });
});
