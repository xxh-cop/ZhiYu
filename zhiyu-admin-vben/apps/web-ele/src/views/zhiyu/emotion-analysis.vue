<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { getDashboardStats } from '#/api/zhiyu/admin';

const { isDark } = usePreferences();

/* ── 色板（深靛蓝系，随主题动态切换） ── */
const C = computed(() => {
  const dark = isDark.value;
  return {
    primary: '#4F46E5',
    primaryLight: '#6366F1',
    accent: '#F59E0B',
    danger: '#EF4444',
    success: '#10B981',
    border: dark ? '#2A2A2A' : '#E5E7EB',
    title: dark ? '#E5E7EB' : '#1F2937',
    text: dark ? '#B0B0B0' : '#4B5563',
    muted: dark ? '#6B7280' : '#9CA3AF',
    palette: ['#4F46E5', '#6366F1', '#F59E0B', '#EF4444', '#94A3B8'],
  };
});

const pieChartRef = ref<EchartsUIType>();
const { renderEcharts: renderPie } = useEcharts(pieChartRef);

const loading = ref(false);
const moodData = ref<Array<{ mood: string; count: number }>>([]);

const moodTableData = computed(() => {
  const total = moodData.value.reduce((sum, item) => sum + item.count, 0);
  return moodData.value.map((item) => ({
    mood: item.mood || '未知',
    count: item.count,
    percent: total > 0 ? ((item.count / total) * 100).toFixed(1) : '0.0',
  }));
});

/* ── 集中渲染饼图（供初始加载和主题切换复用） ── */
function renderChart() {
  const c = C.value;
  const pieData = moodData.value.map((item, idx) => ({
    name: item.mood || '未知',
    value: item.count,
    itemStyle: { color: c.palette[idx % c.palette.length] },
  }));

  renderPie({
    title: { text: '情绪分布', left: 'center', textStyle: { fontSize: 14, color: c.title } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '5%', textStyle: { color: c.muted } },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '45%'],
        data: pieData.length > 0 ? pieData : [{ name: '暂无数据', value: 1, itemStyle: { color: c.border } }],
        label: { color: c.muted },
        itemStyle: { borderColor: isDark.value ? '#1F1F1F' : '#fff', borderWidth: 2 },
      },
    ],
  });
}

/* ── 主题切换时重新渲染 ── */
watch(isDark, () => {
  nextTick(() => renderChart());
});

onMounted(async () => {
  loading.value = true;
  try {
    const data = await getDashboardStats();
    moodData.value = data.moodDistribution || [];
    renderChart();
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="emotion-page">
    <div class="emotion-grid">
      <div class="chart-card">
        <div class="card-header">
          <span class="card-title">情绪分布</span>
        </div>
        <EchartsUI ref="pieChartRef" height="360px" />
      </div>
      <div class="chart-card">
        <div class="card-header">
          <span class="card-title">分布明细</span>
        </div>
        <el-table :data="moodTableData" v-loading="loading" stripe>
          <el-table-column prop="mood" label="情绪" />
          <el-table-column prop="count" label="会话数" width="120" />
          <el-table-column label="占比" width="120">
            <template #default="{ row }">
              {{ row.percent }}%
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.emotion-page {
  padding: 16px;
}
.emotion-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart-card {
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
  padding: 16px 20px;
}
.card-header {
  margin-bottom: 12px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
}

@media (max-width: 1280px) {
  .emotion-grid {
    grid-template-columns: 1fr;
  }
}
</style>
