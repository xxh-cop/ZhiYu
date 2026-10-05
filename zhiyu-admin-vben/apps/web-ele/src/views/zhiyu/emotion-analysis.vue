<template>
  <div class="p-4">
    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <el-card shadow="hover">
        <template #header>
          <span class="font-medium">情绪分布</span>
        </template>
        <div ref="pieChartRef" style="height: 360px"></div>
      </el-card>
      <el-card shadow="hover">
        <template #header>
          <span class="font-medium">分布明细</span>
        </template>
        <el-table :data="moodTableData" v-loading="loading" stripe>
          <el-table-column prop="mood" label="情绪" />
          <el-table-column prop="count" label="会话数" width="120" />
          <el-table-column label="占比" width="120">
            <template #default="{ row }">
              {{ row.percent }}%
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';
import { computed, onMounted, ref } from 'vue';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { getDashboardStats } from '#/api/zhiyu/admin';

const CHART_COLORS = {
  border: '#E5E7EB',
  text: '#1F2937',
  muted: '#9CA3AF',
  palette: ['#2D9B8F', '#5BC4A8', '#FFB547', '#FF6B6B', '#94A3B8'],
};

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

onMounted(async () => {
  loading.value = true;
  try {
    const data = await getDashboardStats();
    moodData.value = data.moodDistribution || [];

    const pieData = moodData.value.map((item, idx) => ({
      name: item.mood || '未知',
      value: item.count,
      itemStyle: { color: CHART_COLORS.palette[idx % CHART_COLORS.palette.length] },
    }));

    renderPie({
      title: { text: '情绪分布', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: '5%', textStyle: { color: CHART_COLORS.muted } },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          center: ['50%', '45%'],
          data: pieData.length > 0 ? pieData : [{ name: '暂无数据', value: 1, itemStyle: { color: CHART_COLORS.border } }],
          label: { color: CHART_COLORS.muted },
        },
      ],
    });
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
});
</script>
