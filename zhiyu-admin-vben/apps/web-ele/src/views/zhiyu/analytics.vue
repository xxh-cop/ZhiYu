<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { getDashboardStats } from '#/api/zhiyu/admin';

/** 色板（薄荷青 v3.0） */
const CHART_COLORS = {
  primary: '#2D9B8F',
  accent: '#5BC4A8',
  danger: '#FF6B6B',
  warning: '#FFB547',
  border: '#E5E7EB',
  text: '#1F2937',
  muted: '#9CA3AF',
  palette: ['#2D9B8F', '#5BC4A8', '#FFB547', '#FF6B6B', '#94A3B8'],
};

/** KPI 卡片数据 */
const kpiCards = ref([
  { title: '总用户', value: '-', unit: '人' },
  { title: '总作品', value: '-', unit: '件' },
  { title: '待审项目', value: '-', unit: '个' },
  { title: '待处理关怀', value: '-', unit: '条' },
]);

/** 加载 / 错误状态 */
const loading = ref(false);
const errorMsg = ref('');

/** 图表 ref */
const lineChartRef = ref<EchartsUIType>();
const funnelChartRef = ref<EchartsUIType>();

const { renderEcharts: renderLine } = useEcharts(lineChartRef);
const { renderEcharts: renderFunnel } = useEcharts(funnelChartRef);

/** 拉取数据 */
async function fetchData() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await getDashboardStats();

    // 更新 KPI 卡片
    kpiCards.value = [
      { title: '总用户', value: String(data.totalUsers ?? '-'), unit: '人' },
      { title: '总作品', value: String(data.totalWorks ?? '-'), unit: '件' },
      { title: '待审项目', value: String(data.pendingProjects ?? '-'), unit: '个' },
      { title: '待处理关怀', value: String(data.pendingCareReminders ?? '-'), unit: '条' },
    ];

    // 更新折线图
    const trend = data.userTrend7d || [];
    const dates = trend.map((item: any) => {
      const parts = item.date ? item.date.split('-') : [];
      return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : (item.date || '-');
    });
    const counts = trend.map((item: any) => item.count || 0);

    renderLine({
      title: { text: '近 7 天新增用户', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
      tooltip: { trigger: 'axis' },
      grid: { left: '8%', right: '8%', bottom: '10%', top: '18%' },
      xAxis: {
        type: 'category',
        data: dates.length > 0 ? dates : ['暂无数据'],
        axisLine: { lineStyle: { color: CHART_COLORS.border } },
        axisLabel: { color: CHART_COLORS.muted },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        splitLine: { lineStyle: { color: CHART_COLORS.border } },
        axisLabel: { color: CHART_COLORS.muted },
      },
      series: [
        {
          name: '新增用户',
          type: 'line',
          smooth: true,
          data: counts.length > 0 ? counts : [0],
          itemStyle: { color: CHART_COLORS.primary },
          lineStyle: { width: 2, color: CHART_COLORS.primary },
          areaStyle: { color: 'rgba(45, 155, 143, 0.08)' },
        },
      ],
    });

  } catch (e: any) {
    errorMsg.value = (e && e.message) || '加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  // 疗愈闭环漏斗（横向条形图模拟，Mock 数据）
  renderFunnel({
    title: { text: '疗愈闭环转化', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '15%', right: '10%', bottom: '10%', top: '18%' },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: CHART_COLORS.border } },
      axisLabel: { color: CHART_COLORS.muted },
    },
    yAxis: {
      type: 'category',
      data: ['情绪表达', '手作推荐', '专注创作', '作品分享'],
      axisLine: { lineStyle: { color: CHART_COLORS.border } },
      axisLabel: { color: CHART_COLORS.muted },
    },
    series: [
      {
        type: 'bar',
        // TODO: 待后端支持后替换为真实数据
        data: [
          { value: 1209, itemStyle: { color: CHART_COLORS.palette[0] } },
          { value: 876, itemStyle: { color: CHART_COLORS.palette[1] } },
          { value: 542, itemStyle: { color: CHART_COLORS.palette[2] } },
          { value: 342, itemStyle: { color: CHART_COLORS.palette[3] } },
        ],
        barWidth: '60%',
        label: { show: true, position: 'right', color: CHART_COLORS.muted },
      },
    ],
  });

  // 拉取真实数据
  await fetchData();
});
</script>

<template>
  <div v-loading="loading" class="flex flex-col gap-4 p-4">
    <h2 class="m-0 text-xl font-semibold">数据看板</h2>

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      :closable="false"
      show-icon
    />

    <!-- KPI 卡片 -->
    <el-row :gutter="16">
      <el-col v-for="card in kpiCards" :key="card.title" :span="6">
        <el-card shadow="never">
          <div class="text-sm text-gray-500">{{ card.title }}</div>
          <div class="mt-2 text-2xl font-semibold" :style="{ color: CHART_COLORS.primary }">
            {{ card.value }}<span class="ml-1 text-sm font-normal text-gray-400">{{ card.unit }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 折线图：近 7 天新增用户 -->
    <el-card shadow="never">
      <EchartsUI ref="lineChartRef" height="320px" />
    </el-card>

    <!-- 底部图表：漏斗（Mock，待后端支持） -->
    <el-card shadow="never">
      <EchartsUI ref="funnelChartRef" height="320px" />
      <p class="mt-2 text-center text-xs text-gray-400">漏斗图暂为 Mock 数据，待后端支持</p>
    </el-card>
  </div>
</template>
