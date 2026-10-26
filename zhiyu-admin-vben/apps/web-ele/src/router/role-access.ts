/**
 * 管理端角色判定（纯函数，无副作用，可单测）。
 *
 * 后端 `GET /api/auth/me` 返回单个数值角色码 `role`（0 学生 / 1 传承人 /
 * 2 咨询师 / 3 管理员），`getUserInfoApi` 会映射为 Vben 的
 * `roles: ['ROLE_<code>']`。这里同时兼容数值、数字字符串与 `ROLE_<code>`
 * 三种写法，只有 1/2/3 视为可进入管理端。
 *
 * 注意：本函数只做判断，不把归一化结果写回 access store，避免影响
 * Vben 后续基于原始 `roles` 字符串的路由 / 菜单匹配。
 */

const ADMIN_ROLE_CODES = new Set([1, 2, 3]);

const NUMERIC_ROLE_PATTERN = /^\d+$/;

/**
 * 登录流程中「账号没有管理端角色」的专用错误标识。
 * 登录页据此区分「账号无权限」与「账号密码错误 / 网络失败」。
 */
const NO_ADMIN_ROLE_ERROR = 'NO_ADMIN_ROLE';

/** 把单个未知值归一化为角色码；无法识别时返回 null。 */
function normalizeRoleCode(value: unknown): null | number {
  if (typeof value === 'number') {
    return Number.isInteger(value) ? value : null;
  }
  if (typeof value !== 'string') {
    return null;
  }
  const normalized = value.trim().toUpperCase();
  if (!normalized) {
    return null;
  }
  const code = normalized.startsWith('ROLE_')
    ? normalized.slice('ROLE_'.length)
    : normalized;
  if (!NUMERIC_ROLE_PATTERN.test(code)) {
    return null;
  }
  return Number(code);
}

/**
 * 判断当前角色是否具备管理端访问资格（role ∈ {1, 2, 3}）。
 *
 * 0（学生）、空值、null / undefined 与未知角色一律返回 false。
 *
 * @param roles 角色码或角色码数组，兼容 `1` / `'1'` / `'ROLE_1'`
 */
function canAccessAdmin(roles: unknown): boolean {
  const candidates = Array.isArray(roles) ? roles : [roles];
  return candidates.some((role) => {
    const code = normalizeRoleCode(role);
    return code !== null && ADMIN_ROLE_CODES.has(code);
  });
}

export { canAccessAdmin, NO_ADMIN_ROLE_ERROR };
