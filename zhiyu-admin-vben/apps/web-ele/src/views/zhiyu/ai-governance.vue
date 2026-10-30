<script setup lang="ts">
import type { AdminAiGovernanceApi } from '#/api/zhiyu/admin-ai-governance';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { getAiGovernanceOverview } from '#/api/zhiyu/admin-ai-governance';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';

import OperationsChart from './components/OperationsChart.vue';

const router = useRouter();
const days = ref(30);
const data = ref<AdminAiGovernanceApi.Overview | null>(null);
const loading = ref(false);
const error = ref('');
let generation = 0;

const blockCount = computed(
  () => Number(data.value?.actionCounts.find((item) => item.name === 'BLOCK')?.value ?? 0),
);
const rewriteCount = computed(
  () => Number(data.value?.actionCounts.find((item) => item.name === 'REWRITE')?.value ?? 0),
);
const actionRows = computed(() =>
  (data.value?.actionCounts ?? []).map((item) => ({ ...item, value: Number(item.value) })),
);
const sceneRows = computed(() =>
  (data.value?.sceneCounts ?? []).map((item) => ({ ...item, value: Number(item.value) })),
);
const reasonRows = computed(() =>
  (data.value?.reasonCounts ?? []).map((item) => ({ ...item, value: Number(item.value) })),
);
const chatModeLabel = computed(() =>
  data.value?.config.chatMode === 'MOCK' ? 'Mock 降级' : '外部模型',
);

const configRows = computed(() => {
  const config = data.value?.config;
  if (!config) return [];
  return [
    {
      label: '聊天模型',
      value: config.chatMode === 'MOCK' ? 'Mock 降级' : '外部模型',
      detail: config.chatVerified ? '已实测' : '未做连通实测',
      tone: config.chatMode === 'MOCK' ? 'warning' : 'info',
    },
    {
      label: '向量检索',
      value: config.vectorMode === 'LOCAL_FILE' ? '本地文件' : '未配置',
      detail: config.vectorVerified ? '已实测' : '未做连通实测',
      tone: config.vectorMode === 'LOCAL_FILE' ? 'success' : 'info',
    },
    {
      label: '语音服务',
      value: config.speechConfigured ? '已配置' : '未配置',
      detail: '未做连通实测',
      tone: config.speechConfigured ? 'success' : 'info',
    },
    {
      label: '熔断存储',
      value: config.resilienceStore || '未知',
      detail: '按资源 key 隔离 · 连续失败 5 次 / 60 秒',
      tone: 'info',
    },
  ];
});

async function load() {
  const requestId = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await getAiGovernanceOverview(days.value);
    if (requestId === generation) data.value = result;
  } catch (e: any) {
    if (requestId === generation) {
      error.value = e?.message || 'AI 治理数据暂时无法读取';
    }
  } finally {
    if (requestId === generation) loading.value = false;
  }
}

function range(value: number) {
  days.value = value;
  load();
}

function actionTagType(action: string) {
  switch (action) {
    case 'PASS':
      return 'success';
    case 'REWRITE':
    case 'REVIEW':
      return 'warning';
    case 'BLOCK':
      return 'danger';
    default:
      return 'info';
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader
      title="AI 治理"
      description="安全审核事件与能力配置状态。只统计事件，不推算调用量、成功率或延迟。"
      :loading="loading"
      @refresh="load"
    />
    <p class="m-0 business-note leading-relaxed">
      本页不返回用户 ID、原始输入输出、自由文本原因或匹配关键词。配置存在不等于服务连通，真实模型联调需单独验收。
    </p>

    <el-card shadow="never">
      <div class="governance-toolbar">
        <div>
          <span class="toolbar-label">统计窗口</span>
          <div class="range" role="group" aria-label="统计时间窗口">
            <el-button
              v-for="value in [7, 30, 90]"
              :key="value"
              size="small"
              :type="days === value ? 'primary' : 'default'"
              :aria-pressed="days === value"
              @click="range(value)"
            >
              近 {{ value }} 天
            </el-button>
          </div>
        </div>
        <span v-if="data" class="generated">
          更新于 {{ formatTime(data.generatedAt) }}
        </span>
      </div>
    </el-card>

    <DataErrorBanner v-if="error" :error="error" :retrying="loading" @retry="load" />

    <template v-if="data">
      <section class="governance-metrics" aria-label="AI 治理指标">
        <el-card shadow="never">
          <span class="metric-label">安全事件</span>
          <strong>{{ Number(data.totalEvents).toLocaleString() }}</strong>
          <span class="metric-caption">近 {{ data.windowDays }} 天</span>
        </el-card>
        <el-card shadow="never">
          <span class="metric-label">BLOCK</span>
          <strong>{{ Number(blockCount).toLocaleString() }}</strong>
          <span class="metric-caption">输入/输出拦截事件</span>
        </el-card>
        <el-card shadow="never">
          <span class="metric-label">REWRITE</span>
          <strong>{{ Number(rewriteCount).toLocaleString() }}</strong>
          <span class="metric-caption">输出越界改写事件</span>
        </el-card>
        <el-card shadow="never">
          <span class="metric-label">聊天模式</span>
          <strong class="metric-mode">{{ chatModeLabel }}</strong>
          <span class="metric-caption">{{ data.config.chatVerified ? '已实测' : '未做连通实测' }}</span>
        </el-card>
      </section>

      <el-card shadow="never">
        <div class="config-heading">
          <div>
            <h3>能力配置状态</h3>
            <p>配置存在、服务连通、合成输入实测是三种不同状态。</p>
          </div>
          <el-tag type="info" effect="plain">未验证即未验证</el-tag>
        </div>
        <div class="config-grid">
          <div v-for="row in configRows" :key="row.label" class="config-item">
            <span class="config-label">{{ row.label }}</span>
            <el-tag :type="row.tone" size="small">{{ row.value }}</el-tag>
            <span class="config-detail">{{ row.detail }}</span>
          </div>
        </div>
        <p class="config-note">{{ data.config.note }}</p>
      </el-card>

      <div class="governance-charts">
        <OperationsChart
          title="安全动作分布"
          subtitle="近窗口内所有安全审核动作"
          kind="pie"
          :rows="actionRows"
          empty-text="暂无安全事件"
          empty-hint="产生 AI 安全审核事件后会在这里显示分布"
        />
        <OperationsChart
          title="场景分布"
          subtitle="按审计场景计数，不代表调用量"
          kind="bar"
          :rows="sceneRows"
          empty-text="暂无场景数据"
          empty-hint="安全审核事件产生后会在这里显示场景分布"
        />
      </div>
      <OperationsChart
        title="原因分类"
        subtitle="只显示归一化分类，不显示匹配关键词"
        kind="bar"
        :rows="reasonRows"
        empty-text="暂无原因分类"
        empty-hint="产生安全审核事件后会在这里显示分类"
      />

      <el-card shadow="never">
        <div class="recent-heading">
          <div>
            <h3>最近安全事件</h3>
            <p>仅展示时间、场景、动作与归一化原因分类。</p>
          </div>
          <el-button link type="primary" @click="router.push('/zhiyu/business/audit-logs')">
            查看 AI 审计日志
          </el-button>
        </div>
        <el-table :data="data.recentEvents" style="width: 100%">
          <template #empty>
            <DataState empty-text="暂无安全事件" empty-hint="窗口内还没有 AI 安全审核记录" />
          </template>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="sceneLabel" label="场景" width="120" />
          <el-table-column label="动作" width="110">
            <template #default="{ row }">
              <el-tag :type="actionTagType(row.action)" size="small">{{ row.action }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reasonCategory" label="原因分类" min-width="160" />
        </el-table>
      </el-card>

      <p class="privacy-note">{{ data.privacyNote }}</p>
    </template>

    <el-card v-else-if="loading" shadow="never">
      <el-skeleton :rows="6" animated />
    </el-card>
    <DataState
      v-else-if="error"
      :error="error"
      :loading="loading"
      empty-text="AI 治理数据暂时不可用"
      @retry="load"
    />
  </div>
</template>

<style scoped>
.governance-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.toolbar-label {
  margin-right: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.range {
  display: inline-flex;
  gap: 6px;
}
.generated {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.governance-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.governance-metrics :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.metric-label,
.metric-caption {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.governance-metrics strong {
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -0.6px;
  color: var(--el-text-color-primary);
}
.governance-metrics .metric-mode {
  font-size: 22px;
}
.config-heading,
.recent-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.config-heading h3,
.recent-heading h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.config-heading p,
.recent-heading p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}
.config-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
}
.config-label {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.config-detail {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
.config-note,
.privacy-note {
  margin: 16px 0 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}
.governance-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.privacy-note {
  margin: 0;
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border-radius: 10px;
}
@media (max-width: 1100px) {
  .governance-metrics,
  .config-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .governance-metrics,
  .config-grid,
  .governance-charts {
    grid-template-columns: minmax(0, 1fr);
  }
  .governance-toolbar {
    align-items: flex-start;
  }
  .generated {
    display: none;
  }
}
</style>
