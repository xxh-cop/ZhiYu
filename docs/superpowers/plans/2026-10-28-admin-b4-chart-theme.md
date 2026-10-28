# 管理端 B4 图表主题桥 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理端所有 ECharts 图表从 Vben preferences 派生颜色，随主题色与深浅模式实时更新，并补齐图表四态、图例可辨识性与无障碍播报。

**Architecture:** 新增纯函数 `buildChartTheme` 读取 Element Plus 的明暗自适应语义令牌；`useChartTheme` 在 `preferences.theme` 变化后重算。新增 `ChartCard` 承载标题、说明、加载/空/错误态与底部操作区。`OperationsChart` 与 `emotion-analysis` 删除本地色板，统一消费主题桥；饼图外部标签直连，折线用线型区分系列，空数据不渲染画布。

**Tech Stack:** Vue 3.5、ECharts 6.1、Vben preferences、Element Plus、Vitest、Playwright（本地 Edge）。

**Spec:** `docs/superpowers/specs/2026-10-26-admin-ui-baseline-design.md`（D8/D9/D10/D11、P-7）

## Global Constraints

- 只改 `zhiyu-admin-vben/apps/web-ele` 与 `zhiyu-admin-vben/packages/effects/plugins/src/echarts`；不改后端、OpenAPI、路由与权限。
- 图表颜色只来自主题令牌；`OperationsChart.vue` 与 `emotion-analysis.vue` 的本地色板、六位 hex 必须归零。
- D8 列出的是 `--primary-500` 等 HSL 分量变量，不能直接作为 ECharts 颜色字符串，且 500 档在深色画布上图形对比不足。实现改用同源的 Element Plus `-dark-2` 令牌：浅色下更深、深色下更亮，满足 D10 的图形 ≥3:1。该偏差记录在 B4 ledger。
- 空数据不渲染画布，使用 `ChartCard` 的空态；错误替换画布并保留已加载数据；加载态不跳动。
- 饼图使用外部标签（`label` + `labelLine`），折线使用 `lineStyle.type`；不得只靠颜色区分系列。
- 情绪分析页必须显示「本系统不能替代专业心理咨询」，字号 ≥12px、对比度 ≥4.5:1；不得出现诊断性措辞。
- 所有可交互元素保留焦点环；图表容器提供中文 `aria.label.description`，并注册 ECharts `AriaComponent`。
- 每任务结束执行 typecheck 与 build；提交前由 controller 展示 `git status --short` 与 `git diff --stat`，得到用户确认后提交；禁止 push。
- 临时脚本放 `.ai-tmp/`，截图放 `output/playwright/`；不提交 `.ai-tmp/`、`dist/`、截图与用户既有 docx。

## 执行修正（2026-10-28）

1. 用户选择「本会话直接实现」，不派子代理；5 个实现任务先完成，提交延后到汇总 diff 确认。
2. `ChartCard` 增加 `role="img"` + `aria-label="标题。说明"`，作为 ECharts `AriaComponent` 的稳定容器级名称。
3. 额外修正（均由浏览器实测发现）：ChartCard 空态描述改用 regular；情绪分析表格头改用 regular；图表下钻链接改用 `--el-color-primary-dark-2`（深色下 4.85:1）。
4. 实际 hex：B4 前 55 处 / 8 文件，完成后 16 处 / 6 文件；两个图表文件为 0，已低于 spec ≤20。剩余在 `preferences.ts`、`base-setting.vue`、`users.vue`、`inheritors.vue`、`VideoUpload/Preview.vue`，归 B5/既有模板。
5. 本地 Vite 需显式 `--port 5777`（当前启动未读取 `VITE_PORT`），验收脚本固定 5777。
6. 浏览器数据全部为路由拦截 fixture，未写入真实业务数据。

---

### Task 1: 图表主题令牌解析

**Files:**
- Create: `zhiyu-admin-vben/apps/web-ele/src/utils/chart-theme.ts`
- Test: `zhiyu-admin-vben/apps/web-ele/src/utils/chart-theme.test.ts`

**Interfaces:**
- Produces: `ChartTheme`、`buildChartTheme(read: (name: string) => string): ChartTheme`
- 后续任务消费 `theme.colors / text / grid / border / background / tooltipBackground / tooltipBorder`

- [ ] **Step 1: 写失败测试**

```ts
import { describe, expect, it } from 'vitest';

import { buildChartTheme } from './chart-theme';

const tokens: Record<string, string> = {
  '--el-bg-color': 'rgb(255, 255, 255)',
  '--el-bg-color-overlay': 'rgb(255, 255, 255)',
  '--el-border-color': 'rgb(220, 223, 230)',
  '--el-border-color-lighter': 'rgb(235, 238, 245)',
  '--el-color-danger': 'rgb(220, 38, 38)',
  '--el-color-danger-dark-2': 'rgb(196, 33, 33)',
  '--el-color-primary': 'rgb(79, 70, 229)',
  '--el-color-primary-dark-2': 'rgb(63, 56, 183)',
  '--el-color-success': 'rgb(4, 120, 87)',
  '--el-color-success-dark-2': 'rgb(4, 108, 79)',
  '--el-color-warning': 'rgb(180, 83, 9)',
  '--el-color-warning-dark-2': 'rgb(161, 74, 8)',
  '--el-text-color-regular': 'rgb(50, 54, 57)',
};

const read = (name: string) => tokens[name] ?? '';

describe('buildChartTheme', () => {
  it('优先读取明暗自适应的语义色令牌', () => {
    const theme = buildChartTheme(read);
    expect(theme.colors).toEqual([
      'rgb(63, 56, 183)',
      'rgb(161, 74, 8)',
      'rgb(4, 108, 79)',
      'rgb(196, 33, 33)',
      'rgb(50, 54, 57)',
    ]);
    expect(theme.text).toBe('rgb(50, 54, 57)');
    expect(theme.grid).toBe('rgb(235, 238, 245)');
    expect(theme.border).toBe('rgb(220, 223, 230)');
    expect(theme.background).toBe('rgb(255, 255, 255)');
    expect(theme.tooltipBackground).toBe('rgb(255, 255, 255)');
    expect(theme.tooltipBorder).toBe('rgb(220, 223, 230)');
  });

  it('缺少 -dark-2 时回退到基础语义色', () => {
    const sparse = { ...tokens };
    delete sparse['--el-color-primary-dark-2'];
    const theme = buildChartTheme((name) => sparse[name] ?? '');
    expect(theme.colors[0]).toBe('rgb(79, 70, 229)');
  });

  it('缺少分线令牌时回退到边框色', () => {
    const sparse = { ...tokens };
    delete sparse['--el-border-color-lighter'];
    expect(buildChartTheme((name) => sparse[name] ?? '').grid).toBe(
      'rgb(220, 223, 230)',
    );
  });

  it('令牌全部缺失时返回空字符串而不抛错', () => {
    const theme = buildChartTheme(() => '');
    expect(theme.colors).toEqual(['', '', '', '', '']);
    expect(theme.background).toBe('');
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd zhiyu-admin-vben && pnpm vitest run --dom apps/web-ele/src/utils/chart-theme.test.ts`
Expected: FAIL，提示无法解析 `./chart-theme`。

- [ ] **Step 3: 实现纯函数**

```ts
export interface ChartTheme {
  background: string;
  border: string;
  colors: string[];
  grid: string;
  text: string;
  tooltipBackground: string;
  tooltipBorder: string;
}

function pick(read: (name: string) => string, ...names: string[]): string {
  for (const name of names) {
    const value = read(name);
    if (value) {
      return value;
    }
  }
  return '';
}

/**
 * Element Plus 的 -dark-2 令牌在浅色下加深、深色下提亮，
 * 比固定的 500 档更适合图表线条与图形。
 */
export function buildChartTheme(
  read: (name: string) => string,
): ChartTheme {
  return {
    background: pick(read, '--el-bg-color'),
    border: pick(read, '--el-border-color'),
    colors: [
      pick(read, '--el-color-primary-dark-2', '--el-color-primary'),
      pick(read, '--el-color-warning-dark-2', '--el-color-warning'),
      pick(read, '--el-color-success-dark-2', '--el-color-success'),
      pick(read, '--el-color-danger-dark-2', '--el-color-danger'),
      pick(read, '--el-text-color-regular'),
    ],
    grid: pick(read, '--el-border-color-lighter', '--el-border-color'),
    text: pick(read, '--el-text-color-regular'),
    tooltipBackground: pick(read, '--el-bg-color-overlay', '--el-bg-color'),
    tooltipBorder: pick(read, '--el-border-color'),
  };
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd zhiyu-admin-vben && pnpm vitest run --dom apps/web-ele/src/utils/chart-theme.test.ts`
Expected: 4 passed。

- [ ] **Step 5: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/utils/chart-theme.ts zhiyu-admin-vben/apps/web-ele/src/utils/chart-theme.test.ts
git commit -F .ai-tmp/b4-task1-commit.txt
```

`.ai-tmp/b4-task1-commit.txt` 内容：

```text
feat(admin): 新增图表主题令牌解析

- 从 Element Plus 语义令牌生成图表色板与文本/网格/提示框颜色
- 优先使用明暗自适应的 -dark-2 令牌，缺失时回退基础色
- 覆盖令牌缺失时的空值行为
```

---

### Task 2: useChartTheme 与图表无障碍注册

**Files:**
- Create: `zhiyu-admin-vben/apps/web-ele/src/composables/use-chart-theme.ts`
- Modify: `zhiyu-admin-vben/packages/effects/plugins/src/echarts/echarts.ts:1-37`

**Interfaces:**
- Consumes: `buildChartTheme` / `ChartTheme`（Task 1）
- Produces: `useChartTheme(): { theme: Ref<ChartTheme> }`

- [ ] **Step 1: 增加响应式主题桥**

```ts
import { ref, watch } from 'vue';

import { preferences } from '@vben/preferences';

import { buildChartTheme } from '#/utils/chart-theme';

export function useChartTheme() {
  const root = document.documentElement;
  const read = (name: string) =>
    getComputedStyle(root).getPropertyValue(name).trim();
  const theme = ref(buildChartTheme(read));

  watch(
    () => preferences.theme,
    () => {
      theme.value = buildChartTheme(read);
    },
    { deep: true, flush: 'post' },
  );

  return { theme };
}
```

`flush: 'post'` 必须保留：Element Plus 设计令牌的 watcher 先更新 DOM 变量，图表桥在其后读取。

- [ ] **Step 2: 注册 AriaComponent**

修改 `packages/effects/plugins/src/echarts/echarts.ts`：在 `echarts/components` 导入块加入 `AriaComponent,`（首项），并在 `echarts.use([...])` 列表加入 `AriaComponent,`（放在 `TitleComponent` 之前）。其他导入与注册顺序不变。

- [ ] **Step 3: 类型检查与构建**

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele typecheck`
Expected: exit 0。

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele build`
Expected: built successfully。

- [ ] **Step 4: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/composables/use-chart-theme.ts zhiyu-admin-vben/packages/effects/plugins/src/echarts/echarts.ts
git commit -F .ai-tmp/b4-task2-commit.txt
```

`.ai-tmp/b4-task2-commit.txt` 内容：

```text
feat(admin): 接入图表主题桥与无障碍

- useChartTheme 在 preferences.theme 变化后重读语义令牌
- 注册 ECharts AriaComponent，使 aria.label.description 生效
```

---

### Task 3: ChartCard 四态容器

**Files:**
- Create: `zhiyu-admin-vben/apps/web-ele/src/components/chart-card/ChartCard.vue`

**Interfaces:**
- Props: `title: string`、`description?: string`、`loading?: boolean`、`error?: string`、`empty?: boolean`、`emptyText?: string`、`emptyHint?: string`、`minHeight?: string`
- Emits: `retry`
- Slots: default（ready 时渲染）、`actions`、`footer`
- Consumes: `resolveDataState`、`DataState`（B2）

- [ ] **Step 1: 创建组件**

```vue
<script lang="ts" setup>
import { computed } from 'vue';

import DataState from '#/components/data-state/DataState.vue';
import { resolveDataState } from '#/components/data-state/resolve';

const props = withDefaults(
  defineProps<{
    description?: string;
    empty?: boolean;
    emptyHint?: string;
    emptyText?: string;
    error?: string;
    loading?: boolean;
    minHeight?: string;
    title: string;
  }>(),
  {
    description: '',
    empty: false,
    emptyHint: '数据将在产生后呈现',
    emptyText: '暂无数据',
    error: '',
    loading: false,
    minHeight: '268px',
  },
);

const emit = defineEmits<{ retry: [] }>();

const kind = computed(() =>
  resolveDataState({
    error: props.error,
    isEmpty: props.empty,
    loading: props.loading,
  }),
);
</script>

<template>
  <section class="chart-card">
    <header class="chart-card__header">
      <div>
        <h3 class="chart-card__title">{{ title }}</h3>
        <p v-if="description" class="chart-card__description">
          {{ description }}
        </p>
      </div>
      <div v-if="$slots.actions" class="chart-card__actions">
        <slot name="actions"></slot>
      </div>
    </header>
    <div
      class="chart-card__body"
      :aria-busy="kind === 'loading'"
      :style="{ minHeight }"
    >
      <DataState
        v-if="kind !== 'ready'"
        :empty-hint="emptyHint"
        :empty-text="emptyText"
        :error="error"
        :loading="loading"
        @retry="emit('retry')"
      />
      <slot v-else></slot>
    </div>
    <footer v-if="$slots.footer" class="chart-card__footer">
      <slot name="footer"></slot>
    </footer>
  </section>
</template>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.chart-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.chart-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chart-card__description {
  margin: 7px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.chart-card__body {
  margin-top: 16px;
}

.chart-card__body :deep(.data-state) {
  display: flex;
  min-height: inherit;
  flex-direction: column;
  justify-content: center;
}

.chart-card__footer {
  margin-top: 12px;
}

@media (max-width: 640px) {
  .chart-card {
    padding: 18px;
  }
}
</style>
```

- [ ] **Step 2: 类型检查与构建**

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele typecheck`
Expected: exit 0。

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele build`
Expected: built successfully。

- [ ] **Step 3: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/components/chart-card/ChartCard.vue
git commit -F .ai-tmp/b4-task3-commit.txt
```

`.ai-tmp/b4-task3-commit.txt` 内容：

```text
feat(admin): 新增图表卡片四态容器

- 统一图表标题、说明、操作区与底部区域
- loading/empty/error 复用 DataState，空态不渲染画布
```

---

### Task 4: OperationsChart 主题化与 Dashboard 空态

**Files:**
- Modify: `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsChart.vue`
- Modify: `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue:25-28`

**Interfaces:**
- Consumes: `ChartCard`（Task 3）、`useChartTheme`（Task 2）
- `OperationsChart` props 增加 `emptyText?: string`、`emptyHint?: string`；既有 `title/subtitle/kind/rows/drilldown` 不变

- [ ] **Step 1: 重写 OperationsChart**

```vue
<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import ChartCard from '#/components/chart-card/ChartCard.vue';
import { useChartTheme } from '#/composables/use-chart-theme';

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
    animation: false,
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
          itemStyle: {
            borderColor: c.background,
            borderRadius: 5,
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
          data: props.rows.map((row) => row.value),
          itemStyle: { borderRadius: [0, 5, 5, 0], color: c.colors[0] },
          label: { color: c.text, position: 'right', show: true },
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
        smooth: false,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.sessions),
        areaStyle: { opacity: 0.08 },
        itemStyle: { color: c.colors[0] },
        lineStyle: { type: 'solid' },
      },
      {
        name: '完成创作',
        type: 'line',
        smooth: false,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.finished),
        itemStyle: { color: c.colors[2] },
        lineStyle: { type: 'dashed' },
      },
      {
        name: '发布作品',
        type: 'line',
        smooth: false,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.works),
        itemStyle: { color: c.colors[1] },
        lineStyle: { type: 'dotted' },
      },
      {
        name: '新增用户',
        type: 'line',
        smooth: false,
        symbol: 'circle',
        symbolSize: 5,
        data: props.rows.map((row) => row.users),
        itemStyle: { color: c.colors[4] },
        lineStyle: { type: 'dashed' },
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
```

- [ ] **Step 2: 更新 Dashboard 与数据看板的图表区**

将 `OperationsBoard.vue` 的 `ElSkeleton` 与图表区（当前第 25–28 行）替换为：

```vue
<ElSkeleton v-if="loading && !data" :rows="5" animated />
<template v-else-if="data">
  <div class="charts-top">
    <OperationsChart
      title="创作与参与趋势"
      :subtitle="`近 ${days} 天 · 可点击图例筛选；各曲线按各自发生日期统计`"
      kind="line"
      :rows="data?.daily || []"
      empty-text="暂无创作趋势"
      :empty-hint="`近 ${days} 天还没有创作记录`"
    />
    <OperationsChart
      title="创作进行到哪一步"
      subtitle="全部创作会话状态分布"
      kind="pie"
      :rows="data?.sessionStates || []"
      empty-text="暂无创作会话"
      empty-hint="完成一次创作后会在这里显示状态分布"
    />
  </div>
  <div class="charts-bottom" :class="{ overview: !analytics }">
    <OperationsChart
      drilldown
      title="手作内容分布"
      subtitle="已上架项目 · 数量前 12 个分类"
      kind="bar"
      :rows="data?.categories || []"
      empty-text="暂无已上架项目"
      empty-hint="上架手作内容后会在这里显示分类分布"
      @select="
        row =>
          router.push({
            path: '/zhiyu/business/audit',
            query: { status: '1', category: row.name },
          })
      "
    />
    <OperationsChart
      drilldown
      title="内容上架进度"
      subtitle="项目审核与发布状态"
      kind="pie"
      :rows="data?.projectStates || []"
      empty-text="暂无审核记录"
      empty-hint="提交项目审核后会在这里显示进度"
      @select="
        row =>
          router.push({
            path: '/zhiyu/business/audit',
            query: {
              status:
                ({
                  '待审核': '0',
                  '待审': '0',
                  '已上架': '1',
                  '上架': '1',
                  '已下架': '2',
                  '下架': '2',
                } as Record<string, string>)[row.name] || '0',
            },
          })
      "
    />
    <OperationsChart
      v-if="analytics"
      title="预约与签到"
      subtitle="全部报名记录 · 含已取消"
      kind="bar"
      :rows="data?.bookingStates || []"
      empty-text="暂无报名记录"
      empty-hint="产生报名后会在这里显示签到状态"
    />
  </div>
  <div v-if="analytics" class="charts-top">
    <OperationsChart
      drilldown
      title="活动内容供给"
      subtitle="当前已发布活动 · 按类型"
      kind="bar"
      :rows="data?.programKinds || []"
      empty-text="暂无已发布活动"
      empty-hint="发布活动后会在这里显示类型分布"
      @select="
        row =>
          router.push({
            path: '/zhiyu/programs',
            query: {
              kind:
                ({
                  '工作坊': 'WORKSHOP',
                  '主题工作坊': 'WORKSHOP',
                  '课程': 'COURSE',
                  '系列课程': 'COURSE',
                  '一对一': 'GUIDANCE',
                  '一对一指导': 'GUIDANCE',
                  '个别指导': 'GUIDANCE',
                } as Record<string, string>)[row.name] || row.name,
            },
          })
      "
    />
    <section class="method-panel">
      <p class="overline">ABOUT THE DATA</p>
      <h3>真实记录，清晰边界</h3>
      <p>
        曲线中的“开始”和“完成”分别按开始日、完成日计数，不能直接相除得到同一批用户的完成率。
      </p>
      <p>
        零值意味着该日期没有记录；空图意味着尚无相关数据。逻辑删除的记录不计入统计。
      </p>
      <p>参与次数与作品数量不代表心理改善或临床疗效。</p>
    </section>
  </div>
</template>
```

保留原有的 `DataErrorBanner`、KPI、待办、welcome strip、method panel、`.updated` 段落与全部样式；只替换图表区。首次加载失败（`error && !data`）时不渲染任何图表卡片，避免把错误伪装成空态。

- [ ] **Step 3: 类型检查、构建与色值门禁**

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele typecheck`
Expected: exit 0。

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele build`
Expected: built successfully。

Run: `cd zhiyu-admin-vben/apps/web-ele && rg --pcre2 '#[0-9a-fA-F]{3,8}\b' src/views/zhiyu/components/OperationsChart.vue`
Expected: no matches。

- [ ] **Step 4: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsChart.vue zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/components/OperationsBoard.vue
git commit -F .ai-tmp/b4-task4-commit.txt
```

`.ai-tmp/b4-task4-commit.txt` 内容：

```text
feat(admin): 图表颜色接入主题并补齐空态

- OperationsChart 删除本地薄荷色板，改用 useChartTheme
- 饼图外部标签、折线线型区分系列，空数据不渲染画布
- Dashboard 首次加载失败不再渲染空图表，避免误导
```

---

### Task 5: 情绪分析页主题化与免责声明

**Files:**
- Modify: `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/emotion-analysis.vue`

**Interfaces:**
- Consumes: `ChartCard`、`DataState`、`DataErrorBanner`、`useChartTheme`
- 保持 `GET /admin/dashboard/stats` 接口与 `moodDistribution` 字段不变

- [ ] **Step 1: 重写页面**

```vue
<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { getDashboardStats } from '#/api/zhiyu/admin';
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
    percent:
      total > 0 ? ((item.count / total) * 100).toFixed(1) : '0.0',
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
  <div class="emotion-page">
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
  max-width: 1600px;
  padding: 16px;
  margin: 0 auto;
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
```

- [ ] **Step 2: 类型检查、构建与色值门禁**

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele typecheck`
Expected: exit 0。

Run: `cd zhiyu-admin-vben && pnpm --filter @vben/web-ele build`
Expected: built successfully。

Run: `cd zhiyu-admin-vben/apps/web-ele && rg --pcre2 '#[0-9a-fA-F]{3,8}\b' src/views/zhiyu/emotion-analysis.vue`
Expected: no matches。

- [ ] **Step 3: 提交**

```bash
git add zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/emotion-analysis.vue
git commit -F .ai-tmp/b4-task5-commit.txt
```

`.ai-tmp/b4-task5-commit.txt` 内容：

```text
feat(admin): 情绪分析图表接入主题并补齐状态

- 删除本地靛蓝色板，改用 useChartTheme 与外部标签
- 增加加载/空/错误态与重试，不再静默吞掉失败
- 补充「本系统不能替代专业心理咨询」声明
```

---

### Task 6: B4 验收

**Files:**
- Create: `.ai-tmp/b4-chart-smoke.cjs`
- Output: `output/playwright/b4-dashboard-light.png`、`b4-dashboard-dark.png`、`b4-dashboard-empty.png`、`b4-dashboard-error.png`、`b4-analytics-light.png`、`b4-emotion-light.png`、`b4-emotion-dark.png`
- Ledger: `.superpowers/sdd/2026-10-28-admin-b4-chart-theme/progress.md`

- [ ] **Step 1: 自动化门禁**

Run:

```bash
cd zhiyu-admin-vben
pnpm --filter @vben/web-ele typecheck
pnpm --filter @vben/web-ele build
pnpm test:unit
```

Expected: typecheck/build exit 0；全部单测通过（B3 为 82 files / 594 tests，Task 1 新增 4 个后应为 598 tests）。

Run:

```bash
cd zhiyu-admin-vben/apps/web-ele
rg --pcre2 '#[0-9a-fA-F]{3,8}\b' src -c
rg --pcre2 '#[0-9a-fA-F]{3,8}\b' src/views/zhiyu/emotion-analysis.vue src/views/zhiyu/components/OperationsChart.vue
rg 'useChartTheme' src/views/zhiyu
```

Expected: 前两条命令对两个图表文件无输出；第三条能看到 `OperationsChart.vue` 与 `emotion-analysis.vue`。全局 hex 总数应比 B3 基线 55 减少 14（近似 41）；剩余项属 B5 范围。

- [ ] **Step 2: 启动本地服务并运行浏览器脚本**

后端（若未运行；不要结束用户已有进程）：

```bash
cd zhiyu-backend
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
```

前端：

```bash
cd zhiyu-admin-vben/apps/web-ele
pnpm vite --mode development
```

运行 `.ai-tmp/b4-chart-smoke.cjs`（从仓库根目录 `node .ai-tmp/b4-chart-smoke.cjs`）。脚本要求：

```js
const { chromium } = require('../zhiyu-admin-vben/node_modules/playwright');
const assert = require('node:assert/strict');
const path = require('node:path');

const base = 'http://localhost:5777';
const out = path.resolve(__dirname, '../output/playwright');
const reports = [];
const errors = [];
const wrap = data => ({ code: 0, message: '操作成功', data });
function record(name, data) {
  reports.push({ name, ...data });
  console.log(name, JSON.stringify(data));
}
function setTheme(page, mode, colorPrimary = '#4F46E5') {
  return page.evaluate(async ({ mode, colorPrimary }) => {
    const module = await import('/@fs/D:/develop/Zhi_Yu/zhiyu-admin-vben/packages/preferences/src/index.ts');
    module.updatePreferences({ theme: { builtinType: 'custom', colorPrimary, mode } });
  }, { mode, colorPrimary }).then(() => page.waitForTimeout(650));
}
async function closeNotification(page) {
  await page.locator('.el-notification__closeBtn').click({ timeout: 2000 }).catch(() => {});
  await page.locator('.el-notification').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
}
async function shot(page, name) {
  await page.locator('#__app-loading__').waitFor({ state: 'detached' }).catch(() => {});
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(out, name + '.png'), fullPage: true, animations: 'disabled' });
}
async function barColors(page) {
  return page.evaluate(() => {
    const card = [...document.querySelectorAll('.chart-card')].find(item => item.querySelector('h3')?.textContent.trim() === '手作内容分布');
    const canvas = card?.querySelector('canvas');
    if (!canvas) return { missing: true };
    const ctx = canvas.getContext('2d');
    const { data } = ctx.getImageData(0, 0, canvas.width, canvas.height);
    let colored = 0, indigo = 0, teal = 0;
    for (let i = 0; i < data.length; i += 16) {
      const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
      if (a < 128) continue;
      if (Math.max(r, g, b) - Math.min(r, g, b) < 35) continue;
      colored += 1;
      if (b > g + 20 && b > r + 20) indigo += 1;
      if (g >= b && g > r + 20) teal += 1;
    }
    return { colored, indigo, teal };
  });
}
function contrast(selector) {
  const el = document.querySelector(selector);
  if (!el) return { missing: true };
  const ctx = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
  const parse = color => {
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    return Array.from(ctx.getImageData(0, 0, 1, 1).data);
  };
  const chain = [];
  for (let node = el; node; node = node.parentElement) chain.unshift(node);
  let background = [255, 255, 255];
  for (const node of chain) {
    const color = parse(getComputedStyle(node).backgroundColor);
    const alpha = color[3] / 255;
    background = background.map((value, index) => value * (1 - alpha) + color[index] * alpha);
  }
  const style = getComputedStyle(el);
  const color = parse(style.color);
  const alpha = color[3] / 255;
  const foreground = background.map((value, index) => value * (1 - alpha) + color[index] * alpha);
  const lum = rgb => rgb.slice(0, 3).map(v => v / 255).map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
  const x = lum(foreground), y = lum(background);
  return { selector, size: parseFloat(style.fontSize), ratio: +((Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05)).toFixed(2) };
}

const overview = {
  totals: { care: 4, pending: 2, projects: 16, sessions: 17, users: 13, works: 7 },
  daily: Array.from({ length: 7 }, (_, i) => ({
    date: `2026-10-${22 + i}`,
    finished: 2 + (i % 2),
    sessions: 3 + (i % 3),
    users: 1 + (i % 4),
    works: 2 + (i % 2),
  })),
  categories: [{ name: '剪纸', value: 6 }, { name: '扎染', value: 4 }, { name: '竹编', value: 3 }],
  projectStates: [{ name: '已上架', value: 5 }, { name: '待审核', value: 2 }],
  sessionStates: [{ name: '进行中', value: 4 }, { name: '已完成', value: 8 }],
  programKinds: [{ name: '课程', value: 3 }, { name: '工作坊', value: 2 }],
  bookingStates: [{ name: '已签到', value: 5 }, { name: '已取消', value: 1 }],
  generatedAt: '2026-10-28T09:00:00+08:00',
};
const stats = {
  moodDistribution: [
    { mood: '平静', count: 8 },
    { mood: '愉悦', count: 5 },
    { mood: '低落', count: 3 },
  ],
};

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  page.on('pageerror', error => errors.push(error.message));
  let operationsMode = 'ok';
  let statsMode = 'ok';

  await page.route('**/api/admin/dashboard/operations**', route => {
    if (operationsMode === 'fail') return route.abort('failed');
    const data = operationsMode === 'empty'
      ? { ...overview, bookingStates: [], categories: [], daily: [], programKinds: [], projectStates: [], sessionStates: [] }
      : overview;
    return route.fulfill({ json: wrap(data) });
  });
  await page.route('**/api/admin/dashboard/stats**', route => {
    if (statsMode === 'fail') return route.abort('failed');
    return route.fulfill({ json: wrap(statsMode === 'empty' ? { moodDistribution: [] } : stats) });
  });

  try {
    await page.goto(base + '/auth/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('工作账号', { exact: true }).fill('admin');
    await page.getByLabel('登录密码', { exact: true }).fill('admin123');
    await page.getByRole('button', { name: '登录工作空间', exact: false }).click();
    await page.getByRole('heading', { name: '你好，平台管理员' }).waitFor();
    await closeNotification(page);
    await setTheme(page, 'light');
    await page.locator('.chart-card canvas').first().waitFor();

    const light = await barColors(page);
    assert.ok(light.colored > 0);
    assert.ok(light.indigo > light.teal, JSON.stringify(light));
    record('dashboard-chart-light', light);
    const cards = await page.locator('.chart-card').count();
    assert.ok(cards >= 4);
    const cardText = await page.evaluate(() => ['.chart-card__title', '.chart-card__description'].map(contrast));
    for (const item of cardText) {
      assert.ok(!item.missing && item.ratio >= 4.5 && item.size >= 12, JSON.stringify(item));
    }
    record('chart-card-contrast-light', cardText);
    await shot(page, 'b4-dashboard-light');

    await setTheme(page, 'light', '#0C8683');
    const teal = await barColors(page);
    assert.ok(teal.teal > teal.indigo, JSON.stringify(teal));
    record('dashboard-chart-teal', teal);
    await setTheme(page, 'dark');
    assert.ok(await page.locator('.chart-card canvas').count() >= 4);
    const darkCard = await page.evaluate(() => ['.chart-card__title', '.chart-card__description'].map(contrast));
    for (const item of darkCard) {
      assert.ok(!item.missing && item.ratio >= 4.5 && item.size >= 12, JSON.stringify(item));
    }
    record('chart-card-contrast-dark', darkCard);
    await shot(page, 'b4-dashboard-dark');
    await setTheme(page, 'light');
    await page.goto(base + '/zhiyu/data-center/analytics', { waitUntil: 'domcontentloaded' });
    await page.getByRole('heading', { name: '每一份参与，都清晰可见。' }).waitFor();
    await page.locator('.chart-card canvas').first().waitFor();
    record('analytics-charts', { cards: await page.locator('.chart-card').count(), canvases: await page.locator('.chart-card canvas').count() });
    await shot(page, 'b4-analytics-light');

    await page.goto(base + '/zhiyu/dashboard', { waitUntil: 'domcontentloaded' });
    await page.getByRole('heading', { name: '你好，平台管理员' }).waitFor();
    operationsMode = 'empty';
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.getByRole('heading', { name: '你好，平台管理员' }).waitFor();
    await page.locator('.chart-card .el-empty').first().waitFor();
    assert.equal(await page.locator('.chart-card canvas').count(), 0);
    record('dashboard-empty', { emptyCards: await page.locator('.chart-card .el-empty').count(), canvases: 0 });
    await shot(page, 'b4-dashboard-empty');

    operationsMode = 'fail';
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.data-error-banner').waitFor();
    assert.equal(await page.locator('.chart-card').count(), 0);
    record('dashboard-first-load-error', { banner: true, charts: 0 });
    await shot(page, 'b4-dashboard-error');

    operationsMode = 'ok';
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.chart-card canvas').first().waitFor();
    operationsMode = 'fail';
    await page.getByRole('button', { name: '刷新数据' }).click();
    await page.locator('.data-error-banner').waitFor();
    assert.ok(await page.locator('.chart-card canvas').count() >= 4);
    record('dashboard-refresh-error-retains-charts', { canvases: await page.locator('.chart-card canvas').count() });
    operationsMode = 'ok';
    await page.locator('.data-error-banner').getByRole('button', { name: '重试' }).click();
    await page.locator('.data-error-banner').waitFor({ state: 'hidden' });
    assert.ok(await page.locator('.chart-card canvas').count() >= 4);

    await page.goto(base + '/zhiyu/data-center/emotion', { waitUntil: 'domcontentloaded' });
    await page.getByText('本系统不能替代专业心理咨询', { exact: true }).waitFor();
    await page.locator('.chart-card canvas').waitFor();
    record('emotion-ready', { disclaimer: true, title: await page.getByText('情绪分布', { exact: true }).count() });
    const disclaimer = await page.evaluate(contrast, '.emotion-note');
    assert.ok(disclaimer.ratio >= 4.5 && disclaimer.size >= 12, JSON.stringify(disclaimer));
    await shot(page, 'b4-emotion-light');
    await setTheme(page, 'dark');
    await page.locator('.chart-card canvas').waitFor();
    await shot(page, 'b4-emotion-dark');
    await setTheme(page, 'light');

    statsMode = 'empty';
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.chart-card .el-empty').waitFor();
    assert.equal(await page.locator('.chart-card canvas').count(), 0);
    record('emotion-empty', { canvases: 0 });

    statsMode = 'fail';
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.data-state [role="alert"]').waitFor();
    record('emotion-error', { errorState: true, retry: await page.getByRole('button', { name: '重试', exact: true }).count() });
    statsMode = 'ok';
    await page.getByRole('button', { name: '重试', exact: true }).click();
    await page.locator('.chart-card canvas').waitFor();
    record('emotion-retry', { recovered: true });

    assert.deepEqual(errors, []);
    record('complete', { pageErrors: 0, reports: reports.length });
  } finally {
    await context.close();
    await browser.close();
  }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
```

- [ ] **Step 3: 人工检查截图**

逐张查看 `output/playwright/b4-*.png`：

- Dashboard 浅色：4 张图表卡片，文字、坐标轴、饼图外部标签清晰；没有薄荷绿残留。
- Dashboard 深色：卡片背景与文字随主题反转；饼图外部标签与折线可见；无白底残留。
- Teal 主题：`手作内容分布` 的柱子从靛蓝变为青绿（脚本已验证颜色统计；截图可人工确认）。
- Empty：4 张图表卡片显示空态，无 canvas；不出现「加载失败」冒充空数据。
- Error：页面显示错误横幅，首次失败时无空图表；刷新失败时旧图表保留。
- Analytics：5 张图表均渲染。
- Emotion：饼图外部标签、表格与免责声明同屏；深色下可读；空态/错误态/重试正常。

- [ ] **Step 4: 更新 ledger 并停止本次启动的服务**

在 `.superpowers/sdd/2026-10-28-admin-b4-chart-theme/progress.md` 记录：任务提交号、主题桥偏差（D8 HSL → EP `-dark-2`）、浏览器实测结果、截图路径、未验证项（真实数据下的标签重叠、超长分类名称需后续观察）、启动/停止的进程号。

只结束本次启动的 backend/Vite 进程，不结束用户其他进程。

- [ ] **Step 5: 汇报并请求提交确认**

展示 `git status --short` 与 `git diff --stat`（若有未提交的验证修正），区分「已实现 / 本次验证 / 未验证」。不要 push；不要 stage 用户既有 docx。

---

## Self-Review

**Spec 覆盖：**

- P-7 / D8：`buildChartTheme` + `useChartTheme` + 删除两处本地色板 + 空态 + 线型 + 饼图外部标签 → Task 1/2/4/5。
- D9 `ChartCard`：Task 3。
- D10 对比度/图形对比/键盘/aria：主题令牌、`AriaComponent`、焦点环保留 → Task 2/4/5/6。
- D11 情绪页免责声明：Task 5。
- 验收：主题切换、深浅模式、空态、错误态、类型检查、构建、单测、hex 门禁 → Task 6。
- B4 不含 B5 页面滚动改造与 B5 的剩余文本色清理；Dashboard 卡片圆角沿用既有布局，不在本计划统一重排。

**占位符扫描：** 无 TBD/TODO；每个代码步骤均给出完整实现或完整替换片段。

**类型一致性：** `ChartTheme` 字段在 Task 1/2/4/5 一致；`useChartTheme()` 返回 `{ theme }`；`ChartCard` props 与两个消费页一致；`OperationsChart` 新增的 `emptyText`/`emptyHint` 均在 Task 4 模板中使用。
