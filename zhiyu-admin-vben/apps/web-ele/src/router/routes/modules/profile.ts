import type { RouteRecordRaw } from 'vue-router';

/**
 * 个人中心路由（P0-4.5 修复）。
 *
 * 此前 Profile 挂在 coreRoutes 顶级，导致登录后首落 /profile/base 时
 * 守卫直接放行、跳过 generateAccess，accessMenus 为空 → 侧边栏无菜单。
 * 移入动态路由后挂到 Root.children 下（与 /zhiyu/** 同级），
 * 首次访问即触发动态路由与菜单生成；meta.hideInMenu 保证不进菜单。
 */
const routes: RouteRecordRaw[] = [
  {
    name: 'Profile',
    path: '/profile',
    redirect: '/profile/base',
    meta: {
      hideInMenu: true,
      hideInTab: true,
      title: '个人中心',
    },
    children: [
      {
        name: 'ProfileBase',
        path: 'base',
        component: () => import('#/views/_core/profile/index.vue'),
        meta: {
          title: '基本设置',
        },
      },
    ],
  },
];

export default routes;
