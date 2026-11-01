<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElButton, ElCard, ElTable, ElTableColumn, ElTag } from 'element-plus';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import { requestClient } from '#/api/request';

type HealthComponent = {
  detail: string;
  key: string;
  label: string;
  latencyMs?: number | null;
  status: string;
};
type Health = {
  components: HealthComponent[];
  counts: {
    adminOps24h: number;
    aiAudit24h: number;
    blocked24h: number;
    heritageVersions: number;
    practiceSessions: number;
  };
  generatedAt: string;
  jvm: { heapMaxMb: number; heapUsedMb: number; uptimeSeconds: number };
  overall: string;
};
type TableCount = { present: boolean; rows: number; table: string };
type BackupManifest = {
  generatedAt: string;
  missingTables: string[];
  presentTables: number;
  tables: TableCount[];
  totalTables: number;
};

const health = ref<Health | null>(null);
const loading = ref(false);
const error = ref('');
const manifest = ref<BackupManifest | null>(null);
const manifestLoading = ref(false);
const manifestError = ref('');
const copyState = ref('');

const database = computed(() => health.value?.components.find(item => item.key === 'database'));
const statusType = (status: string) =>
  status === 'UP' ? 'success' : status === 'WARN' ? 'warning' : status === 'DOWN' ? 'danger' : 'info';
const overallLabel = computed(() =>
  health.value?.overall === 'UP'
    ? '运行正常'
    : health.value?.overall === 'DEGRADED'
      ? '存在降级项'
      : '存在故障项',
);
const overallType = computed(() =>
  health.value?.overall === 'UP' ? 'success' : health.value?.overall === 'DEGRADED' ? 'warning' : 'danger',
);

function formatTime(value?: string | null) {
  if (!value) return '—';
  return value.replace('T', ' ').slice(0, 19);
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    health.value = await requestClient.get<Health>('/admin/ops/health');
  } catch (event: any) {
    error.value = event?.message || '运行健康检查失败，请重试。';
  } finally {
    loading.value = false;
  }
}

async function loadManifest() {
  manifestLoading.value = true;
  manifestError.value = '';
  try {
    manifest.value = await requestClient.get<BackupManifest>('/admin/ops/backup-manifest');
  } catch (event: any) {
    manifestError.value = event?.message || '备份清单读取失败，请重试。';
  } finally {
    manifestLoading.value = false;
  }
}

async function copyManifest() {
  if (!manifest.value) await loadManifest();
  if (!manifest.value) return;
  try {
    await navigator.clipboard.writeText(JSON.stringify(manifest.value, null, 2));
    copyState.value = '已复制';
  } catch {
    copyState.value = '复制失败，请重试';
  }
  setTimeout(() => (copyState.value = ''), 2000);
}

onMounted(load);
</script>

<template>
  <div class="zy-business-page health-page flex flex-col gap-4">
    <BusinessPageHeader
      title="运行健康"
      description="只读检查数据库、缓存、媒体、RAG 与 AI 配置；不执行备份、恢复或外部 AI 调用。"
      :loading="loading"
      @refresh="load"
    >
      <el-button :loading="loading" @click="load">刷新检查</el-button>
    </BusinessPageHeader>

    <DataErrorBanner v-if="error && health" :error="error" :retrying="loading" @retry="load" />
    <el-card v-if="!health && !loading" shadow="never">
      <DataState :loading="false" :error="error" empty-text="暂无健康检查结果" @retry="load" />
    </el-card>

    <template v-if="health">
      <div class="health-hero">
        <div>
          <span class="eyebrow">READ-ONLY HEALTH</span>
          <h2>系统整体：{{ overallLabel }}</h2>
          <p>
            检查于 {{ formatTime(health.generatedAt) }} · 这是只读自检，不代替真实压测、备份恢复或外部服务验收
          </p>
        </div>
        <el-tag :type="overallType" effect="dark" size="large">{{ health.overall }}</el-tag>
      </div>

      <div class="metric-grid">
        <el-card shadow="never" class="metric-card">
          <span>数据库延迟</span>
          <strong>{{ database?.latencyMs != null ? `${database.latencyMs} ms` : '—' }}</strong>
          <small>{{ database?.detail || '等待数据库检查' }}</small>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>AI 审计 · 24h</span>
          <strong>{{ health.counts.aiAudit24h }}</strong>
          <small>其中 BLOCK {{ health.counts.blocked24h }} 次</small>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>练习会话</span>
          <strong>{{ health.counts.practiceSessions }}</strong>
          <small>非遗目录版本 {{ health.counts.heritageVersions }} 个</small>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>JVM 堆内存</span>
          <strong>{{ health.jvm.heapUsedMb }} / {{ health.jvm.heapMaxMb }} MB</strong>
          <small>已运行 {{ Math.floor(health.jvm.uptimeSeconds / 60) }} 分钟</small>
        </el-card>
      </div>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>组件状态</span>
            <span class="card-hint">只读检查 · 不返回密钥或文件路径</span>
          </div>
        </template>
        <el-table :data="health.components" stripe>
          <el-table-column prop="label" label="组件" min-width="130" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="说明" min-width="320" />
          <el-table-column label="延迟" width="100">
            <template #default="{ row }">
              {{ row.latencyMs != null ? `${row.latencyMs} ms` : '—' }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>备份自检</span>
            <div class="backup-actions">
              <el-button :loading="manifestLoading" @click="loadManifest">读取清单</el-button>
              <el-button :disabled="!manifest" @click="copyManifest">
                {{ copyState || '复制清单 JSON' }}
              </el-button>
            </div>
          </div>
        </template>
        <el-alert
          title="这里只核对关键表是否存在和行数，不读取业务内容、不生成或下载备份文件。实际备份与恢复请按 docs/runbook-backup-restore.md 在隔离库执行。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="manifestError"
          :title="manifestError"
          type="error"
          :closable="false"
          show-icon
          class="backup-error"
        />
        <div v-if="manifest" class="manifest-summary">
          <span>关键表 {{ manifest.presentTables }} / {{ manifest.totalTables }} 存在</span>
          <span v-if="manifest.missingTables.length">缺少：{{ manifest.missingTables.join('、') }}</span>
          <span>生成于 {{ formatTime(manifest.generatedAt) }}</span>
        </div>
        <el-table v-if="manifest" :data="manifest.tables" max-height="320" stripe>
          <el-table-column prop="table" label="表" min-width="220" />
          <el-table-column label="存在" width="90">
            <template #default="{ row }">
              <el-tag :type="row.present ? 'success' : 'danger'" effect="plain">
                {{ row.present ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rows" label="行数" width="140" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.health-page {
  max-width: 1280px;
  margin: 0 auto;
}
.health-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9), var(--el-bg-color));
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--el-color-primary);
}
.health-hero h2 {
  margin: 8px 0 6px;
  font-size: 24px;
  color: var(--el-text-color-primary);
}
.health-hero p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.metric-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 118px;
}
.metric-card span {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.metric-card strong {
  font-size: 26px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.metric-card small {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  font-weight: 600;
}
.card-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.backup-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.backup-error {
  margin-top: 14px;
}
.manifest-summary {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin: 16px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .health-hero {
    align-items: flex-start;
    flex-direction: column;
  }
  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
