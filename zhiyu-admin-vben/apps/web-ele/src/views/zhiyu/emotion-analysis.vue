<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { getDashboardStats } from '#/api/zhiyu/admin';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import ChartCard from '#/components/chart-card/ChartCard.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import { useChartTheme } from '#/composables/use-chart-theme';

const { theme } = useChartTheme();
const pieChartRef = ref<EchartsUIType>();
const { renderEcharts: renderPie } = useEcharts(pieChartRef);

const loading = ref(false);
const error = ref('');
const moodData = ref<Array<{ count: number; mood: string }>>([]);
let request = 0;

const moodTableData = computed(() => {
  const total = moodData.value.reduce((sum, item) => sum + item.count, 0);
  return moodData.value.map((item) => ({
    mood: item.mood || '未知',
    count: item.count,
    percent: total > 0 ? ((item.count / total) * 100).toFixed(1) : '0.0',
  }));
});

function renderChart() {
  if (!moodData.value.length) {
    return;
  }
  const c = theme.value;
  const pieData = moodData.value.map((item, index) => ({
    name: item.mood || '未知',
    value: item.count,
    itemStyle: { color: c.colors[index % c.colors.length] },
  }));

  renderPie({
    animation: false,
    aria: {
      enabled: true,
      label: { description: '情绪分布。按会话中记录的情绪标签汇总。' },
    },
    color: c.colors,
    legend: {
      bottom: '4%',
      icon: 'circle',
      itemHeight: 8,
      itemWidth: 8,
      textStyle: { color: c.text },
    },
    series: [
      {
        type: 'pie',
        avoidLabelOverlap: true,
        center: ['50%', '46%'],
        data: pieData,
        itemStyle: {
          borderColor: c.background,
          borderRadius: 4,
          borderWidth: 2,
        },
        label: {
          color: c.text,
          formatter: '{b}\n{d}%',
          overflow: 'break',
          show: true,
          width: 96,
        },
        labelLine: { lineStyle: { color: c.border }, show: true },
        radius: ['40%', '64%'],
      },
    ],
    tooltip: {
      backgroundColor: c.tooltipBackground,
      borderColor: c.tooltipBorder,
      formatter: '{b}: {c} ({d}%)',
      renderMode: 'richText',
      textStyle: { color: c.text },
      trigger: 'item',
    },
  });
}

watch(theme, () => nextTick(renderChart));

async function load() {
  const turn = ++request;
  loading.value = true;
  error.value = '';
  try {
    const data = await getDashboardStats();
    if (turn !== request) {
      return;
    }
    moodData.value = data.moodDistribution || [];
  } catch {
    if (turn === request) {
      error.value = '情绪数据暂未读取成功，请稍后重试。';
    }
  } finally {
    if (turn === request) {
      loading.value = false;
    }
  }
  await nextTick();
  if (turn === request && !error.value) {
    renderChart();
  }
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page emotion-page">
    <BusinessPageHeader
      title="情绪分析"
      description="按会话中记录的情绪标签汇总，仅展示统计结果；不用于诊断或疗效判断。"
      :loading="loading"
      @refresh="load"
    />
    <DataErrorBanner
      v-if="error && moodData.length"
      :error="error"
      :retrying="loading"
      @retry="load"
    />
    <DataState
      v-else-if="error"
      :error="error"
      :loading="loading"
      empty-text="暂无情绪分布数据"
      @retry="load"
    />
    <div v-else class="emotion-grid">
      <ChartCard
        title="情绪分布"
        description="按会话中记录的情绪标签汇总，仅展示统计结果。"
        :empty="!loading && !moodData.length"
        empty-text="暂无情绪分布数据"
        empty-hint="完成一次对话后，这里会呈现情绪分布"
        :loading="loading && !moodData.length"
        @retry="load"
      >
        <EchartsUI ref="pieChartRef" height="360px" />
      </ChartCard>
      <section class="emotion-table-card">
        <header class="card-header">
          <span class="card-title">分布明细</span>
        </header>
        <el-table v-loading="loading" :data="moodTableData" stripe>
          <template #empty>
            <DataState
              :loading="loading"
              empty-text="暂无情绪分布数据"
              @retry="load"
            />
          </template>
          <el-table-column prop="mood" label="情绪" />
          <el-table-column prop="count" label="会话数" width="120" />
          <el-table-column label="占比" width="120">
            <template #default="{ row }">
              {{ row.percent }}%
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
    <p class="emotion-note">本系统不能替代专业心理咨询</p>
  </div>
</template>

<style scoped>
.emotion-page {
  padding: 0;
}

.emotion-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.emotion-table-card {
  min-width: 0;
  padding: 16px 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.emotion-table-card :deep(.el-table) {
  --el-table-header-text-color: var(--el-text-color-regular);
}

.card-header {
  margin-bottom: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.emotion-note {
  margin-top: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

@media (max-width: 1280px) {
  .emotion-grid {
    grid-template-columns: 1fr;
  }
}
</style>
