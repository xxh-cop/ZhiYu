<script setup>
import { computed, ref } from 'vue'

import { useUserStore } from '../stores/user'

const userStore = useUserStore()

const nickname = computed(
  () => (userStore.userInfo && userStore.userInfo.nickname) || '管理员',
)
const roleLabel = computed(() => userStore.roleLabel)

/** 菜单项目前全部指向 /dashboard（占位），点击只提示「开发中」 */
const menus = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'audit', label: '内容审核' },
  { key: 'care', label: '关怀提醒' },
  { key: 'stats', label: '数据看板' },
  { key: 'inheritor', label: '传承人管理' },
  { key: 'users', label: '用户管理' },
]

const activeMenu = ref('dashboard')

const placeholderCards = [
  { title: '待审作品', value: '—' },
  { title: '待处理关怀提醒', value: '—' },
  { title: '本周新增作品', value: '—' },
]

function handleSelect(index) {
  if (index === activeMenu.value) return
  const menu = menus.find((m) => m.key === index)
  // 占位：不真正切换路由，高亮回到 Dashboard
  ElMessage.info(`「${menu ? menu.label : index}」开发中`)
}

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
        :default-active="activeMenu"
        @select="handleSelect"
      >
        <el-menu-item v-for="item in menus" :key="item.key" :index="item.key">
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
        <h2 class="admin-title">欢迎使用织愈管理端</h2>
        <p class="admin-tip">
          内容审核、关怀提醒、数据看板将在 M2 里程碑接入；当前为 T0.6a 脚手架空壳。
        </p>

        <el-row :gutter="16">
          <el-col v-for="card in placeholderCards" :key="card.title" :span="8">
            <el-card class="admin-card" shadow="never">
              <div class="admin-card-title">{{ card.title }}</div>
              <div class="admin-card-value">{{ card.value }}</div>
            </el-card>
          </el-col>
        </el-row>
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

.admin-title {
  margin: 0 0 var(--zy-space-8);
  font-size: 20px;
  font-weight: 600;
  color: var(--zy-text);
}

.admin-tip {
  margin: 0 0 var(--zy-space-24);
  font-size: 13px;
  color: #8a8378;
}

.admin-card {
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);
}

.admin-card-title {
  font-size: 13px;
  color: #8a8378;
}

.admin-card-value {
  margin-top: var(--zy-space-8);
  font-size: 22px;
  font-weight: 600;
  color: var(--zy-primary);
}
</style>
