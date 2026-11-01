<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import ChartCard from '#/components/chart-card/ChartCard.vue';
import { useChartTheme } from '#/composables/use-chart-theme';
import { withAlpha } from '#/utils/chart-theme';

const props = withDefaults(
  defineProps<{
    drilldown?: boolean;
    emptyHint?: string;
    emptyText?: string;
    kind: 'bar' | 'line' | 'pie';
    rows: Array<Record<string, any>>;
    subtitle: string;
    title: string;
  }>(),
  {
    drilldown: false,
    emptyHint: '数据将在产生后呈现',
    emptyText: '暂无数据',
  },
);

const emit = defineEmits<{ select: [row: Record<string, any>] }>();

const chart = ref<EchartsUIType>();
const { theme } = useChartTheme();
const { renderEcharts: renderChart } = useEcharts(chart);
const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;

/** 面积/条形渐变：canvas 只吃 rgba，色值由主题令牌换算而来 */
function verticalFade(color: string) {
  return {
    type: 'linear' as const,
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: withAlpha(color, 0.34) },
      { offset: 1, color: withAlpha(color, 0.02) },
    ],
  };
}

function horizontalFade(color: string) {
  return {
    type: 'linear' as const,
    x: 0,
    y: 0,
    x2: 1,
    y2: 0,
    colorStops: [
      { offset: 0, color: withAlpha(color, 0.5) },
      { offset: 1, color },
    ],
  };
}

async function renderEcharts(options: Parameters<typeof renderChart>[0]) {
  const instance = await renderChart(options);
  instance?.off('click');
  if (props.drilldown) {
    instance?.on('click', (event: any) => {
      const row = props.rows[event.dataIndex];
      if (row) {
        emit('select', row);
      }
    });
  }
}

function baseOption() {
  const c = theme.value;
  return {
    animation: !reduceMotion,
    animationDuration: 720,
    animationEasing: 'cubicOut' as const,
    aria: {
      enabled: true,
      label: { description: `${props.title}。${props.subtitle}` },
    },
    color: c.colors,
    legend: {
      bottom: 0,
      icon: 'circle',
      itemHeight: 8,
      itemWidth: 8,
      show: props.kind === 'line',
      textStyle: { color: c.text },
    },
    textStyle: { color: c.text },
    tooltip: {
      backgroundColor: c.tooltipBackground,
      borderColor: c.tooltipBorder,
      renderMode: 'richText' as const,
      textStyle: { color: c.text },
      trigger: props.kind === 'pie' ? ('item' as const) : ('axis' as const),
    },
  };
}

async function render() {
  if (!props.rows.length) {
    return;
  }
  await nextTick();
  const c = theme.value;
  const base = baseOption();

  if (props.kind === 'pie') {
    await renderEcharts({
      ...base,
      series: [
        {
          type: 'pie',
          avoidLabelOverlap: true,
          center: ['50%', '46%'],
          data: props.rows,
          emphasis: {
            scaleSize: 6,
            itemStyle: {
              shadowBlur: 20,
              shadowColor: withAlpha(c.colors[0], 0.35),
            },
          },
          itemStyle: {
            borderColor: c.background,
            borderRadius: 6,
            borderWidth: 3,
          },
          label: {
            color: c.text,
            formatter: '{b}\n{d}%',
            overflow: 'break',
            show: true,
            width: 96,
          },
          labelLine: { lineStyle: { color: c.border }, show: true },
          radius: ['46%', '66%'],
        },
      ],
    });
    return;
  }

  if (props.kind === 'bar') {
    await renderEcharts({
      ...base,
      grid: { bottom: 15, containLabel: true, left: 8, right: 36, top: 14 },
      series: [
        {
          type: 'bar',
          barMaxWidth: 20,
          backgroundStyle: {
            borderRadius: [0, 6, 6, 0],
            color: withAlpha(c.border, 0.22),
          },
          data: props.rows.map((row) => row.value),
          itemStyle: { borderRadius: [0, 6, 6, 0], color: horizontalFade(c.colors[0]) },
          label: { color: c.text, position: 'right', show: true },
          showBackground: true,
        },
      ],
      xAxis: {
        type: 'value',
        axisLabel: { color: c.text },
        minInterval: 1,
        splitLine: { lineStyle: { color: c.grid } },
      },
      yAxis: {
        type: 'category',
        inverse: true,
        axisLine: { show: false },
        axisLabel: { color: c.text, overflow: 'truncate', width: 100 },
        axisTick: { show: false },
        data: props.rows.map((row) => row.name),
      },
    });
    return;
  }

  await renderEcharts({
    ...base,
    grid: { bottom: 48, containLabel: true, left: 10, right: 18, top: 20 },
    series: [
      {
        name: '开始创作',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.sessions),
        areaStyle: { color: verticalFade(c.colors[0]) },
        emphasis: { focus: 'series' },
        itemStyle: { color: c.colors[0] },
        lineStyle: { type: 'solid', width: 2.4 },
      },
      {
        name: '完成创作',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.finished),
        itemStyle: { color: c.colors[2] },
        lineStyle: { type: 'dashed', width: 2 },
      },
      {
        name: '发布作品',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.works),
        itemStyle: { color: c.colors[1] },
        lineStyle: { type: 'dotted' },
      },
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.users),
        itemStyle: { color: c.colors[4] },
        lineStyle: { type: 'dashed', width: 2 },
      },
    ],
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.rows.map((row) => String(row.date).slice(5)),
      axisLabel: { color: c.text },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: c.text },
      splitLine: { lineStyle: { color: c.grid, type: 'dashed' } },
    },
  });
}

watch([() => props.rows, theme], render, { deep: true });
onMounted(render);
</script>

<template>
  <ChartCard
    :description="subtitle"
    :empty="!rows.length"
    :empty-hint="emptyHint"
    :empty-text="emptyText"
    :title="title"
  >
    <EchartsUI ref="chart" height="268px" />
    <template v-if="drilldown && rows.length" #footer>
      <div class="chart-links">
        <button
          v-for="row in rows"
          :key="row.name"
          type="button"
          @click="emit('select', row)"
        >
          {{ row.name }} · 查看列表 ↗
        </button>
      </div>
    </template>
  </ChartCard>
</template>

<style scoped>
.chart-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chart-links button {
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-color-primary-dark-2);
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.chart-links button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
