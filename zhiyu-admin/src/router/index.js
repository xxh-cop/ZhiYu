import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '../stores/user'

/** 可进入管理端的角色：1 传承人 / 2 咨询师 / 3 管理员（0 学生 → 请使用学生端） */
const ADMIN_ROLES = [1, 2, 3]

const routes = [
  { path: '/', redirect: '/dashboard' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'audit',
        name: 'audit',
        component: () => import('../views/Audit.vue'),
        meta: { title: '内容审核' },
      },
      {
        path: 'care-reminders',
        name: 'care-reminders',
        component: () => import('../views/CareReminders.vue'),
        meta: { title: '关怀提醒' },
      },
      {
        path: 'analytics',
        name: 'analytics',
        component: () => import('../views/Analytics.vue'),
        meta: { title: '数据看板' },
      },
      {
        path: 'inheritors',
        name: 'inheritors',
        component: () => import('../views/Inheritors.vue'),
        meta: { title: '传承人管理' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/Users.vue'),
        meta: { title: '用户管理' },
      },
    ],
  },
  // 兜底：未匹配路径回登录页
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 严格模式守卫：每次进入受保护路由都调 GET /api/auth/me 校验，
 * 不信任前端解出的 JWT payload（与学生端「只解 payload 做跳转」不同）。
 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    return true
  }

  if (!userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  try {
    const info = await userStore.fetchUserInfo()
    if (!ADMIN_ROLES.includes(info && info.role)) {
      userStore.clearAuth()
      ElMessage.warning('请使用学生端')
      return { path: '/login' }
    }
    return true
  } catch (e) {
    // 401/403 已由 request.js 统一处理（清 token + 回登录页）
    userStore.clearAuth()
    return { path: '/login' }
  }
})

export default router
