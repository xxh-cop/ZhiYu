<script setup>
import { ref, computed, onMounted } from 'vue'

import { useUserStore } from '../stores/user'
import { getDashboardStats } from '../api/admin'

const userStore = useUserStore()

const nickname = computed(
  () => (userStore.userInfo && userStore.userInfo.nickname) || '管理员',
)

/** KPI 卡片 */
const kpiCards = ref([
  { title: '总用户', value: '-' },
  { title: '总作品', value: '-' },
  { title: '待审项目', value: '-' },
  { title: '待处理关怀', value: '-' },
])

const loading = ref(false)
const errorMsg = ref('')

async function fetchStats() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getDashboardStats()
    kpiCards.value = [
      { title: '总用户', value: data.totalUsers ?? '-' },
      { title: '总作品', value: data.totalWorks ?? '-' },
      { title: '待审项目', value: data.pendingProjects ?? '-' },
      { title: '待处理关怀', value: data.pendingCareReminders ?? '-' },
    ]
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<template>
  <div class="dashboard-page" v-loading="loading">
    <h2 class="page-title">欢迎使用织愈管理端</h2>
    <p class="page-tip">
      你好，{{ nickname }}。以下是平台当前的关键指标概览。
    </p>

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      :closable="false"
      show-icon
    />

    <el-row :gutter="16">
      <el-col v-for="card in kpiCards" :key="card.title" :span="6">
        <el-card class="dash-card" shadow="never">
          <div class="dash-card-title">{{ card.title }}</div>
          <div class="dash-card-value">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-16);
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--zy-text);
}

.page-tip {
  margin: 0;
  font-size: 13px;
  color: #8a8378;
}

.dash-card {
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);
}

.dash-card-title {
  font-size: 13px;
  color: #8a8378;
}

.dash-card-value {
  margin-top: var(--zy-space-8);
  font-size: 22px;
  font-weight: 600;
  color: var(--zy-primary);
}
</style>
