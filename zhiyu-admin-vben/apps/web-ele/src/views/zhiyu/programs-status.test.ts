import { describe, expect, it } from 'vitest';

import { allowedStatuses } from './programs-status';

describe('allowedStatuses', () => {
  it('草稿可以保存、发布或取消', () => {
    expect(allowedStatuses('DRAFT')).toEqual(['DRAFT', 'PUBLISHED', 'CANCELLED']);
  });

  it('已发布只能保持或取消', () => {
    expect(allowedStatuses('PUBLISHED')).toEqual(['PUBLISHED', 'CANCELLED']);
  });

  it('已取消是终态', () => {
    expect(allowedStatuses('CANCELLED')).toEqual(['CANCELLED']);
  });

  it('未知状态回退到草稿', () => {
    expect(allowedStatuses('UNKNOWN')).toEqual(['DRAFT']);
  });
});
