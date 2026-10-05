import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:layout-dashboard', order: 1, title: '工作台' },
    name: 'ZhiyuDashboard',
    path: '/zhiyu/dashboard',
    component: () => import('#/views/zhiyu/dashboard.vue'),
  },
  {
    meta: { icon: 'lucide:bar-chart-3', order: 2, title: '数据中心' },
    name: 'ZhiyuDataCenter',
    path: '/zhiyu/data-center',
    children: [
      {
        name: 'ZhiyuAnalytics',
        path: 'analytics',
        component: () => import('#/views/zhiyu/analytics.vue'),
        meta: { icon: 'lucide:line-chart', title: '数据看板' },
      },
      {
        name: 'ZhiyuEmotionAnalysis',
        path: 'emotion',
        component: () => import('#/views/zhiyu/emotion-analysis.vue'),
        meta: { icon: 'lucide:heart-pulse', title: '情绪分析' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:sparkles', order: 3, title: '织愈业务' },
    name: 'ZhiyuBusiness',
    path: '/zhiyu/business',
    children: [
      {
        name: 'ZhiyuAudit',
        path: 'audit',
        component: () => import('#/views/zhiyu/audit.vue'),
        meta: { icon: 'lucide:clipboard-check', title: '内容审核' },
      },
      {
        name: 'ZhiyuCareReminders',
        path: 'care-reminders',
        component: () => import('#/views/zhiyu/care-reminders.vue'),
        meta: { icon: 'lucide:heart', title: '关怀提醒' },
      },
    ],
  },
];

export default routes;
