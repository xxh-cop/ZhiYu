import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:palette',
      order: 10,
      title: '我的传承',
      authority: ['ROLE_1'],
    },
    name: 'MyInheritance',
    path: '/my-inheritance',
    children: [
      {
        name: 'MyProjects',
        path: 'projects',
        component: () => import('#/views/zhiyu/my-projects.vue'),
        meta: {
          icon: 'lucide:folder-kanban',
          title: '我的项目',
          authority: ['ROLE_1'],
        },
      },
      {
        name: 'ProjectEdit',
        path: 'project-edit/:id',
        component: () => import('#/views/zhiyu/project-edit.vue'),
        meta: {
          title: '编辑项目',
          hideInMenu: true,
          authority: ['ROLE_1'],
        },
      },
      {
        name: 'ProjectNew',
        path: 'project-new',
        component: () => import('#/views/zhiyu/project-edit.vue'),
        meta: {
          title: '新建项目',
          hideInMenu: true,
          authority: ['ROLE_1'],
        },
      },
    ],
  },
];

export default routes;
