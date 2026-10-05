import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: 1,
      title: '织愈',
    },
    name: 'Zhiyu',
    path: '/zhiyu',
    children: [
      {
        name: 'ZhiyuDashboard',
        path: 'dashboard',
        component: () => import('#/views/zhiyu/dashboard.vue'),
        meta: {
          icon: 'lucide:home',
          title: '工作台',
        },
      },
      {
        name: 'ZhiyuAudit',
        path: 'audit',
        component: () => import('#/views/zhiyu/audit.vue'),
        meta: {
          icon: 'lucide:clipboard-check',
          title: '内容审核',
        },
      },
      {
        name: 'ZhiyuCareReminders',
        path: 'care-reminders',
        component: () => import('#/views/zhiyu/care-reminders.vue'),
        meta: {
          icon: 'lucide:heart',
          title: '关怀提醒',
        },
      },
      {
        name: 'ZhiyuAnalytics',
        path: 'analytics',
        component: () => import('#/views/zhiyu/analytics.vue'),
        meta: {
          icon: 'lucide:bar-chart-3',
          title: '数据看板',
        },
      },
    ],
  },
];

export default routes;
