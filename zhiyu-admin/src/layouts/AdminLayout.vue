<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { useUserStore } from '../stores/user'

const route = useRoute()
const userStore = useUserStore()

const nickname = computed(
  () => (userStore.userInfo && userStore.userInfo.nickname) || '管理员',
)
const roleLabel = computed(() => userStore.roleLabel)

/** 侧边栏菜单：router 模式，index 即路由路径 */
const menus = [
  { index: '/dashboard', label: 'Dashboard' },
  { index: '/audit', label: '内容审核' },
  { index: '/care-reminders', label: '关怀提醒' },
  { index: '/analytics', label: '数据看板' },
  { index: '/inheritors', label: '传承人管理' },
  { index: '/users', label: '用户管理' },
]

const activeMenu = computed(() => route.path)

async function handleLogout() {
  await userStore.logout()
  ElMessage.success('已退出登录')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside class="admin-aside" width="220px">
      <div class="admin-logo">织愈管理端</div>
      <el-menu
        class="admin-menu"
        router
        :default-active="activeMenu"
      >
        <el-menu-item
          v-for="item in menus"
          :key="item.index"
          :index="item.index"
        >
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <span class="admin-welcome">
          欢迎，{{ nickname }}（{{ roleLabel }}）
        </span>
        <el-button type="primary" link @click="handleLogout">退出登录</el-button>
      </el-header>

      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  background-color: var(--zy-bg);
}

.admin-aside {
  background-color: #ffffff;
  border-right: 1px solid var(--zy-border);
}

.admin-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--zy-primary);
  border-bottom: 1px solid var(--zy-border);
}

.admin-menu {
  border-right: none;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #ffffff;
  border-bottom: 1px solid var(--zy-border);
}

.admin-welcome {
  font-size: 15px;
  color: var(--zy-text);
}

.admin-main {
  padding: var(--zy-space-32);
}
</style>
