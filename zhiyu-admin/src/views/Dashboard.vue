<script setup>
import { computed } from 'vue'

import { useUserStore } from '../stores/user'

const userStore = useUserStore()

const nickname = computed(
  () => (userStore.userInfo && userStore.userInfo.nickname) || '管理员',
)

const placeholderCards = [
  { title: '待审作品', value: '—' },
  { title: '待处理关怀提醒', value: '—' },
  { title: '本周新增作品', value: '—' },
]
</script>

<template>
  <div class="dashboard-page">
    <h2 class="page-title">欢迎使用织愈管理端</h2>
    <p class="page-tip">
      内容审核、关怀提醒、数据看板将在 M2 里程碑接入；当前为 T0.6b 页面骨架 + Mock 数据。
    </p>

    <el-row :gutter="16">
      <el-col v-for="card in placeholderCards" :key="card.title" :span="8">
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
