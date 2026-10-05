<template>
  <div class="p-4">
    <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
      <el-card v-for="card in kpiCards" :key="card.title" shadow="hover">
        <div class="text-sm text-gray-500">{{ card.title }}</div>
        <div class="mt-2 text-2xl font-semibold">
          {{ card.value }}<span class="ml-1 text-sm text-gray-400">{{ card.unit }}</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { getDashboardStats } from '#/api/zhiyu/admin';

const kpiCards = ref([
  { title: '总用户', value: '-', unit: '人' },
  { title: '总作品', value: '-', unit: '件' },
  { title: '待审项目', value: '-', unit: '个' },
  { title: '待处理关怀', value: '-', unit: '条' },
]);

onMounted(async () => {
  try {
    const data = await getDashboardStats();
    kpiCards.value = [
      { title: '总用户', value: String(data.totalUsers ?? '-'), unit: '人' },
      { title: '总作品', value: String(data.totalWorks ?? '-'), unit: '件' },
      { title: '待审项目', value: String(data.pendingProjects ?? '-'), unit: '个' },
      { title: '待处理关怀', value: String(data.pendingCareReminders ?? '-'), unit: '条' },
    ];
  } catch {
    // requestClient 拦截器已弹错误提示
  }
});
</script>
