import { defineStore } from 'pinia'

import http, { TOKEN_KEY } from '../utils/request'

const ROLE_LABELS = {
  0: '学生',
  1: '传承人',
  2: '咨询师',
  3: '管理员',
}

function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

/** 跳登录页（动态 import 以避免与 router → store → request 形成静态循环依赖） */
async function redirectToLogin() {
  const { default: router } = await import('../router')
  if (router.currentRoute.value.path !== '/login') {
    router.replace({ path: '/login' })
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: readToken(),
    userInfo: null,
  }),

  getters: {
    isLogin: (state) => !!state.token,
    roleLabel: (state) =>
      ROLE_LABELS[state.userInfo && state.userInfo.role] || '未知',
  },

  actions: {
    setToken(t) {
      this.token = t || ''
      if (this.token) {
        localStorage.setItem(TOKEN_KEY, this.token)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
    },

    setUserInfo(info) {
      this.userInfo = info || null
    },

    /** 只清本地登录态，不做跳转 */
    clearAuth() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
    },

    /** 调 GET /api/auth/me，成功后写入 userInfo 并返回（守卫据此判角色） */
    async fetchUserInfo() {
      const data = await http.get('/api/auth/me')
      this.setUserInfo(data)
      return data
    },

    async logout() {
      this.clearAuth()
      await redirectToLogin()
    },
  },
})
