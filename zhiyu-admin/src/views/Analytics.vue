<script setup>
import { ref, onMounted, onUnmounted, shallowRef } from 'vue'

/** KPI 卡片数据 */
const kpiCards = [
  { title: '总用户', value: '1,286', unit: '人' },
  { title: '总作品', value: '342', unit: '件' },
  { title: '闭环完成率', value: '68.5', unit: '%' },
  { title: '心流时长占比', value: '42.3', unit: '%' },
]

/** 图表 DOM 引用 */
const lineChartRef = ref(null)
const pieChartRef = ref(null)
const funnelChartRef = ref(null)

/** 用 shallowRef 存 echarts 实例，避免深层响应式代理 */
const lineChart = shallowRef(null)
const pieChart = shallowRef(null)
const funnelChart = shallowRef(null)

/** 色板（与 ui-guidelines.md 一致） */
const CHART_COLORS = {
  primary: '#8B6F47',
  accent: '#C89B6E',
  danger: '#B85450',
  border: '#E5DDD0',
  text: '#2B2B2B',
  muted: '#8A8378',
  palette: ['#8B6F47', '#C89B6E', '#B85450', '#A49C90', '#6F5939'],
}

onMounted(async () => {
  const echarts = await import('echarts')

  /* ---- 折线图：近 7 天新增用户 ---- */
  lineChart.value = echarts.init(lineChartRef.value)
  lineChart.value.setOption({
    title: { text: '近 7 天新增用户', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
    tooltip: { trigger: 'axis' },
    grid: { left: '8%', right: '8%', bottom: '10%', top: '18%' },
    xAxis: {
      type: 'category',
      data: ['9/24', '9/25', '9/26', '9/27', '9/28', '9/29', '9/30'],
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
        data: [12, 18, 15, 22, 28, 31, 25],
        itemStyle: { color: CHART_COLORS.primary },
        lineStyle: { width: 2, color: CHART_COLORS.primary },
        areaStyle: { color: 'rgba(139, 111, 71, 0.08)' },
      },
    ],
  })

  /* ---- 饼图：情绪分布 ---- */
  pieChart.value = echarts.init(pieChartRef.value)
  pieChart.value.setOption({
    title: { text: '情绪分布', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '5%', textStyle: { color: CHART_COLORS.muted } },
    series: [
      {
        type: 'pie',
        radius: ['35%', '60%'],
        center: ['50%', '45%'],
        data: [
          { name: '焦虑', value: 386, itemStyle: { color: CHART_COLORS.palette[0] } },
          { name: '压力', value: 295, itemStyle: { color: CHART_COLORS.palette[1] } },
          { name: '低落', value: 218, itemStyle: { color: CHART_COLORS.palette[2] } },
          { name: '烦躁', value: 167, itemStyle: { color: CHART_COLORS.palette[3] } },
          { name: '疲惫', value: 143, itemStyle: { color: CHART_COLORS.palette[4] } },
        ],
        label: { color: CHART_COLORS.muted },
      },
    ],
  })

  /* ---- 漏斗图：疗愈闭环 ---- */
  funnelChart.value = echarts.init(funnelChartRef.value)
  funnelChart.value.setOption({
    title: { text: '疗愈闭环漏斗', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人' },
    series: [
      {
        type: 'funnel',
        left: '15%',
        right: '15%',
        top: '20%',
        bottom: '10%',
        data: [
          { name: '情绪表达', value: 1209, itemStyle: { color: CHART_COLORS.palette[0] } },
          { name: '手作推荐', value: 876, itemStyle: { color: CHART_COLORS.palette[1] } },
          { name: '专注创作', value: 542, itemStyle: { color: CHART_COLORS.palette[2] } },
          { name: '作品分享', value: 342, itemStyle: { color: CHART_COLORS.palette[3] } },
        ],
        label: { color: CHART_COLORS.text, position: 'inside' },
        labelLine: { show: false },
      },
    ],
  })

  window.addEventListener('resize', handleResize)
})

function handleResize() {
  lineChart.value?.resize()
  pieChart.value?.resize()
  funnelChart.value?.resize()
}

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  lineChart.value?.dispose()
  pieChart.value?.dispose()
  funnelChart.value?.dispose()
})
</script>

<template>
  <div class="analytics-page">
    <h2 class="page-title">数据看板</h2>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="card in kpiCards" :key="card.title" :span="6">
        <el-card class="kpi-card" shadow="never">
          <div class="kpi-title">{{ card.title }}</div>
          <div class="kpi-value">
            {{ card.value }}<span class="kpi-unit">{{ card.unit }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 中间图表：折线 + 饼图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card class="chart-card" shadow="never">
          <div ref="lineChartRef" class="chart-container" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card" shadow="never">
          <div ref="pieChartRef" class="chart-container" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部图表：漏斗 -->
    <el-card class="chart-card" shadow="never">
      <div ref="funnelChartRef" class="chart-container" />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.analytics-page {
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

.kpi-card,
.chart-card {
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);
}

.kpi-title {
  font-size: 13px;
  color: #8a8378;
}

.kpi-value {
  margin-top: var(--zy-space-8);
  font-size: 28px;
  font-weight: 600;
  color: var(--zy-primary);
}

.kpi-unit {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 400;
  color: #8a8378;
}

.chart-container {
  width: 100%;
  height: 320px;
}
</style>
