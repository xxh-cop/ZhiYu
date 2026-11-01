<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { TherapyFunnelData } from './therapy-funnel';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { requestClient } from '#/api/request';
import ChartCard from '#/components/chart-card/ChartCard.vue';
import { useChartTheme } from '#/composables/use-chart-theme';

import { formatDuration, formatRate, toFunnelChartData } from './therapy-funnel';

const props = defineProps<{ days: number }>();

const chart = ref<EchartsUIType>();
const loading = ref(true);
const error = ref('');
const data = ref<null | TherapyFunnelData>(null);
const { theme } = useChartTheme();
const { renderEcharts } = useEcharts(chart);
const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
let requestId = 0;

const empty = computed(
  () =>
    !loading.value &&
    !error.value &&
    data.value !== null &&
    data.value.cohort === 0,
);

async function render() {
  if (!data.value || data.value.cohort === 0) {
    return;
  }
  await nextTick();
  const c = theme.value;
  await renderEcharts({
    animation: !reduceMotion,
    animationDuration: 640,
    animationEasing: 'cubicOut' as const,
    aria: {
      enabled: true,
      label: {
        description: `疗愈闭环。近 ${data.value.days} 天，共 ${data.value.cohort} 名用户。运营漏斗，不代表疗效或因果。`,
      },
    },
    color: c.colors,
    series: [
      {
        type: 'funnel',
        bottom: 16,
        gap: 4,
        label: {
          color: c.text,
          fontSize: 12,
          formatter: '{b}  {c} 人',
          position: 'right',
          show: true,
        },
        labelLine: {
          lineStyle: { color: c.border },
          show: true,
        },
        left: '6%',
        maxSize: '100%',
        minSize: '24%',
        sort: 'none',
        top: 16,
        width: '58%',
        data: toFunnelChartData(data.value.stages, c.colors.slice(0, 4)),
      },
    ],
    textStyle: { color: c.text },
    tooltip: {
      backgroundColor: c.tooltipBackground,
      borderColor: c.tooltipBorder,
      formatter: '{b}: {c} 人',
      renderMode: 'richText',
      textStyle: { color: c.text },
      trigger: 'item',
    },
  });
}

async function load() {
  const turn = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const result = await requestClient.get<TherapyFunnelData>(
      '/admin/dashboard/therapy-funnel',
      { params: { days: props.days } },
    );
    if (turn === requestId) {
      data.value = result;
    }
  } catch {
    if (turn === requestId) {
      error.value = '疗愈漏斗暂未读取成功，请重试。';
    }
  } finally {
    if (turn === requestId) {
      loading.value = false;
    }
  }
  if (turn === requestId && !error.value) {
    await render();
  }
}

watch(
  () => props.days,
  () => {
    load();
  },
);
watch(theme, () => {
  nextTick(render);
});
onMounted(load);
</script>

<template>
  <ChartCard
    class="therapy-funnel"
    description="同一批情绪会话用户 · 运营口径，不代表疗效或因果"
    :empty="empty"
    empty-hint="有学生开始情绪倾听后，这里会呈现转化路径"
    empty-text="近一段时间的情绪会话为 0"
    :error="error"
    :loading="loading && !data"
    :title="`疗愈闭环 · 近 ${days} 天`"
    @retry="load"
  >
    <EchartsUI ref="chart" height="320px" />
    <template v-if="data && data.cohort > 0 && !error" #footer>
      <p v-if="data.insufficient" class="therapy-funnel__note">
        当前样本用户少于 5 人，暂不计算转化率；以下只展示人数。
      </p>
      <div class="therapy-funnel__stages">
        <div
          v-for="stage in data.stages"
          :key="stage.key"
          class="therapy-funnel__row"
        >
          <span>{{ stage.label }}</span>
          <strong>{{ stage.users }}</strong>
          <span class="therapy-funnel__rate">
            较上一阶段 {{ formatRate(stage.fromPrevious) }}
          </span>
        </div>
      </div>
      <p class="therapy-funnel__foot">
        完成创作 {{ data.flow.completedSessions }} 次 · 心流
        {{ formatDuration(data.flow.flowSeconds) }} · 心流占比
        {{ formatRate(data.flow.focusRate) }}
      </p>
    </template>
  </ChartCard>
</template>

<style scoped>
.therapy-funnel {
  margin-bottom: 20px;
}

.therapy-funnel__note {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
}

.therapy-funnel__stages {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 20px;
  margin-top: 12px;
}

.therapy-funnel__row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 10px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.therapy-funnel__row strong {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.therapy-funnel__rate {
  grid-column: 1 / -1;
}

.therapy-funnel__foot {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

@media (max-width: 900px) {
  .therapy-funnel__stages {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .therapy-funnel__stages {
    grid-template-columns: 1fr;
  }
}
</style>
