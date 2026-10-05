<template>
  <div class="p-4">
    <div class="mb-4">
      <h2 class="text-xl font-semibold">{{ greeting }}</h2>
      <p class="mt-1 text-sm text-gray-500">{{ today }}</p>
    </div>

    <div class="mb-4">
      <h3 class="mb-2 text-base font-medium">待办事项</h3>
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <el-card shadow="hover" class="cursor-pointer" @click="goTo('/zhiyu/business/audit')">
          <div class="text-sm text-gray-500">待审核项目</div>
          <div class="mt-1 text-3xl font-semibold">{{ stats.pendingProjects }}</div>
        </el-card>
        <el-card shadow="hover" class="cursor-pointer" @click="goTo('/zhiyu/business/care-reminders')">
          <div class="text-sm text-gray-500">待处理关怀</div>
          <div class="mt-1 text-3xl font-semibold">{{ stats.pendingCareReminders }}</div>
        </el-card>
      </div>
    </div>

    <div>
      <h3 class="mb-2 text-base font-medium">快捷入口</h3>
      <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <el-button @click="goTo('/zhiyu/business/audit')">内容审核</el-button>
        <el-button @click="goTo('/zhiyu/business/care-reminders')">关怀提醒</el-button>
        <el-button @click="goTo('/zhiyu/data-center/analytics')">数据看板</el-button>
        <el-button @click="goTo('/zhiyu/data-center/emotion')">情绪分析</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getDashboardStats } from '#/api/zhiyu/admin';

const router = useRouter();

const stats = ref({ pendingProjects: 0, pendingCareReminders: 0 });

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const today = computed(() => {
  const d = new Date();
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
});

function goTo(path: string) {
  router.push(path);
}

onMounted(async () => {
  try {
    const data = await getDashboardStats();
    stats.value = {
      pendingProjects: data.pendingProjects ?? 0,
      pendingCareReminders: data.pendingCareReminders ?? 0,
    };
  } catch (e) {
    // 拦截器已提示
  }
});
</script>
