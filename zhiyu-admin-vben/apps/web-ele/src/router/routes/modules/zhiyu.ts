import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
 {name:'ZhiyuRuntimeConfig',path:'/zhiyu/runtime-config',component:()=>import('#/views/zhiyu/runtime-config.vue'),meta:{title:'推荐与陪伴配置',icon:'lucide:sliders-horizontal',order:7,authority:['ROLE_3']}},
 {name:'ZhiyuComments',path:'/zhiyu/comments',component:()=>import('#/views/zhiyu/comments.vue'),meta:{title:'评论审核',icon:'lucide:message-square-check',order:5,authority:['ROLE_3']}},
 {name:'ZhiyuSpeechConfig',path:'/zhiyu/speech-config',component:()=>import('#/views/zhiyu/speech-config.vue'),meta:{title:'语音服务',icon:'lucide:mic',order:7,authority:['ROLE_3']}},
 {name:'ZhiyuCampus',path:'/zhiyu/campus',component:()=>import('#/views/zhiyu/campus.vue'),meta:{title:'校园身份审核',icon:'lucide:graduation-cap',order:5,authority:['ROLE_3']}},
 {name:'ZhiyuOperationLogs',path:'/zhiyu/operation-logs',component:()=>import('#/views/zhiyu/operation-logs.vue'),meta:{title:'操作记录',icon:'lucide:history',order:6,authority:['ROLE_3']}},
 {name:'ZhiyuPrograms',path:'/zhiyu/programs',component:()=>import('#/views/zhiyu/programs.vue'),meta:{title:'活动与预约',icon:'lucide:calendar-days',order:4,authority:['ROLE_1','ROLE_2','ROLE_3']}},
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
        meta: { icon: 'lucide:clipboard-check', title: '内容审核', authority: ['ROLE_3'] },
      },
      {
        name: 'ZhiyuCareReminders',
        path: 'care-reminders',
        component: () => import('#/views/zhiyu/care-reminders.vue'),
        meta: { icon: 'lucide:heart', title: '关怀提醒', authority: ['ROLE_2', 'ROLE_3'] },
      },
      {
        name: 'ZhiyuUsers',
        path: 'users',
        component: () => import('#/views/zhiyu/users.vue'),
        meta: { icon: 'lucide:users', title: '用户管理', authority: ['ROLE_3'] },
      },
      {
        name: 'ZhiyuAuditLogs',
        path: 'audit-logs',
        component: () => import('#/views/zhiyu/audit-logs.vue'),
        meta: { icon: 'lucide:scroll-text', title: 'AI审计日志', authority: ['ROLE_3'] },
      },
      {
        name: 'ZhiyuInheritors',
        path: 'inheritors',
        component: () => import('#/views/zhiyu/inheritors.vue'),
        meta: { icon: 'lucide:award', title: '传承人管理' },
      },
      {
        name: 'ZhiyuWorks',
        path: 'works',
        component: () => import('#/views/zhiyu/works.vue'),
        meta: { icon: 'lucide:image', title: '作品管理' },
      },
      {
        name: 'ZhiyuGroups',
        path: 'groups',
        component: () => import('#/views/zhiyu/groups.vue'),
        meta: { icon: 'lucide:users-round', title: '小组管理' },
      },
    ],
  },
];

export default routes;
