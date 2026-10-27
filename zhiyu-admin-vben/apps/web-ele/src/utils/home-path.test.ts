import { describe, expect, it } from 'vitest';

import { homePathForRoles } from './home-path';

describe('homePathForRoles', () => {
  it('管理员返回工作台', () => {
    expect(homePathForRoles(['ROLE_3'])).toBe('/zhiyu/dashboard');
  });

  it('咨询师返回关怀提醒', () => {
    expect(homePathForRoles(['ROLE_2'])).toBe(
      '/zhiyu/business/care-reminders',
    );
  });

  it('传承人返回活动与预约', () => {
    expect(homePathForRoles(['ROLE_1'])).toBe('/zhiyu/programs');
  });

  it('无角色时回退到工作台', () => {
    expect(homePathForRoles([])).toBe('/zhiyu/dashboard');
  });

  it('多角色时管理员优先', () => {
    expect(homePathForRoles(['ROLE_1', 'ROLE_3'])).toBe('/zhiyu/dashboard');
  });
});
