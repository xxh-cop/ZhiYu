import { describe, expect, it } from 'vitest';

import { resolveDataState } from './resolve';

describe('resolveDataState', () => {
  it('error 优先于 loading，重试期间不显示骨架', () => {
    expect(
      resolveDataState({ error: '加载失败', isEmpty: true, loading: true }),
    ).toBe('error');
  });

  it('无错误且加载中时显示 loading', () => {
    expect(
      resolveDataState({ error: '', isEmpty: true, loading: true }),
    ).toBe('loading');
  });

  it('请求成功但没有数据时显示 empty', () => {
    expect(
      resolveDataState({ error: '', isEmpty: true, loading: false }),
    ).toBe('empty');
  });

  it('有数据时返回 ready，由表格自行渲染', () => {
    expect(
      resolveDataState({ error: '', isEmpty: false, loading: false }),
    ).toBe('ready');
  });
});
