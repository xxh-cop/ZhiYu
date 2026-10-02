<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import http from '../utils/request'
import { useUserStore } from '../stores/user'

/** 可进入管理端的角色：1 传承人 / 2 咨询师 / 3 管理员 */
const ADMIN_ROLES = [1, 2, 3]

const router = useRouter()
const userStore = useUserStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const errMsg = ref('')

async function handleLogin() {
  errMsg.value = ''
  const username = (form.value.username || '').trim()
  const password = form.value.password || ''
  if (!username || !password) {
    errMsg.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const data = await http.post('/api/auth/login', { username, password })

    // 学生账号（role=0）不允许进入管理端：不落 token、不跳转
    if (!ADMIN_ROLES.includes(data.role)) {
      userStore.clearAuth()
      errMsg.value = '请使用学生端'
      ElMessage.warning('请使用学生端')
      return
    }

    userStore.setToken(data.token)
    userStore.setUserInfo(data)
    ElMessage.success('登录成功')
    router.replace('/dashboard')
  } catch (e) {
    errMsg.value = (e && e.message) || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <h1 class="login-title">织愈管理端</h1>
      <p class="login-subtitle">非遗艺术疗愈平台 · 内容与关怀管理</p>

      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-alert
          v-if="errMsg"
          class="login-alert"
          :title="errMsg"
          type="error"
          :closable="false"
          show-icon
        />

        <el-button
          class="login-button"
          type="primary"
          size="large"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>
    </el-card>

    <p class="login-slogan">用一针一线，缝补青春的焦虑</p>
    <p class="login-disclaimer">本系统不能替代专业心理咨询</p>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: var(--zy-space-24);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--zy-space-12);
  background-color: var(--zy-bg);
}

.login-card {
  width: 100%;
  max-width: 380px;
  background-color: #ffffff;
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);

  :deep(.el-card__body) {
    padding: var(--zy-space-32) var(--zy-space-24);
  }
}

.login-title {
  margin: 0 0 var(--zy-space-8);
  font-size: 22px;
  font-weight: 600;
  text-align: center;
  color: var(--zy-primary);
}

.login-subtitle {
  margin: 0 0 var(--zy-space-24);
  font-size: 13px;
  text-align: center;
  color: #8a8378;
}

.login-alert {
  margin-bottom: var(--zy-space-16);
}

.login-button {
  width: 100%;
  letter-spacing: 4px;
}

.login-slogan {
  margin: var(--zy-space-8) 0 0;
  font-size: 14px;
  color: var(--zy-primary);
}

.login-disclaimer {
  margin: 0;
  font-size: 12px;
  color: #a49c90;
}
</style>
