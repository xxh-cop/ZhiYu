import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useUserStore } from '../stores/user'

/** 可进入管理端的角色：1 传承人 / 2 咨询师 / 3 管理员（0 学生 → 请使用学生端） */
const ADMIN_ROLES = [1, 2, 3]

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '工作台' },
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
