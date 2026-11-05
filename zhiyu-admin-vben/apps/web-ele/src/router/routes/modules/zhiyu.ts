import type { RouteRecordRaw } from 'vue-router';

/**
 * 织愈管理端信息架构（2026-11-04 AB 批重整）：
 * - 只调显示名称与分组归属，所有页面 URL 保持不变
 *   （迁入新分组的页面用绝对路径锁定原地址，通知跳转 / 书签不受影响）；
 * - 父分组显式 redirect 到首个子页：绝对路径子路由不会触发框架自动 redirect，
 *   面包屑「父级 › 当前页」点击父级时需要它回到分组首页；
 * - 父分组 authority 必须覆盖全部子页角色，否则子页会被整组隐藏。
 * - 「内容审核」更名「项目审核」，与「评论审核」「校园身份审核」边界清晰。
 */
const routes: RouteRecordRaw[] = [
  {
    name: 'ZhiyuDashboard',
    path: '/zhiyu/dashboard',
    component: () => import('#/views/zhiyu/dashboard.vue'),
    meta: {
      icon: 'lucide:layout-dashboard',
      order: 1,
      title: '工作台',
      authority: ['ROLE_3'],
    },
  },
  {
    name: 'ZhiyuNotifications',
    path: '/zhiyu/notifications',
    component: () => import('#/views/zhiyu/notifications.vue'),
    meta: {
      icon: 'lucide:bell',
      order: 2,
      title: '通知中心',
      authority: ['ROLE_1', 'ROLE_2', 'ROLE_3'],
    },
  },
  {
    name: 'ZhiyuReview',
    path: '/zhiyu/review',
    redirect: '/zhiyu/business/review-center',
    meta: {
      icon: 'lucide:shield-check',
      order: 3,
      title: '审核中心',
      authority: ['ROLE_3'],
    },
    children: [
      {
        name: 'ZhiyuReviewCenter',
        path: '/zhiyu/business/review-center',
        component: () => import('#/views/zhiyu/review-center.vue'),
        meta: {
          icon: 'lucide:inbox',
          title: '审核工作台',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuAudit',
        path: '/zhiyu/business/audit',
        component: () => import('#/views/zhiyu/audit.vue'),
        meta: {
          icon: 'lucide:clipboard-check',
          title: '项目审核',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuComments',
        path: '/zhiyu/comments',
        component: () => import('#/views/zhiyu/comments.vue'),
        meta: {
          icon: 'lucide:message-square-check',
          title: '评论审核',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuCampus',
        path: '/zhiyu/campus',
        component: () => import('#/views/zhiyu/campus.vue'),
        meta: {
          icon: 'lucide:graduation-cap',
          title: '校园身份审核',
          authority: ['ROLE_3'],
        },
      },
    ],
  },
  {
    name: 'ZhiyuOperation',
    path: '/zhiyu/operation',
    redirect: '/zhiyu/programs',
    meta: {
      icon: 'lucide:briefcase',
      order: 4,
      title: '业务运营',
      // 覆盖「活动与预约」的 ROLE_1/2，否则传承人/咨询师看不到该子页
      authority: ['ROLE_1', 'ROLE_2', 'ROLE_3'],
    },
    children: [
      {
        name: 'ZhiyuPrograms',
        path: '/zhiyu/programs',
        component: () => import('#/views/zhiyu/programs.vue'),
        meta: {
          icon: 'lucide:calendar-days',
          title: '活动与预约',
          authority: ['ROLE_1', 'ROLE_2', 'ROLE_3'],
        },
      },
      {
        name: 'ZhiyuWorks',
        path: '/zhiyu/business/works',
        component: () => import('#/views/zhiyu/works.vue'),
        meta: {
          icon: 'lucide:image',
          title: '作品管理',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuGroups',
        path: '/zhiyu/business/groups',
        component: () => import('#/views/zhiyu/groups.vue'),
        meta: {
          icon: 'lucide:users-round',
          title: '小组管理',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuHeritage',
        path: '/zhiyu/business/heritage',
        component: () => import('#/views/zhiyu/heritage.vue'),
        meta: {
          icon: 'lucide:library',
          title: '非遗内容',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuInheritors',
        path: '/zhiyu/business/inheritors',
        component: () => import('#/views/zhiyu/inheritors.vue'),
        meta: {
          icon: 'lucide:award',
          title: '传承人管理',
          authority: ['ROLE_3'],
        },
      },
    ],
  },
  {
    name: 'ZhiyuUserCare',
    path: '/zhiyu/user-care',
    redirect: '/zhiyu/business/users',
    meta: {
      icon: 'lucide:heart-handshake',
      order: 5,
      title: '用户与关怀',
      // 覆盖「关怀提醒」的 ROLE_2（咨询师）
      authority: ['ROLE_2', 'ROLE_3'],
    },
    children: [
      {
        name: 'ZhiyuUsers',
        path: '/zhiyu/business/users',
        component: () => import('#/views/zhiyu/users.vue'),
        meta: {
          icon: 'lucide:users',
          title: '用户管理',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuCareReminders',
        path: '/zhiyu/business/care-reminders',
        component: () => import('#/views/zhiyu/care-reminders.vue'),
        meta: {
          icon: 'lucide:heart',
          title: '关怀提醒',
          authority: ['ROLE_2', 'ROLE_3'],
        },
      },
      {
        // AN 2026-11-04：危机留存查看页，与后端契约一致仅 role ∈ {2,3}
        name: 'ZhiyuCrisisLogs',
        path: '/zhiyu/business/crisis-logs',
        component: () => import('#/views/zhiyu/crisis-logs.vue'),
        meta: {
          icon: 'lucide:shield-alert',
          title: '危机日志',
          authority: ['ROLE_2', 'ROLE_3'],
        },
      },
    ],
  },
  {
    name: 'ZhiyuDataCenter',
    path: '/zhiyu/data-center',
    meta: {
      icon: 'lucide:bar-chart-3',
      order: 6,
      title: '数据中心',
      authority: ['ROLE_3'],
    },
    children: [
      {
        name: 'ZhiyuAnalytics',
        path: 'analytics',
        component: () => import('#/views/zhiyu/analytics.vue'),
        meta: {
          icon: 'lucide:line-chart',
          title: '数据看板',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuEmotionAnalysis',
        path: 'emotion',
        component: () => import('#/views/zhiyu/emotion-analysis.vue'),
        meta: {
          icon: 'lucide:heart-pulse',
          title: '情绪分析',
          authority: ['ROLE_3'],
        },
      },
      {
        // 阶段 1-2（2026-11-05）：心流/专注度量看板；指标名由后端校准下发
        name: 'ZhiyuFlowMetrics',
        path: 'flow-metrics',
        component: () => import('#/views/zhiyu/flow-metrics.vue'),
        meta: {
          icon: 'lucide:gauge',
          title: '专注度量',
          authority: ['ROLE_3'],
        },
      },
    ],
  },
  {
    name: 'ZhiyuAiGovernanceGroup',
    path: '/zhiyu/ai',
    redirect: '/zhiyu/business/ai-governance',
    meta: {
      icon: 'lucide:bot',
      order: 7,
      title: 'AI 治理与审计',
      authority: ['ROLE_3'],
    },
    children: [
      {
        name: 'ZhiyuAiGovernance',
        path: '/zhiyu/business/ai-governance',
        component: () => import('#/views/zhiyu/ai-governance.vue'),
        meta: {
          icon: 'lucide:shield-check',
          title: 'AI 治理',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuAuditLogs',
        path: '/zhiyu/business/audit-logs',
        component: () => import('#/views/zhiyu/audit-logs.vue'),
        meta: {
          icon: 'lucide:scroll-text',
          title: 'AI 审计日志',
          authority: ['ROLE_3'],
        },
      },
    ],
  },
  {
    name: 'ZhiyuSystem',
    path: '/zhiyu/system',
    redirect: '/zhiyu/runtime-config',
    meta: {
      icon: 'lucide:settings-2',
      order: 8,
      title: '系统管理',
      authority: ['ROLE_3'],
    },
    children: [
      {
        name: 'ZhiyuRuntimeConfig',
        path: '/zhiyu/runtime-config',
        component: () => import('#/views/zhiyu/runtime-config.vue'),
        meta: {
          icon: 'lucide:sliders-horizontal',
          title: '推荐与陪伴配置',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuSpeechConfig',
        path: '/zhiyu/speech-config',
        component: () => import('#/views/zhiyu/speech-config.vue'),
        meta: {
          icon: 'lucide:mic',
          title: '语音服务',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuSystemHealth',
        path: '/zhiyu/system-health',
        component: () => import('#/views/zhiyu/system-health.vue'),
        meta: {
          icon: 'lucide:activity',
          title: '运行健康',
          authority: ['ROLE_3'],
        },
      },
      {
        name: 'ZhiyuOperationLogs',
        path: '/zhiyu/operation-logs',
        component: () => import('#/views/zhiyu/operation-logs.vue'),
        meta: {
          icon: 'lucide:history',
          title: '操作记录',
          authority: ['ROLE_3'],
        },
      },
    ],
  },
];

export default routes;
