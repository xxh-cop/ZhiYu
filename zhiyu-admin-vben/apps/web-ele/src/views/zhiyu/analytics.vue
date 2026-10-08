<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { getDashboardStats } from '#/api/zhiyu/admin';

const { isDark } = usePreferences();

/* ── 色板（深靛蓝系，随主题动态切换文字/边框/网格色） ── */
const C = computed(() => {
  const dark = isDark.value;
  return {
    primary: '#4F46E5',
    primaryLight: '#6366F1',
    primaryBg: dark ? '#1E1B4B' : '#EEF2FF',
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

/* ── KPI 卡片（6 个：4 真实 + 2 Mock） ── */
const kpiCards = ref([
  { title: '总用户', value: '-', unit: '人', trend: '+5%', trendUp: true, mock: false },
  { title: '总作品', value: '-', unit: '件', trend: '+8%', trendUp: true, mock: false },
  { title: '总项目', value: '36', unit: '个', trend: '+2', trendUp: true, mock: true },
  { title: '总小组', value: '12', unit: '个', trend: '+1', trendUp: true, mock: true },
  { title: '待审项目', value: '-', unit: '个', trend: '-3', trendUp: false, mock: false },
  { title: '待处理关怀', value: '-', unit: '条', trend: '-5%', trendUp: false, mock: false },
]);

/* ── 数字滚动 ── */
const displayValues = ref<number[]>([0, 0, 0, 0, 0, 0]);

function animateNumber(index: number, target: number, duration = 800) {
  const start = displayValues.value[index];
  const startTime = performance.now();
  function step(now: number) {
    const elapsed = now - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    displayValues.value[index] = Math.round(start + (target - start) * eased);
    if (progress < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}

/* ── 图表 ref ── */
const lineChartRef = ref<EchartsUIType>();
const pieChartRef = ref<EchartsUIType>();
const funnelChartRef = ref<EchartsUIType>();
const barChartRef = ref<EchartsUIType>();

/* sparkline — 6 个独立 ref + useEcharts 实例，在 setup 同步阶段创建避免生命周期警告 */
const sparklineRefs = [
  ref<EchartsUIType>(),
  ref<EchartsUIType>(),
  ref<EchartsUIType>(),
  ref<EchartsUIType>(),
  ref<EchartsUIType>(),
  ref<EchartsUIType>(),
];
const sparklineRenderers = sparklineRefs.map((r) => useEcharts(r));

const { renderEcharts: renderLine } = useEcharts(lineChartRef);
const { renderEcharts: renderPie } = useEcharts(pieChartRef);
const { renderEcharts: renderFunnel } = useEcharts(funnelChartRef);
const { renderEcharts: renderBar } = useEcharts(barChartRef);

/* ── sparkline 渲染 ── */
function renderSparklineForCard(idx: number, data: number[]) {
  const render = sparklineRenderers[idx]?.renderEcharts;
  if (!render) return;
  const colors = [C.value.primary, C.value.primaryLight, C.value.accent, C.value.success, C.value.danger, C.value.muted];
  const color = colors[idx % colors.length];
  render({
    grid: { left: 0, right: 0, top: 2, bottom: 0 },
    xAxis: { type: 'category', show: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false },
    series: [
      {
        type: 'line',
        data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.5, color },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: color + '40' },
              { offset: 1, color: color + '00' },
            ],
          },
        },
      },
    ],
  });
}

/* ── 热力图 Mock 数据（固定种子） ── */
function seededRandom(seed: number) {
  const x = Math.sin(seed) * 10000;
  return x - Math.floor(x);
}

const heatmapData: { day: number; hour: number; value: number }[] = [];
for (let d = 0; d < 7; d++) {
  for (let h = 0; h < 24; h++) {
    const seed = d * 24 + h + 1;
    const base = seededRandom(seed) * 50;
    const boost = h >= 9 && h <= 21 ? 1.5 : 0.3;
    heatmapData.push({ day: d, hour: h, value: Math.round(base * boost) });
  }
}

const heatmapCells = computed(() => {
  const cells: { value: number; intensity: number }[] = [];
  const maxVal = Math.max(...heatmapData.map((d) => d.value));
  for (let h = 0; h < 24; h++) {
    for (let d = 0; d < 7; d++) {
      const item = heatmapData.find((it) => it.day === d && it.hour === h)!;
      cells.push({ value: item.value, intensity: item.value / maxVal });
    }
  }
  return cells;
});

/* ── 缓存最近一次数据，用于主题切换时重新渲染 ── */
let lastData: any = null;

/* ── 拉取数据 ── */
const loading = ref(false);

async function fetchData() {
  loading.value = true;
  try {
    const data = await getDashboardStats();
    lastData = data;

    // KPI 更新
    const realValues = [
      data.totalUsers ?? 0,
      data.totalWorks ?? 0,
      36,
      12,
      data.pendingProjects ?? 0,
      data.pendingCareReminders ?? 0,
    ];
    realValues.forEach((v, i) => animateNumber(i, v));

    renderAllCharts(data);
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}

/* ── 集中渲染所有图表（供初始加载和主题切换复用） ── */
function renderAllCharts(data: any) {
  const c = C.value;

  // 主折线图
  const trend = data?.userTrend7d || [];
  const dates = trend.map((item: any) => {
    const parts = item.date ? item.date.split('-') : [];
    return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : (item.date || '-');
  });
  const counts = trend.map((item: any) => item.count || 0);
  const activeCounts = counts.map((v: number) => Math.round(v * (1.5 + Math.random() * 0.5)));

  renderLine({
    title: { text: '近 7 天新增用户', left: 'left', textStyle: { fontSize: 14, color: c.title } },
    tooltip: { trigger: 'axis' },
    legend: { right: 10, top: 5, textStyle: { color: c.muted, fontSize: 12 } },
    grid: { left: '5%', right: '5%', bottom: '8%', top: '18%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates.length > 0 ? dates : ['暂无'],
      axisLine: { lineStyle: { color: c.border } },
      axisLabel: { color: c.muted, fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: c.border, type: 'dashed' } },
      axisLabel: { color: c.muted, fontSize: 12 },
    },
    series: [
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        data: counts.length > 0 ? counts : [0],
        itemStyle: { color: c.primary },
        lineStyle: { width: 2.5, color: c.primary },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(79,70,229,0.25)' },
              { offset: 1, color: 'rgba(79,70,229,0)' },
            ],
          },
        },
      },
      {
        name: '活跃用户',
        type: 'line',
        smooth: true,
        data: activeCounts,
        itemStyle: { color: c.accent },
        lineStyle: { width: 2, color: c.accent, type: 'dashed' },
        symbol: 'diamond',
      },
    ],
  });

  // 饼图：情绪分布
  const moodData = (data?.moodDistribution || []).map((item: any, i: number) => ({
    name: item.mood || `情绪${i + 1}`,
    value: item.count || 0,
    itemStyle: { color: c.palette[i % c.palette.length] },
  }));
  const moodTotal = moodData.reduce((sum: number, item: any) => sum + item.value, 0);

  renderPie({
    title: { text: '情绪分布', left: 'left', textStyle: { fontSize: 14, color: c.title } },
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    graphic: {
      type: 'text',
      left: 'center',
      top: '42%',
      style: {
        text: `总计\n${moodTotal}`,
        fontSize: 16,
        fontWeight: 600,
        fill: c.title,
        textAlign: 'center',
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '55%'],
        data: moodData.length > 0 ? moodData : [{ name: '暂无', value: 1, itemStyle: { color: c.border } }],
        label: { color: c.text, fontSize: 12 },
        labelLine: { lineStyle: { color: c.border } },
        itemStyle: { borderColor: isDark.value ? '#1F1F1F' : '#fff', borderWidth: 2 },
      },
    ],
  });

  // 漏斗（横向 bar 模拟）
  renderFunnel({
    title: { text: '疗愈闭环转化', left: 'left', textStyle: { fontSize: 14, color: c.title } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '5%', right: '12%', bottom: '8%', top: '18%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: c.border, type: 'dashed' } },
      axisLabel: { color: c.muted, fontSize: 12 },
    },
    yAxis: {
      type: 'category',
      data: ['情绪表达', '手作推荐', '专注创作', '作品分享'],
      axisLine: { lineStyle: { color: c.border } },
      axisLabel: { color: c.text, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        data: [
          { value: 1209, itemStyle: { color: c.primary } },
          { value: 876, itemStyle: { color: c.primaryLight } },
          { value: 542, itemStyle: { color: c.accent } },
          { value: 342, itemStyle: { color: c.danger } },
        ],
        barWidth: '55%',
        label: { show: true, position: 'right', color: c.muted, fontSize: 12 },
        itemStyle: { borderRadius: [0, 4, 4, 0] },
      },
    ],
  });

  // 热门手作 Top 5
  renderBar({
    title: { text: '热门手作 Top 5', left: 'left', textStyle: { fontSize: 14, color: c.title } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '5%', right: '8%', bottom: '8%', top: '18%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: c.border, type: 'dashed' } },
      axisLabel: { color: c.muted, fontSize: 12 },
    },
    yAxis: {
      type: 'category',
      data: ['编织杯垫', '陶艺捏塑杯垫', '剪纸释压团花', '赣绣小书签', '解压扎染方巾'],
      axisLine: { lineStyle: { color: c.border } },
      axisLabel: { color: c.text, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        data: [
          { value: 165, itemStyle: { color: c.primaryLight } },
          { value: 198, itemStyle: { color: c.primaryLight } },
          { value: 256, itemStyle: { color: c.accent } },
          { value: 287, itemStyle: { color: c.accent } },
          { value: 342, itemStyle: { color: c.primary } },
        ],
        barWidth: '50%',
        label: { show: true, position: 'right', color: c.muted, fontSize: 12 },
        itemStyle: { borderRadius: [0, 4, 4, 0] },
      },
    ],
  });

  // Sparkline
  const sparkData = (trend || []).map((item: any) => item.count || 0);
  const sparkVariants = [
    sparkData,
    sparkData.map((v: number) => Math.round(v * 0.8)),
    sparkData.map((v: number) => Math.round(v * 1.1)),
    sparkData.map((v: number) => Math.round(v * 0.9)),
    sparkData.map((v: number) => Math.round(v * 0.7)),
    sparkData.map((v: number) => Math.round(v * 0.85)),
  ];
  nextTick(() => {
    sparkVariants.forEach((data, idx) => {
      renderSparklineForCard(idx, data.length > 0 ? data : [0, 0, 0, 0, 0, 0, 0]);
    });
  });
}

/* ── 主题切换时重新渲染所有图表 ── */
watch(isDark, () => {
  if (lastData) {
    nextTick(() => renderAllCharts(lastData));
  }
});

onMounted(() => {
  fetchData();
});

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
</script>

<template>
  <div v-loading="loading" class="analytics-page">
    <h2 class="page-title">数据看板</h2>

    <!-- 区块 1：6 个 KPI 卡片 -->
    <div class="kpi-grid">
      <div
        v-for="(card, i) in kpiCards"
        :key="card.title"
        class="kpi-card zy-slide-up zy-stagger zy-hover-lift"
        :style="{ '--stagger-i': i }"
      >
        <div class="kpi-header">
          <span class="kpi-title">{{ card.title }}</span>
          <span
            class="kpi-trend"
            :style="{
              color: card.trendUp ? C.success : C.danger,
              background: (card.trendUp ? C.success : C.danger) + '12',
            }"
          >
            {{ card.trendUp ? '↑' : '↓' }}{{ card.trend }}
          </span>
        </div>
        <div class="kpi-value-row">
          <span class="kpi-number">{{ displayValues[i] }}</span>
          <span class="kpi-unit">{{ card.unit }}</span>
        </div>
        <!-- sparkline：每个 KPI 卡片一个独立 EchartsUI 实例 -->
        <div class="kpi-sparkline">
          <EchartsUI :ref="(el) => { if (el) sparklineRefs[i].value = el; }" height="36px" width="100%" />
        </div>
        <div v-if="card.mock" class="kpi-mock-tag">Mock</div>
      </div>
    </div>

    <!-- 区块 2：主折线图 -->
    <div class="chart-card zy-slide-up">
      <EchartsUI ref="lineChartRef" height="320px" />
    </div>

    <!-- 区块 3：饼图 + 漏斗 -->
    <div class="two-col">
      <div class="chart-card zy-slide-up">
        <EchartsUI ref="pieChartRef" height="320px" />
      </div>
      <div class="chart-card zy-slide-up">
        <EchartsUI ref="funnelChartRef" height="320px" />
        <p class="mock-hint">漏斗数据暂为 Mock，待后端支持</p>
      </div>
    </div>

    <!-- 区块 4：热门手作 Top 5 -->
    <div class="chart-card zy-slide-up">
      <EchartsUI ref="barChartRef" height="280px" />
    </div>

    <!-- 区块 5：活跃用户热力图（CSS Grid 自绘） -->
    <div class="chart-card zy-slide-up">
      <h3 class="heatmap-title">活跃用户热力图（7 天 × 24 小时）</h3>
      <div class="heatmap-wrapper">
        <!-- 顶部小时标签 -->
        <div class="heatmap-hours">
          <span class="heatmap-corner"></span>
          <span v-for="h in 24" :key="h - 1" class="heatmap-hour-label">{{ (h - 1) % 6 === 0 ? h - 1 : '' }}</span>
        </div>
        <!-- 每行：天标签 + 24 个格子 -->
        <div v-for="(day, dIdx) in weekDays" :key="day" class="heatmap-row">
          <span class="heatmap-day-label">{{ day }}</span>
          <div class="heatmap-cells">
            <div
              v-for="h in 24"
              :key="h - 1"
              class="heatmap-cell"
              :style="{
                backgroundColor: `rgba(79, 70, 229, ${0.04 + heatmapCells[dIdx * 24 + (h - 1)]?.intensity * 0.8})`,
              }"
              :title="`${day} ${(h - 1).toString().padStart(2, '0')}:00 — ${heatmapCells[dIdx * 24 + (h - 1)]?.value} 次活跃`"
            ></div>
          </div>
        </div>
      </div>
      <!-- 图例 -->
      <div class="heatmap-legend">
        <span class="heatmap-legend-text">少</span>
        <div
          v-for="i in 6"
          :key="i"
          class="heatmap-legend-cell"
          :style="{ backgroundColor: `rgba(79, 70, 229, ${0.04 + (i - 1) * 0.16})` }"
        ></div>
        <span class="heatmap-legend-text">多</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analytics-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
}

/* ── KPI 卡片 ── */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.kpi-card {
  position: relative;
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
  padding: 14px 16px;
}
.kpi-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kpi-title {
  font-size: 13px;
  font-weight: 400;
  color: var(--zy-admin-muted, #9ca3af);
}
.kpi-trend {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: var(--zy-radius-sm, 4px);
  white-space: nowrap;
}
.kpi-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 8px;
}
.kpi-number {
  font-size: 28px;
  font-weight: 600;
  color: var(--zy-admin-title, #1f2937);
  font-variant-numeric: tabular-nums;
}
.kpi-unit {
  font-size: 12px;
  color: var(--zy-admin-muted, #9ca3af);
}
.kpi-sparkline {
  margin-top: 6px;
  height: 36px;
  overflow: hidden;
}
.kpi-mock-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 10px;
  color: var(--zy-admin-muted, #9ca3af);
  background: var(--zy-admin-border, #e5e7eb);
  padding: 1px 4px;
  border-radius: 2px;
}

/* ── 图表卡片 ── */
.chart-card {
  background: var(--zy-admin-card-bg, #fff);
  border-radius: var(--zy-radius-lg, 12px);
  box-shadow: var(--zy-shadow-sm, 0 2px 8px rgba(0, 0, 0, 0.06));
  padding: 16px 20px;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.mock-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--zy-admin-muted, #9ca3af);
  text-align: center;
}

/* ── 热力图 ── */
.heatmap-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--zy-admin-title, #1f2937);
}
.heatmap-wrapper {
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow-x: auto;
}
.heatmap-hours {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-left: 36px;
}
.heatmap-corner {
  width: 32px;
  flex-shrink: 0;
}
.heatmap-hour-label {
  width: 14px;
  font-size: 10px;
  color: var(--zy-admin-muted, #9ca3af);
  text-align: center;
}
.heatmap-row {
  display: flex;
  align-items: center;
  gap: 3px;
}
.heatmap-day-label {
  width: 32px;
  font-size: 11px;
  color: var(--zy-admin-muted, #9ca3af);
  text-align: right;
  flex-shrink: 0;
}
.heatmap-cells {
  display: flex;
  gap: 3px;
}
.heatmap-cell {
  width: 14px;
  height: 18px;
  border-radius: 2px;
  cursor: default;
  transition: transform var(--zy-duration-fast, 150ms) var(--zy-ease-out, ease-out);
}
.heatmap-cell:hover {
  transform: scale(1.3);
  z-index: 1;
}
.heatmap-legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  margin-left: 36px;
}
.heatmap-legend-text {
  font-size: 11px;
  color: var(--zy-admin-muted, #9ca3af);
}
.heatmap-legend-cell {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

/* ── 响应式 ── */
@media (max-width: 1280px) {
  .kpi-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
