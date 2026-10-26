import { describe, expect, it } from 'vitest';

import { canAccessAdmin } from './role-access';

describe('canAccessAdmin', () => {
  it('放行传承人 / 咨询师 / 管理员（数值、数字字符串、ROLE_ 前缀）', () => {
    expect(canAccessAdmin([1])).toBe(true);
    expect(canAccessAdmin([2])).toBe(true);
    expect(canAccessAdmin([3])).toBe(true);
    expect(canAccessAdmin(['1'])).toBe(true);
    expect(canAccessAdmin(['ROLE_1'])).toBe(true);
    expect(canAccessAdmin(['ROLE_2'])).toBe(true);
    expect(canAccessAdmin(['ROLE_3'])).toBe(true);
  });

  it('拒绝学生角色 0', () => {
    expect(canAccessAdmin([0])).toBe(false);
    expect(canAccessAdmin(['0'])).toBe(false);
    expect(canAccessAdmin(['ROLE_0'])).toBe(false);
    expect(canAccessAdmin('ROLE_0')).toBe(false);
  });

  it('拒绝空角色、null、undefined', () => {
    expect(canAccessAdmin([])).toBe(false);
    expect(canAccessAdmin([''])).toBe(false);
    expect(canAccessAdmin('')).toBe(false);
    expect(canAccessAdmin(null)).toBe(false);
    expect(canAccessAdmin(undefined)).toBe(false);
  });

  it('拒绝未知与畸形角色值', () => {
    expect(canAccessAdmin(['ROLE_10'])).toBe(false);
    expect(canAccessAdmin(['admin'])).toBe(false);
    expect(canAccessAdmin(['ROLE_'])).toBe(false);
    expect(canAccessAdmin([1.5])).toBe(false);
    expect(canAccessAdmin([Number.NaN])).toBe(false);
    expect(canAccessAdmin([{}])).toBe(false);
    expect(canAccessAdmin([true])).toBe(false);
  });

  it('大小写与首尾空白归一化后仍可识别', () => {
    expect(canAccessAdmin([' role_3 '])).toBe(true);
    expect(canAccessAdmin([' 2 '])).toBe(true);
  });

  it('数组中有任意一个合法管理端角色即放行', () => {
    expect(canAccessAdmin(['ROLE_0', 'ROLE_2'])).toBe(true);
    expect(canAccessAdmin(['ROLE_0', 'ROLE_9'])).toBe(false);
  });
});
