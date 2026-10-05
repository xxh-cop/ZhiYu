<script setup>
import { ref, onMounted, onUnmounted, shallowRef, watch } from 'vue'

import { getDashboardStats } from '../api/admin'

/** KPI 卡片数据 */
const kpiCards = ref([
  { title: '总用户', value: '-', unit: '人' },
  { title: '总作品', value: '-', unit: '件' },
  { title: '待审项目', value: '-', unit: '个' },
  { title: '待处理关怀', value: '-', unit: '条' },
])

/** 加载 / 错误状态 */
const loading = ref(false)
const errorMsg = ref('')

/** 后端返回的原始数据（用于图表更新） */
const statsData = ref(null)

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

/** 拉取数据 */
async function fetchData() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getDashboardStats()
    statsData.value = data

    // 更新 KPI 卡片
    kpiCards.value = [
      { title: '总用户', value: data.totalUsers ?? '-', unit: '人' },
      { title: '总作品', value: data.totalWorks ?? '-', unit: '件' },
      { title: '待审项目', value: data.pendingProjects ?? '-', unit: '个' },
      { title: '待处理关怀', value: data.pendingCareReminders ?? '-', unit: '条' },
    ]

    // 更新图表
    updateLineChart(data.userTrend7d || [])
    updatePieChart(data.moodDistribution || [])
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 更新折线图 */
function updateLineChart(trend) {
  if (!lineChart.value) return

  const dates = trend.map((item) => {
    const parts = item.date ? item.date.split('-') : []
    return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : (item.date || '-')
  })
  const counts = trend.map((item) => item.count || 0)

  lineChart.value.setOption({
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
        areaStyle: { color: 'rgba(139, 111, 71, 0.08)' },
      },
    ],
  })
}

/** 更新饼图 */
function updatePieChart(moodDist) {
  if (!pieChart.value) return

  const pieData = moodDist.map((item, idx) => ({
    name: item.mood || '未知',
    value: item.count || 0,
    itemStyle: { color: CHART_COLORS.palette[idx % CHART_COLORS.palette.length] },
  }))

  pieChart.value.setOption({
    title: { text: '情绪分布', left: 'center', textStyle: { fontSize: 14, color: CHART_COLORS.text } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '5%', textStyle: { color: CHART_COLORS.muted } },
    series: [
      {
        type: 'pie',
        radius: ['35%', '60%'],
        center: ['50%', '45%'],
        data: pieData.length > 0 ? pieData : [{ name: '暂无数据', value: 1, itemStyle: { color: CHART_COLORS.border } }],
        label: { color: CHART_COLORS.muted },
      },
    ],
  })
}

onMounted(async () => {
  const echarts = await import('echarts')

  /* ---- 初始化三个图表实例 ---- */
  lineChart.value = echarts.init(lineChartRef.value)
  pieChart.value = echarts.init(pieChartRef.value)

  /* ---- 漏斗图：保留 Mock，待后端支持 ---- */
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
        // TODO: 待后端支持后替换为真实数据
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

  // 拉取真实数据
  await fetchData()
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
  <div class="analytics-page" v-loading="loading">
    <h2 class="page-title">数据看板</h2>

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 0;"
    />

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

    <!-- 底部图表：漏斗（Mock，待后端支持） -->
    <el-card class="chart-card" shadow="never">
      <div ref="funnelChartRef" class="chart-container" />
      <p class="mock-tip">漏斗图暂为 Mock 数据，待后端支持</p>
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

.mock-tip {
  margin: var(--zy-space-8) 0 0;
  font-size: 12px;
  color: #a49c90;
  text-align: center;
}
</style>
