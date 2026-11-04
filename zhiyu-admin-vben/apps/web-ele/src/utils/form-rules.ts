import type { FormItemRule } from 'element-plus';

/**
 * 管理端共享表单校验规则。
 * 各弹窗不再各自手写 username/password/nickname 规则组，
 * 文案与后端约束（登录名 3-50、密码 6-32、昵称 ≤50）保持一处维护。
 */

/** 通用文本：必填 + 长度区间，blur 触发、错误就地展示 */
export function textRules(
  label: string,
  options: { max?: number; maxMessage?: string; min?: number; minMaxMessage?: string; required?: boolean } = {},
): FormItemRule[] {
  const { min, max, required = true, minMaxMessage, maxMessage } = options;
  const rules: FormItemRule[] = [];
  if (required) {
    rules.push({ required: true, message: `请输入${label}`, trigger: 'blur' });
  }
  if (min !== undefined && max !== undefined) {
    rules.push({ min, max, message: minMaxMessage ?? `${label}长度 ${min}-${max}`, trigger: 'blur' });
  } else if (max !== undefined) {
    rules.push({ max, message: maxMessage ?? `${label}不超过 ${max} 字`, trigger: 'blur' });
  }
  return rules;
}

/** 登录名：3-50 位 */
export const usernameRules = (): FormItemRule[] => [
  { required: true, message: '请输入登录名', trigger: 'blur' },
  { min: 3, max: 50, message: '登录名长度 3-50', trigger: 'blur' },
];

/** 密码：6-32 位 */
export const passwordRules = (): FormItemRule[] => [
  { required: true, message: '请输入密码', trigger: 'blur' },
  { min: 6, max: 32, message: '密码长度 6-32', trigger: 'blur' },
];

/** 显示昵称：必填，不超过 50 字 */
export const nicknameRules = (): FormItemRule[] => [
  { required: true, message: '请输入显示昵称', trigger: 'blur' },
  { max: 50, message: '显示昵称不超过 50 字', trigger: 'blur' },
];
