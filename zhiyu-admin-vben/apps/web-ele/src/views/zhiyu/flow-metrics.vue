<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { ElMessage } from 'element-plus';

import type { FlowMetricsDashboard } from '#/api/zhiyu/admin';
import { getFlowMetricsDashboard } from '#/api/zhiyu/admin';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import ChartCard from '#/components/chart-card/ChartCard.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import { dictLabel, PROJECT_LEVEL } from '#/constants/status-dict';
import { useChartTheme } from '#/composables/use-chart-theme';
import { withAlpha } from '#/utils/chart-theme';

/**
 * 心流/专注度量看板（阶段 1-2，2026-11-05）。
 * 口径全部吃后端（docs/flow-metrics-dashboard-literature-20261105.md §3/§9）：
 * - 指标名由 calibration 效度决定（metricLabel），前端不自造「心流」命名；
 * - 只报人×周中位数与分布（p50/p90），不用均值大数字；
 * - 分层分面独立呈现（不跨难度并线）；operational 下架批次单独成面；
 * - 页脚固定声明 engagement ≠ 疗效（不可折叠、非 hover）。
 */

const { theme } = useChartTheme();
const trendChartRef = ref<EchartsUIType>();
const distChartRef = ref<EchartsUIType>();
const { renderEcharts: renderTrend } = useEcharts(trendChartRef);
const { renderEcharts: renderDist } = useEcharts(distChartRef);

const loading = ref(false);
const error = ref('');
const data = ref<FlowMetricsDashboard | null>(null);
/** 区间（YYYY-MM-DD），默认近 30 天；跨度 ≤90 天（后端 1001 兜底，前端先拦） */
const today = new Date();
const monthAgo = new Date(Date.now() - 29 * 24 * 3600 * 1000);
const fmt = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};
const range = ref<[string, string]>([fmt(monthAgo), fmt(today)]);
let turn = 0;

const calibrationInsufficient = computed(
  () => data.value?.calibration?.status === 'insufficient',
);

const hasDistribution = computed(
  () => (data.value?.distribution?.personWeeks ?? 0) > 0,
);

/** 时长桶 / 下架分面标签（level 走 PROJECT_LEVEL 字典，unknown 如实显示「未分层」） */
const BUCKET_LABELS: Record<string, string> = {
  lt5: '<5 分钟',
  m5to15: '5–15 分钟',
  m15to30: '15–30 分钟',
  gt30: '>30 分钟',
};
const KIND_LABELS: Record<string, string> = {
  none: '无下架',
  operational: '运营下架',
  compliance: '合规下架',
};
const EVENT_LABELS: Record<string, string> = {
  step_leave: '步骤停留',
  dwell_stagnation: '反复停留',
  step_backtrack: '步骤回退',
  breathing_prompt: '呼吸触发',
  breathing_responded: '呼吸响应',
  suspected_away: '疑似离席（仅标记）',
  export: '导出成功',
  session_archive: '归档转只读',
};

function levelLabel(key: string) {
  return key === 'unknown' ? '未分层' : dictLabel(PROJECT_LEVEL, key);
}
function facetLabel(group: string, key: string) {
  if (group === 'byLevel') return levelLabel(key);
  if (group === 'byDurationBucket') return BUCKET_LABELS[key] ?? key;
  return KIND_LABELS[key] ?? key;
}
function pct(v?: null | number) {
  return v === null || v === undefined ? '—' : `${(v * 100).toFixed(1)}%`;
}
function secondsText(s?: number) {
  if (!s) return '0 分钟';
  return `${Math.round(s / 60)} 分钟`;
}

/** 趋势图：人×周中位数折线 + IQR 带 + 活跃人数副轴；2026-11-05 口径变更 markLine 注明前后不可比 */
function renderTrendChart() {
  const d = data.value;
  if (!d || !d.weekly.length) return;
  const c = theme.value;
  const ts = (w: { weekStart: string }) => `${w.weekStart}T00:00:00+08:00`;
  renderTrend({
    animation: false,
    aria: {
      enabled: true,
      label: {
        description:
          '人×周专注占比中位数趋势。2026年11月5日口径变更，变更前后数据不可比。',
      },
    },
    grid: { bottom: 56, left: 56, right: 56, top: 40 },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemHeight: 8,
      itemWidth: 8,
      textStyle: { color: c.text },
    },
    series: [
      {
        // IQR 带：p25 透明底 + (p75-p25) 区域
        name: 'IQR 底',
        type: 'line',
        data: d.weekly.map((w) => [ts(w), w.p25 ?? 0]),
        lineStyle: { opacity: 0 },
        stack: 'iqr',
        symbol: 'none',
        tooltip: { show: false },
      },
      {
        name: 'IQR（p25–p75）',
        type: 'line',
        areaStyle: { color: withAlpha(c.colors[0], 0.14) },
        data: d.weekly.map((w) => [
          ts(w),
          w.p25 !== null && w.p75 !== null && w.p25 !== undefined && w.p75 !== undefined
            ? w.p75 - w.p25
            : 0,
        ]),
        lineStyle: { opacity: 0 },
        stack: 'iqr',
        symbol: 'none',
        tooltip: { show: false },
      },
      {
        name: '人×周中位占比',
        type: 'line',
        color: c.colors[0],
        data: d.weekly.map((w) => [ts(w), w.medianRatio]),
        lineStyle: { width: 2.5 },
        markLine: {
          data: [{ xAxis: '2026-11-05T00:00:00+08:00' }],
          label: {
            color: c.colors[1],
            formatter: '2026-11-05 口径变更\n前后不可比',
            fontSize: 11,
          },
          lineStyle: { color: c.colors[1], type: 'dashed', width: 1.5 },
          symbol: 'none',
        },
        smooth: false,
        symbol: 'circle',
        symbolSize: 7,
      },
      {
        name: '活跃人数',
        type: 'bar',
        barMaxWidth: 18,
        color: withAlpha(c.colors[2], 0.55),
        data: d.weekly.map((w) => [ts(w), w.activeUsers]),
        yAxisIndex: 1,
      },
    ],
    tooltip: {
      backgroundColor: c.tooltipBackground,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.text },
      trigger: 'axis',
      valueFormatter: (v: any) =>
        typeof v === 'number' && v <= 1 ? `${(v * 100).toFixed(1)}%` : v,
    },
    xAxis: {
      type: 'time',
      axisLabel: { color: c.text },
      axisLine: { lineStyle: { color: c.border } },
    },
    yAxis: [
      {
        type: 'value',
        max: 1,
        min: 0,
        axisLabel: {
          color: c.text,
          formatter: (v: number) => `${Math.round(v * 100)}%`,
        },
        name: '占比',
        nameTextStyle: { color: c.text },
        splitLine: { lineStyle: { color: c.grid } },
      },
      {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: c.text },
        name: '人数',
        nameTextStyle: { color: c.text },
        splitLine: { show: false },
      },
    ],
  });
}

/** 分布图：人×周中位占比的分桶样本数（不给均值大数字） */
function renderDistChart() {
  const d = data.value;
  if (!d || !hasDistribution.value) return;
  const c = theme.value;
  renderDist({
    animation: false,
    aria: {
      enabled: true,
      label: { description: '人×周专注占比中位数的分桶样本数分布。' },
    },
    grid: { bottom: 48, left: 48, right: 24, top: 24 },
    series: [
      {
        type: 'bar',
        barMaxWidth: 32,
        data: d.distribution.buckets.map((b) => b.count),
        itemStyle: { borderRadius: [4, 4, 0, 0], color: c.colors[0] },
      },
    ],
    tooltip: {
      backgroundColor: c.tooltipBackground,
      borderColor: c.tooltipBorder,
      formatter: (p: any) => {
        const b = d.distribution.buckets[p.dataIndex];
        if (!b) return '';
        const hi = b.max === null || b.max === undefined ? '100%+' : `${b.max * 100}%`;
        return `${b.min * 100}%–${hi}：${p.value} 个人×周`;
      },
      textStyle: { color: c.text },
    },
    xAxis: {
      type: 'category',
      axisLabel: { color: c.text, interval: 0, rotate: 30 },
      axisLine: { lineStyle: { color: c.border } },
      data: d.distribution.buckets.map((b) =>
        b.max === null || b.max === undefined
          ? '90–100%+'
          : `${b.min * 100}–${b.max * 100}%`,
      ),
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: c.text },
      splitLine: { lineStyle: { color: c.grid } },
    },
  });
}

async function renderAll() {
  await nextTick();
  renderTrendChart();
  renderDistChart();
}

watch(theme, () => renderAll());

async function load() {
  if (!range.value?.[0] || !range.value?.[1]) {
    ElMessage.warning('请选择统计区间');
    return;
  }
  const t = ++turn;
  loading.value = true;
  error.value = '';
  try {
    const res = await getFlowMetricsDashboard({
      from: range.value[0],
      to: range.value[1],
    });
    if (t !== turn) return;
    data.value = res;
  } catch {
    if (t === turn) {
      // 加载失败不显示假数据：清空旧数据，只留错误态
      data.value = null;
      error.value = '看板数据暂未读取成功，请稍后重试。';
    }
  } finally {
    if (t === turn) loading.value = false;
  }
  if (t === turn && !error.value) await renderAll();
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page flow-metrics-page">
    <BusinessPageHeader
      title="专注度量"
      description="创作会话的停留与专注行为度量（行为代理）。口径：分母=步骤停留活跃秒数，分子=阈值后深度沉浸秒数；人×周中位数与分布，不报均值。"
      :loading="loading"
      @refresh="load"
    />

    <div class="range-bar zy-glass">
      <label class="range-bar__label" for="flow-range">统计区间</label>
      <el-date-picker
        id="flow-range"
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
        style="width: 260px"
        @change="load"
      />
      <span class="range-bar__hint">跨度不超过 90 天</span>
    </div>

    <DataErrorBanner v-if="error" :message="error" @retry="load" />

    <template v-if="data && !error">
      <!-- 指标名与效度说明：标题吃后端 metricLabel，insufficient 显式说明 -->
      <section class="metric-head zy-glass zy-slide-up">
        <p class="overline">METRIC</p>
        <h2 class="metric-head__title">{{ data.metricLabel }}</h2>
        <p v-if="calibrationInsufficient" class="metric-head__warn" role="note">
          样本不足（n={{ data.calibration.sampleSize }} &lt;
          {{ data.calibration.minSamples }}），当前展示为「专注停留占比」，尚未验证与自评心流的一致性。
        </p>
        <p v-else class="metric-head__ok">
          自评校准 ρ={{ data.calibration.rho?.toFixed(2) }}（n={{
            data.calibration.sampleSize
          }}，阈值 {{ data.calibration.threshold }}）
        </p>
        <div class="metric-head__stats">
          <div class="stat">
            <span class="stat__label">人×周中位数 p50</span>
            <span class="stat__value">{{ pct(data.distribution.p50) }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">人×周 p90</span>
            <span class="stat__value">{{ pct(data.distribution.p90) }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">人×周样本</span>
            <span class="stat__value">{{ data.distribution.personWeeks }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">活跃人数 / 纳入会话</span>
            <span class="stat__value">
              {{ data.totals.activeUsers }} / {{ data.totals.includedSessions }}
            </span>
          </div>
        </div>
      </section>

      <!-- 趋势 -->
      <ChartCard
        title="周趋势"
        description="人×周会话占比中位数 + IQR（p25–p75）+ 活跃人数副轴。2026-11-05 三处口径变更（归档态 / 只读剔除 / flow_seconds 语义），变更点前后数据不得跨期比较。"
        :loading="loading"
        :empty="!data.weekly.length"
        empty-text="区间内暂无可统计的创作会话"
        min-height="320px"
      >
        <EchartsUI ref="trendChartRef" height="320px" width="100%" />
      </ChartCard>

      <!-- 分布 + 分层 -->
      <div class="grid-2">
        <ChartCard
          title="占比分布"
          description="人×周中位占比的分桶样本数。分布通常双峰（快速流失 vs 深度投入），故不用均值代表整体。"
          :loading="loading"
          :empty="!hasDistribution"
          empty-text="暂无人×周样本"
          min-height="260px"
        >
          <EchartsUI ref="distChartRef" height="260px" width="100%" />
        </ChartCard>

        <section class="facet-card zy-glass zy-slide-up">
          <h3>分层（分面独立，不并线比较）</h3>
          <p class="facet-card__note">
            不同难度项目的挑战-技能前提不同，占比天然不可比；下架批次伴随冲刺行为，单独成面。单元用户数
            &lt;10 标灰（样本稀疏，中位数不稳定）。
          </p>
          <div
            v-for="group in [
              { key: 'byLevel', title: '按难度' },
              { key: 'byDurationBucket', title: '按活跃时长' },
              { key: 'byTakedownKind', title: '按下架批次' },
            ]"
            :key="group.key"
            class="facet-group"
          >
            <h4>{{ group.title }}</h4>
            <p
              v-if="!(data.facets as any)[group.key]?.length"
              class="facet-empty"
            >
              区间内无样本
            </p>
            <ul v-else class="facet-list">
              <li
                v-for="cell in (data.facets as any)[group.key]"
                :key="cell.key"
                class="facet-cell"
                :class="{ 'facet-cell--sparse': cell.sparse }"
              >
                <span class="facet-cell__key">{{ facetLabel(group.key, cell.key) }}</span>
                <span class="facet-cell__metric">
                  p50 {{ pct(cell.p50) }} · p90 {{ pct(cell.p90) }}
                </span>
                <span class="facet-cell__n">
                  {{ cell.sessions }} 会话 / {{ cell.users }} 人
                  <template v-if="cell.sparse">（稀疏）</template>
                </span>
              </li>
            </ul>
          </div>
        </section>
      </div>

      <!-- 数据质量与剔除透明 -->
      <section class="quality zy-glass zy-slide-up">
        <h3>样本与剔除</h3>
        <div class="quality__grid">
          <div class="stat">
            <span class="stat__label">区间内会话</span>
            <span class="stat__value">{{ data.totals.sessionsInRange }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">在途（未进分布）</span>
            <span class="stat__value">{{ data.totals.inProgressSessions }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">剔除：只读/归档</span>
            <span class="stat__value">{{ data.totals.excludedReadOnly }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">剔除：待审项目</span>
            <span class="stat__value">{{ data.totals.excludedPendingProject }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">剔除：无停留事件</span>
            <span class="stat__value">{{ data.totals.excludedNoEvents }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">分母活跃时长</span>
            <span class="stat__value">{{ secondsText(data.totals.activeSeconds) }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">分子沉浸时长</span>
            <span class="stat__value">{{ secondsText(data.totals.flowSeconds) }}</span>
          </div>
        </div>
        <template v-if="data.exclusions.length">
          <h4 class="quality__sub">剔除明细（前 {{ data.exclusions.length }} 条）</h4>
          <ul class="exclusion-list">
            <li v-for="x in data.exclusions" :key="x.sessionId">
              会话 {{ x.sessionId }} · 项目 {{ x.projectId ?? '—' }} ·
              {{ x.reason === 'read_only' ? '只读/归档' : '待审项目' }}
              <template v-if="x.readOnlySince">
                · 只读起点 {{ x.readOnlySince.replace('T', ' ').substring(0, 16) }}
              </template>
            </li>
          </ul>
        </template>
        <h4 class="quality__sub">行为信号（区间内事件计数）</h4>
        <ul class="event-list">
          <li v-for="(v, k) in data.events" :key="k">
            {{ EVENT_LABELS[k] ?? k }}：{{ v }}
          </li>
        </ul>
      </section>

      <!-- 自评校准响应率 -->
      <section class="self-report zy-glass zy-slide-up">
        <h3>自评校准采样</h3>
        <p class="self-report__desc">
          会话结束后可选单题（1–7）。区分「没看到」（抽中未响应）与「看到不答」（主动跳过）。
        </p>
        <div class="quality__grid">
          <div class="stat">
            <span class="stat__label">抽中</span>
            <span class="stat__value">{{ data.selfReport.sampled }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">作答</span>
            <span class="stat__value">{{ data.selfReport.answered }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">看到不答</span>
            <span class="stat__value">{{ data.selfReport.skipped }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">没看到</span>
            <span class="stat__value">{{ data.selfReport.noResponse }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">响应率</span>
            <span class="stat__value">{{ pct(data.selfReport.responseRate) }}</span>
          </div>
        </div>
      </section>

      <!-- 不可得清单：单独一栏，如实展示 -->
      <section class="unavailable zy-glass zy-slide-up">
        <h3>当前不可得</h3>
        <p class="unavailable__desc">以下指标没有可靠数据源，本页不以 0 或代理值冒充。</p>
        <ul class="unavailable__list">
          <li v-for="u in data.unavailable" :key="u.key">
            <strong>{{ u.label }}</strong>
            <span>{{ u.reason }}</span>
          </li>
        </ul>
      </section>

      <!-- 解读须知（三条禁止） -->
      <section class="reading zy-glass zy-slide-up">
        <h3>解读须知</h3>
        <ul>
          <li>不跨难度并线：分层占比各自独立呈现，不作横向排名；高阶项目占比偏低是设计使然。</li>
          <li>不用单一大数字代表整体：本页只报中位数与分布，没有「整体占比均值」。</li>
          <li>
            不把占比上升读作疗愈变好，也不把下降直接读作变差——先查样本构成与活跃人数变化。
          </li>
          <li>
            口径变更点（<template v-for="c in data.definitionChanges" :key="c.key">{{ c.date }} </template>）前后数据不得跨期比较；flow_seconds 修复后数值下降是口径修正，不是用户专注度下降。
          </li>
        </ul>
      </section>

      <p class="updated">
        数据更新于
        {{ new Date(data.generatedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}
        · 区间 {{ data.range.from }} ~ {{ data.range.to }}（{{ data.range.days }} 天）
      </p>
    </template>

    <!-- 空态（加载成功但无数据） -->
    <section
      v-else-if="!loading && !error"
      class="empty-panel zy-glass"
    >
      <h2>暂无可展示的数据</h2>
      <p>当前区间内没有可统计的创作会话。调整区间或等数据产生后再看。</p>
    </section>

    <!-- 页脚固定声明：不可折叠、非 hover，常驻可见 -->
    <footer class="flow-footer" role="note">
      <p>
        engagement ≠ 疗效；本页度量的是停留与专注行为，不代表疗愈效果。
      </p>
      <p>本系统不能替代专业心理咨询。</p>
    </footer>
  </div>
</template>

<style scoped>
.flow-metrics-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.range-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
}

.range-bar__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.range-bar__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.overline {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 2.8px;
  color: var(--el-text-color-regular);
}

.metric-head {
  padding: 24px;
}

.metric-head__title {
  margin: 0;
  font-size: 26px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.metric-head__warn {
  margin: 10px 0 0;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 8px;
}

.metric-head__ok {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.metric-head__stats,
.quality__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 10px;
}

.stat__label {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.stat__value {
  font-size: 20px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.grid-2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
}

.facet-card,
.quality,
.self-report,
.unavailable,
.reading,
.empty-panel {
  padding: 24px;
}

.facet-card h3,
.quality h3,
.self-report h3,
.unavailable h3,
.reading h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.facet-card__note,
.self-report__desc,
.unavailable__desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.facet-group {
  margin-top: 14px;
}

.facet-group h4,
.quality__sub {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.facet-empty {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.facet-list,
.exclusion-list,
.event-list,
.unavailable__list,
.reading ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.facet-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 8px 12px;
  margin-bottom: 6px;
  font-size: 13px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  color: var(--el-text-color-primary);
}

/* 样本稀疏单元标灰（中位数不稳定） */
.facet-cell--sparse {
  opacity: 0.55;
}

.facet-cell__key {
  font-weight: 600;
}

.facet-cell__metric {
  color: var(--el-text-color-regular);
}

.facet-cell__n {
  margin-left: auto;
  color: var(--el-text-color-regular);
}

.exclusion-list,
.event-list {
  font-size: 13px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
}

.event-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 4px 16px;
}

.quality__sub {
  margin-top: 16px;
}

.unavailable__list li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  margin-bottom: 8px;
  font-size: 13px;
  background: var(--el-fill-color-lighter);
  border-left: 3px solid var(--el-color-info-light-5);
  border-radius: 8px;
}

.unavailable__list strong {
  color: var(--el-text-color-primary);
}

.unavailable__list span {
  color: var(--el-text-color-regular);
}

.reading ul li {
  position: relative;
  padding-left: 18px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.reading ul li::before {
  position: absolute;
  left: 0;
  content: '·';
  color: var(--el-color-primary);
}

.updated {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.empty-panel h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.empty-panel p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

/* 页脚固定声明：常驻可见，不折叠、不依赖 hover */
.flow-footer {
  padding: 16px 24px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  border-top: 1px solid var(--el-border-color);
  border-radius: 10px;
}

.flow-footer p {
  margin: 0;
}

@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
