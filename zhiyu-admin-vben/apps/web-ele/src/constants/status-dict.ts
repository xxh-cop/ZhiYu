/**
 * 全站状态字典（valueEnum 思路，与后端真实枚举一一对应）。
 * 页面禁止再各自写 statusTagType/statusLabel 硬映射；
 * tag 渲染统一走 ZyStatusTag，纯文本场景用 dictLabel。
 * 修改枚举前必须先核对后端 DDL/枚举定义，不要凭页面习惯改。
 */

export type StatusTagType = 'danger' | 'info' | 'primary' | 'success' | 'warning';

export interface StatusMeta {
  label: string;
  tag: StatusTagType;
}

export type StatusDict = Record<number | string, StatusMeta>;

/** 纯文本取文案；未知值回退为原值字符串（不猜、不显示「未知」掩盖数据问题） */
export function dictLabel(dict: StatusDict, value: number | string | undefined | null): string {
  if (value === undefined || value === null || value === '') return '-';
  return dict[value]?.label ?? String(value);
}

/** 取 tag 色型；未知值回退 info（中性） */
export function dictTag(dict: StatusDict, value: number | string | undefined | null): StatusTagType {
  if (value === undefined || value === null || value === '') return 'info';
  return dict[value]?.tag ?? 'info';
}

/** 角色码（user.role，与 sql/schema.sql 一致） */
export const ROLE: StatusDict = {
  0: { label: '学生', tag: 'info' },
  1: { label: '传承人', tag: 'info' },
  2: { label: '咨询师', tag: 'info' },
  3: { label: '管理员', tag: 'info' },
};

/** 账号状态（user.status：1 正常 / 0 禁用） */
export const ACCOUNT_STATUS: StatusDict = {
  0: { label: '禁用', tag: 'info' },
  1: { label: '正常', tag: 'success' },
};

/** 小组状态（0 关闭 / 1 开放） */
export const GROUP_STATUS: StatusDict = {
  0: { label: '关闭', tag: 'info' },
  1: { label: '开放', tag: 'success' },
};

/** 传承人认证状态（verified：0 未认证 / 1 已认证） */
export const VERIFIED_STATUS: StatusDict = {
  0: { label: '未认证', tag: 'info' },
  1: { label: '已认证', tag: 'success' },
};

/** 校园身份审核状态 */
export const CAMPUS_STATUS: StatusDict = {
  APPROVED: { label: '已通过', tag: 'success' },
  PENDING: { label: '待审核', tag: 'warning' },
  REJECTED: { label: '未通过', tag: 'danger' },
};

/** 作品审核状态（work.status：0 待审 / 1 已发布 / 2 下架） */
export const WORK_STATUS: StatusDict = {
  0: { label: '待审', tag: 'warning' },
  1: { label: '已发布', tag: 'success' },
  2: { label: '下架', tag: 'info' },
};

/** 作品可见性（work.visible） */
export const WORK_VISIBLE: StatusDict = {
  0: { label: '私密', tag: 'info' },
  1: { label: '公开', tag: 'success' },
  2: { label: '仅小组', tag: 'info' },
};

/**
 * 手作项目状态（craft_project.status：0 / 1 / 2 / 3）。
 * 定稿依据 sql/schema.sql DDL 注释「0待审 1上架 2下架」；3=归档（分层下架 2026-11-05，
 * 下架到期由归档 Worker 流转，2/3 不可回 1）。
 */
export const PROJECT_STATUS: StatusDict = {
  0: { label: '待审', tag: 'warning' },
  1: { label: '上架', tag: 'success' },
  2: { label: '下架', tag: 'info' },
  // 分层下架（2026-11-05）：下架到期由归档 Worker 转 3；颜色沿用终态灰，文字区分
  3: { label: '归档', tag: 'info' },
};

/** 手作项目难度（level：1 入门 / 2 进阶 / 3 高阶） */
export const PROJECT_LEVEL: StatusDict = {
  1: { label: '入门', tag: 'info' },
  2: { label: '进阶', tag: 'info' },
  3: { label: '高阶', tag: 'info' },
};

/** 评论审核状态（0 待审核 / 1 已公开 / 2 已隐藏） */
export const COMMENT_STATUS: StatusDict = {
  0: { label: '待审核', tag: 'warning' },
  1: { label: '已公开', tag: 'success' },
  2: { label: '已隐藏', tag: 'info' },
};

/** 通知类型（notification.type，与后端通知模块枚举一一对应） */
export const NOTIFICATION_TYPE: StatusDict = {
  BOOKING_REPLY: { label: '预约回复', tag: 'primary' },
  CAMPUS_AUDIT: { label: '校园审核', tag: 'warning' },
  CARE_REMINDER: { label: '关怀提醒', tag: 'danger' },
  COMMENT_AUDIT: { label: '评论审核', tag: 'warning' },
  GROUP_JOIN: { label: '小组动态', tag: 'info' },
  // 软档处置链（2026-11-07）：作者侧=展示受限/恢复通知；管理员侧=SLA 升级与申诉提醒
  SOFT_TIER_NOTICE: { label: '软档处置', tag: 'warning' },
  WORK_AUDIT: { label: '作品审核', tag: 'warning' },
};

/**
 * 通知关联对象类型（notification.ref_type，BK 筛选用）。
 * 与 openapi /api/notifications 的 refType 参数 9 个合法值一一对应（BJ + 2026-11-07 处置链增 soft_review），
 * 取自事件侧真实写入集合；「全部」= 不传参，不含占位值。
 */
export const NOTIFICATION_REF_TYPE: StatusDict = {
  booking: { label: '预约', tag: 'primary' },
  campus_verification: { label: '校园核验', tag: 'warning' },
  care_reminder: { label: '关怀跟进', tag: 'danger' },
  comment: { label: '评论', tag: 'warning' },
  group: { label: '小组', tag: 'info' },
  inheritor_profile: { label: '传承人资料', tag: 'info' },
  project: { label: '项目', tag: 'warning' },
  soft_review: { label: '软档复核', tag: 'warning' },
  work: { label: '作品', tag: 'warning' },
};

/**
 * 通知类型精化表（BH）：后端 BE 起项目审核结果复用 WORK_AUDIT 类型码 +
 * refType=project 下发（不新增类型码），展示层按 "type:refType" 组合键精化。
 * 未来新增的细分（如 BG 批核验/小组类通知）只需在此表加条目，页面不加分支。
 */
const NOTIFICATION_TYPE_REFINE: Record<string, StatusMeta> = {
  // BE：项目审核结果复用 WORK_AUDIT + refType=project（不加类型码）
  'WORK_AUDIT:project': { label: '项目审核', tag: 'warning' },
  // BG1（BN 补）：传承人入驻核验结果复用 CAMPUS_AUDIT + refType=inheritor_profile
  'CAMPUS_AUDIT:inheritor_profile': { label: '传承人核验', tag: 'warning' },
};

/**
 * 通知类型标签：返回可直接 v-bind 给 ZyStatusTag 的 { dict, value }。
 * 先查 "type:refType" 精化表，未命中回退 NOTIFICATION_TYPE（保持 type 单维度语义）。
 */
export function notificationTypeTag(type: string, refType?: string) {
  const key = `${type}:${refType ?? ''}`;
  const refined = NOTIFICATION_TYPE_REFINE[key];
  if (refined) {
    return { dict: { [key]: refined } as StatusDict, value: key };
  }
  return { dict: NOTIFICATION_TYPE, value: type };
}

/** AI 安全过滤动作（ai_audit_log.action，标签即原始枚举值） */
export const AUDIT_ACTION: StatusDict = {
  BLOCK: { label: 'BLOCK', tag: 'danger' },
  PASS: { label: 'PASS', tag: 'success' },
  REVIEW: { label: 'REVIEW', tag: 'warning' },
  REWRITE: { label: 'REWRITE', tag: 'warning' },
};

/** AI 调用场景（ai_audit_log.scene） */
export const AUDIT_SCENE: StatusDict = {
  chat: { label: '对话', tag: 'info' },
  moderation: { label: '审核辅助', tag: 'info' },
  recommend: { label: '推荐', tag: 'info' },
  work_message: { label: '作品寄语', tag: 'info' },
};

/**
 * AI 审核辅助倾向（BL ai-suggest 的 tendency 三枚举）。
 * 仅描述「机器提示倾向」，不构成审核结论（disclaimer 随响应常驻展示）。
 */
export const AI_MODERATION_TENDENCY: StatusDict = {
  HAS_CONCERNS: { label: '有需要关注点', tag: 'warning' },
  NO_OBVIOUS_ISSUE: { label: '未见明显关注点', tag: 'success' },
  UNCERTAIN: { label: '无法判断', tag: 'info' },
};

/** AI 调用结果状态（治理看板 resultStatus） */
export const AI_RESULT_STATUS: StatusDict = {
  BLOCKED: { label: 'BLOCKED', tag: 'danger' },
  ERROR: { label: 'ERROR', tag: 'danger' },
  FALLBACK: { label: 'FALLBACK', tag: 'warning' },
  OK: { label: 'OK', tag: 'success' },
};

/**
 * 风险等级（危机日志 crisis_session_log / 关怀提醒 care_reminder 的 risk_level）。
 * AW1（2026-11-05）对齐后端 AU1 定稿三档：1 关注 / 2 高危 / 3 紧急。
 * 配色只用既有 tag 类型：1→info、2→warning、3→danger（自 AQ2 起后端按词表分组写真实档位）。
 */
export const CARE_RISK: StatusDict = {
  1: { label: '关注', tag: 'info' },
  2: { label: '高危', tag: 'warning' },
  3: { label: '紧急', tag: 'danger' },
};

/** 关怀跟进状态（0 待处理 / 1 已跟进） */
export const CARE_STATUS: StatusDict = {
  0: { label: '待处理', tag: 'warning' },
  1: { label: '已跟进', tag: 'success' },
};

/** 活动类型 */
export const PROGRAM_KIND: StatusDict = {
  COURSE: { label: '系列课程', tag: 'info' },
  GUIDANCE: { label: '一对一指导', tag: 'info' },
  WORKSHOP: { label: '主题工作坊', tag: 'info' },
};

/** 活动发布状态 */
export const PROGRAM_STATUS: StatusDict = {
  CANCELLED: { label: '已取消', tag: 'danger' },
  DRAFT: { label: '草稿', tag: 'info' },
  PUBLISHED: { label: '已发布', tag: 'success' },
};

/** 活动预约状态 */
export const BOOKING_STATUS: StatusDict = {
  BOOKED: { label: '已确认 / 待签到', tag: 'success' },
  CHECKED_IN: { label: '已签到', tag: 'success' },
  PENDING: { label: '待确认', tag: 'warning' },
  REJECTED: { label: '未通过', tag: 'info' },
};

/**
 * 项目问责动作来源（admin_operation_log 的 project.* 动作，BC1/BF 2026-11-05）。
 * 与后端 GET /api/admin/projects/{id} 的 lastAction 枚举一一对应；
 * status=2 的问责语义区分：评审驳回（REJECT）与事后下架（TAKEDOWN）是两种不同场景，颜色分开。
 */
export const PROJECT_ACTION_ORIGIN: StatusDict = {
  APPROVE: { label: '审核通过', tag: 'success' },
  REJECT: { label: '评审驳回', tag: 'danger' },
  TAKEDOWN: { label: '事后下架', tag: 'warning' },
  DELETE: { label: '删除', tag: 'info' },
};

/** 运行健康组件状态（/admin/ops/health；标签即原始枚举值） */
export const HEALTH_STATUS: StatusDict = {
  DEGRADED: { label: 'DEGRADED', tag: 'warning' },
  DOWN: { label: 'DOWN', tag: 'danger' },
  UP: { label: 'UP', tag: 'success' },
  WARN: { label: 'WARN', tag: 'warning' },
};

/**
 * 软档复核单状态（moderation_soft_review.pool_status，2026-11-07 处置链）。
 * 文案纪律：禁用「违规/处罚/降权」，基调是「保护」——BLOCK 用「拦截」、降权用「展示受限」。
 * EXPIRED 列存在于表设计但当前无代码路径产生，不入字典（出现即原值透传，不猜）。
 */
export const SOFT_TIER_POOL_STATUS: StatusDict = {
  PENDING: { label: '待复核', tag: 'warning' },
  ESCALATED: { label: '已升级待审', tag: 'danger' },
  NOT_SAMPLED: { label: '未抽中（窗口期）', tag: 'info' },
  CLEAR: { label: '已恢复展示', tag: 'success' },
  UPHOLD: { label: '维持展示受限', tag: 'info' },
  BLOCK: { label: '已拦截', tag: 'danger' },
};

/**
 * 软档抽样分层（sample_layer，与后端 SoftTierRecordService.decideLayer 一一对应）。
 * overflow_* 为日入池达软上限后转未抽中（仍处 72h 展示受限窗口，次日队列优先补足）。
 */
export const SOFT_TIER_SAMPLE_LAYER: StatusDict = {
  mandatory_lexicon: { label: '词库命中', tag: 'danger' },
  mandatory_riskpoint: { label: '风险点', tag: 'warning' },
  mandatory_author_first: { label: '作者首条', tag: 'warning' },
  random: { label: '随机抽检', tag: 'info' },
  overflow_author_first: { label: '作者首条 · 溢出未抽中', tag: 'info' },
  overflow_random: { label: '随机 · 溢出未抽中', tag: 'info' },
};

/** 软档申诉状态（appeal_status；FILED 即「申诉逃生通道」已触发，复核单升 ESCALATED） */
export const SOFT_TIER_APPEAL_STATUS: StatusDict = {
  NONE: { label: '未申诉', tag: 'info' },
  FILED: { label: '已申诉', tag: 'danger' },
  RESOLVED: { label: '申诉已处理', tag: 'success' },
};

/** 软档对象类型（target_type） */
export const SOFT_TIER_TARGET_TYPE: StatusDict = {
  work: { label: '作品', tag: 'warning' },
  comment: { label: '评论', tag: 'primary' },
};
