<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  type AdminAiRoutingApi,
  getAiRoutingOverview,
} from '#/api/zhiyu/admin-ai-routing';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';

/**
 * AI 模型路由只读面板（2026-11-08）：答辩演示与日常运维自查。
 *
 * 三段回答三个问题：
 * ① 现在谁在干活——capabilities 配置呈现（档位/云模型/cloud-only/兜底生效值）；
 * ② 最近实际走了谁——distribution 按 scene+provider 的纯计数（近 7 天默认）；
 * ③ 有没有异常——前端用 ①+② 推导（cloud-only 出现本地记录应为 0；
 *    云端在线但本地占比高；兜底率异常升高）。
 *
 * 只读纪律：页面没有任何配置写入口；provider 切换走启动参数（刻意如此）。
 * 口径边界：llm-message 由 message/stuck_guide/listening_summary 回落共用，
 * 其 ollama 行不能归给 cloud-only 的倾听小结——异常检查只看「独占 scene」。
 */

const TASK_LABELS: Record<string, string> = {
  chat: '情绪对话',
  recommend: '手作推荐',
  message: '寄语与创作回顾',
  rag: '知识检索（RAG）',
  stuck_guide: '卡点引导',
  listening_summary: '倾听小结',
  summary: '上下文摘要',
  companion: '陪伴编排',
  lineart: '线稿生成',
  moderation: '审核建议',
  moderation_shadow: '审核影子判定',
  crisis: '危机干预',
};

/** 云端失败率提示阈值（云端调用样本足够时超过才升级为 warning，否则 info） */
const CLOUD_FAIL_RATE_HINT = 0.2;
/** 云端失败率判定的最小云端调用样本量（低于此不升级，避免 1/1=100% 噪声） */
const CLOUD_FAIL_MIN_ATTEMPTS = 5;

const data = ref<AdminAiRoutingApi.Overview | null>(null);
const loading = ref(false);
const error = ref('');

/** 事故锚点临时覆盖（datetime-local 值）；null = 用服务端配置默认 */
const anchorDraft = ref<null | string>(null);
const anchorApplied = ref<null | string>(null);

/** 统计区间：默认近 7 天（YYYY-MM-DD 二元组） */
function defaultRange(): [string, string] {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 6);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return [fmt(from), fmt(to)];
}
const range = ref<[string, string]>(defaultRange());

function num(v: number | string | undefined): number {
  return Number(v ?? 0);
}

async function load() {
  const [fromDate, toDate] = range.value;
  const spanDays =
    (new Date(toDate).getTime() - new Date(fromDate).getTime()) / 86_400_000;
  if (spanDays < 0 || spanDays > 89) {
    ElMessage.error('统计区间最长 90 天');
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    data.value = await getAiRoutingOverview({
      from: `${fromDate}T00:00:00`,
      to: `${toDate}T23:59:59`,
      ...(anchorApplied.value ? { anchor: anchorApplied.value } : {}),
    });
  } catch (e: any) {
    error.value = e?.message || '模型路由总览加载失败，请重试。';
  } finally {
    loading.value = false;
  }
}

/** 应用临时锚点（datetime-local 值转 ISO 本地时间）；为空则不覆盖 */
function applyAnchor() {
  anchorApplied.value = anchorDraft.value || null;
  load();
}

/** 恢复服务端配置锚点 */
function resetAnchor() {
  anchorDraft.value = null;
  anchorApplied.value = null;
  load();
}

onMounted(load);

/* ── ① 现在谁在干活 ── */

const registry = computed(() => data.value?.registry);
const capabilities = computed(() => data.value?.capabilities ?? []);

function taskLabel(task: string) {
  return TASK_LABELS[task] ?? task;
}

/* ── ② 最近实际走了谁 ── */

/** scene → 归属能力（一个 scene 可被多能力共用） */
const sceneOwners = computed(() => {
  const map = new Map<string, AdminAiRoutingApi.Capability[]>();
  for (const cap of capabilities.value) {
    for (const scene of cap.sceneKeys) {
      const list = map.get(scene) ?? [];
      list.push(cap);
      map.set(scene, list);
    }
  }
  return map;
});

function ownersLabel(scene: string): string {
  const owners = sceneOwners.value.get(scene);
  if (!owners?.length) return '其他（语音/编排层）';
  return owners.map((c) => taskLabel(c.task)).join('、');
}

type DistRow = {
  scene: string;
  provider: null | string;
  providerLabel: string;
  providerType: '' | 'danger' | 'info' | 'primary' | 'success' | 'warning';
  period: 'postFix' | 'preFix';
  calls: number;
  okCount: number;
  fallbackCount: number;
  errorCount: number;
  blockedCount: number;
  otherCount: number;
  fallbackRate: null | number;
};

const PROVIDER_ORDER: Record<string, number> = {
  deepseek: 0,
  ollama: 1,
  mock: 2,
};

function providerMeta(provider: null | string): {
  label: string;
  type: DistRow['providerType'];
} {
  if (provider === 'deepseek') return { label: '云端 DeepSeek', type: 'primary' };
  if (provider === 'ollama') return { label: '本地 Ollama', type: 'warning' };
  if (provider === 'mock') return { label: 'Mock 降级', type: 'info' };
  return { label: '未记录', type: 'info' };
}

const distRows = computed<DistRow[]>(() => {
  const rows = (data.value?.distribution ?? []).map((d) => {
    const meta = providerMeta(d.provider);
    const calls = num(d.calls);
    const fallbackCount = num(d.fallbackCount);
    return {
      scene: d.scene,
      provider: d.provider,
      providerLabel: meta.label,
      providerType: meta.type,
      period: d.period,
      calls,
      okCount: num(d.okCount),
      fallbackCount,
      errorCount: num(d.errorCount),
      blockedCount: num(d.blockedCount),
      otherCount: num(d.otherCount),
      fallbackRate: calls > 0 ? fallbackCount / calls : null,
    };
  });
  return rows.sort((a, b) => {
    if (a.scene !== b.scene) return a.scene < b.scene ? -1 : 1;
    if (a.period !== b.period) return a.period === 'postFix' ? -1 : 1;
    const pa = a.provider ? (PROVIDER_ORDER[a.provider] ?? 9) : 99;
    const pb = b.provider ? (PROVIDER_ORDER[b.provider] ?? 9) : 99;
    return pa - pb;
  });
});

function pct(rate: null | number): string {
  return rate == null ? '—' : `${(rate * 100).toFixed(1)}%`;
}

/* ── ③ 异常检查 ── */

type Anomaly = {
  /** 对应 el-alert 的 type（error=红线级） */
  level: 'error' | 'info' | 'warning';
  title: string;
  detail: string;
};

/** 「cloud-only 是否被本地接管」权威数字（后端按事故锚点拆历史/现行） */
const localCheck = computed(() => data.value?.cloudOnlyLocalCheck);
const anchorInfo = computed(() => data.value?.incidentAnchor);

const anomalies = computed<Anomaly[]>(() => {
  if (!data.value) return [];
  const list: Anomaly[] = [];
  const rows = distRows.value;

  // 按 scene 汇总（跨 provider、跨 period）
  const byScene = new Map<string, DistRow[]>();
  for (const r of rows) {
    const list2 = byScene.get(r.scene) ?? [];
    list2.push(r);
    byScene.set(r.scene, list2);
  }
  for (const [scene, sceneRows] of byScene) {
    const owners = sceneOwners.value.get(scene) ?? [];
    const fallbackCapable = owners.some((c) => c.fallbackAllowed);
    const cloudAttempts = sceneRows
      .filter((r) => r.provider === 'deepseek')
      .reduce((s, r) => s + r.calls, 0);
    const cloudFailures = sceneRows
      .filter((r) => r.provider === 'deepseek')
      .reduce((s, r) => s + r.fallbackCount + r.errorCount, 0);
    const localCalls = sceneRows
      .filter((r) => r.provider === 'ollama')
      .reduce((s, r) => s + r.calls, 0);
    const degradedNoCall = sceneRows
      .filter((r) => r.provider === null)
      .reduce((s, r) => s + r.fallbackCount, 0);

    // 云在场·云端失败（真故障信号）：deepseek 行的 FALLBACK/ERROR 是逐行精确事实
    if (cloudFailures > 0) {
      const rate = cloudAttempts > 0 ? cloudFailures / cloudAttempts : null;
      const severe =
        cloudAttempts >= CLOUD_FAIL_MIN_ATTEMPTS &&
        rate != null &&
        rate > CLOUD_FAIL_RATE_HINT;
      list.push({
        level: severe ? 'warning' : 'info',
        title: `云端失败 ${cloudFailures} 次：${scene}`,
        detail: `「${ownersLabel(scene)}」区间内云端调用 ${cloudAttempts} 次、失败 ${cloudFailures} 次（${pct(rate)}）。云在场但调用失败才转本地/降级——这是真故障信号，建议核对云端失败原因（超时/限额/鉴权），与「云未装配按策略直供」区分开。`,
      });
    }

    // 云缺席·本地直供（估算口径：本地服务 − 云端失败；演示环境预期状态）
    const directLocal = Math.max(0, localCalls - cloudFailures);
    if (fallbackCapable && directLocal > 0) {
      list.push({
        level: 'info',
        title: `云缺席·本地直供（估算 ${directLocal} 次）：${scene}`,
        detail: `「${ownersLabel(scene)}」本地服务共 ${localCalls} 次，扣除云端失败后转本地的部分，估算 ${directLocal} 次为云端未装配期间按策略直供（估算口径：本地服务 − 云端失败，逐行关联需 trace 级关联键，当前按聚合差值）。当前云未装配，这属演示环境预期状态；配上云 key 后应回落到接近 0。`,
      });
    }

    // 降级未发起真实调用（模板/缓存，或 provider 列加列前存量）：无本地顶替
    if (degradedNoCall > 0) {
      list.push({
        level: 'info',
        title: `降级未发起真实调用 ${degradedNoCall} 次：${scene}`,
        detail: `「${ownersLabel(scene)}」有 ${degradedNoCall} 次 provider 未记录的降级（返回模板/缓存而未发起真实调用，或 2026-11-08 provider 列加列前的存量），不涉及本地顶替。`,
      });
    }
  }
  return list;
});

/** error + warning 条目数（info 不计入「异常」判定） */
const severeCount = computed(
  () => anomalies.value.filter((a) => a.level !== 'info').length,
);

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
}
</script>

<template>
  <div class="zy-business-page routing-page flex flex-col gap-4">
    <BusinessPageHeader
      title="AI 模型路由"
      description="只读总览：每个能力当前走云端还是本地、时间窗内实际调用分布与路由异常自查。页面不提供任何配置写入——切换走启动参数，刻意如此。"
      :loading="loading"
      @refresh="load"
    >
      <el-button :loading="loading" @click="load">刷新</el-button>
    </BusinessPageHeader>

    <DataErrorBanner v-if="error && data" :error="error" :retrying="loading" @retry="load" />
    <el-card v-if="!data && !loading" shadow="never">
      <DataState :loading="false" :error="error" empty-text="暂无路由总览数据" @retry="load" />
    </el-card>
    <el-card v-if="!data && loading" shadow="never">
      <DataState :loading="true" />
    </el-card>

    <template v-if="data">
      <!-- ① 现在谁在干活：装配状态 + 路由配置呈现 -->
      <div class="metric-grid">
        <el-card shadow="never" class="metric-card">
          <span>云端模型</span>
          <strong :class="{ 'is-off': !registry?.cloudAvailable }">
            {{ registry?.cloudAvailable ? '已装配' : '未装配' }}
          </strong>
          <small>
            {{ registry?.cloudAvailable ? `provider：${registry.cloudProvider}` : '无真实云 key，可兜底能力按策略走本地/Mock' }}
          </small>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>本地 Ollama 兜底</span>
          <strong :class="{ 'is-off': !registry?.localAvailable }">
            {{ registry?.localAvailable ? '已装配' : '未装配' }}
          </strong>
          <small>模型：{{ registry?.localModel || '—' }}</small>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>Mock 降级</span>
          <strong :class="{ 'is-off': !registry?.mockAvailable }">
            {{ registry?.mockAvailable ? '已装配' : '未装配' }}
          </strong>
          <small>诚实降级，不冒充真实生成</small>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>影子判定本地演示</span>
          <strong :class="{ 'is-off': !registry?.shadowLocalEffective }">
            {{ registry?.shadowLocalEffective ? '生效' : '未生效' }}
          </strong>
          <small>
            双开关：local-demo-mode={{ registry?.localDemoMode ? '开' : '关' }}，
            两开关同时成立才允许本地
          </small>
        </el-card>
      </div>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>当前路由表（配置呈现）</span>
            <span class="card-hint">生效状态来自 ModelRouter / LlmClientRegistry · 切换走启动参数，本页不可改</span>
          </div>
        </template>
        <el-table :data="capabilities" stripe>
          <el-table-column label="能力" min-width="170">
            <template #default="{ row }">
              <div class="cap-cell">
                <span class="cap-name">{{ taskLabel(row.task) }}</span>
                <span class="cap-task">{{ row.task }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="档位" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.tier === 'strong'" type="warning" effect="plain">强档</el-tag>
              <el-tag v-else-if="row.tier === 'none'" type="danger" effect="plain">不走 LLM</el-tag>
              <el-tag v-else effect="plain">标准档</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="云模型" min-width="150">
            <template #default="{ row }">{{ row.cloudModel || '—' }}</template>
          </el-table-column>
          <el-table-column label="本地兜底" min-width="190">
            <template #default="{ row }">
              <el-tag v-if="row.tier === 'none'" type="danger" effect="dark">红线：不走 LLM</el-tag>
              <el-tag v-else-if="row.cloudOnly" type="danger" effect="plain">cloud-only，本地永不接管</el-tag>
              <el-tag v-else-if="row.task === 'moderation_shadow'" :type="row.fallbackAllowed ? 'warning' : 'info'" effect="plain">
                演示双开关{{ row.fallbackAllowed ? '生效' : '未生效' }}
              </el-tag>
              <el-tag v-else-if="row.fallbackAllowed" type="success" effect="plain">允许本地兜底</el-tag>
              <el-tag v-else type="info" effect="plain">本地兜底已关停</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="记账场景（scene）" min-width="220">
            <template #default="{ row }">
              <template v-if="row.sceneKeys.length">
                <el-tag v-for="s in row.sceneKeys" :key="s" class="scene-tag" effect="plain" type="info">{{ s }}</el-tag>
              </template>
              <span v-else class="scene-none">
                {{ row.task === 'crisis' ? '红线，无调用' : '不落 ai_trace（走影子台账/审计）' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- ② 最近实际走了谁 -->
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>实际调用分布（按 scene × provider）</span>
            <span class="card-hint">生成于 {{ formatTime(data.generatedAt) }} · trace 粒度纯计数，不含内容</span>
          </div>
        </template>

        <div class="range-bar">
          <label class="range-bar__label" for="routing-range">统计区间</label>
          <el-date-picker
            id="routing-range"
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            style="width: 260px"
            @change="load"
          />
          <span class="range-bar__hint">
            默认近 7 天，最长 90 天 · 窗口 {{ formatTime(data.window.from) }} ~ {{ formatTime(data.window.to) }}
          </span>
        </div>

        <DataState
          v-if="!distRows.length"
          :loading="false"
          empty-text="区间内无 AI 调用记录"
          empty-hint="换个时间区间试试；若长期无记录，请确认业务侧确实发起了 AI 调用。"
        />
        <el-table v-else :data="distRows" stripe>
          <el-table-column prop="scene" label="scene" min-width="150" />
          <el-table-column label="归属能力" min-width="160">
            <template #default="{ row }">{{ ownersLabel(row.scene) }}</template>
          </el-table-column>
          <el-table-column label="provider" width="130">
            <template #default="{ row }">
              <el-tag :type="row.providerType" effect="plain">{{ row.providerLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时段" width="96">
            <template #default="{ row }">
              <el-tag v-if="row.period === 'postFix'" type="success" effect="plain">修复后</el-tag>
              <el-tag v-else type="info" effect="plain">修复前</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="calls" label="调用" width="90" align="right" />
          <el-table-column prop="okCount" label="成功" width="90" align="right" />
          <el-table-column prop="fallbackCount" label="兜底/降级" width="110" align="right" />
          <el-table-column prop="errorCount" label="错误" width="90" align="right" />
          <el-table-column prop="blockedCount" label="拦截" width="90" align="right" />
          <el-table-column label="兜底率" width="100" align="right">
            <template #default="{ row }">{{ pct(row.fallbackRate) }}</template>
          </el-table-column>
        </el-table>
        <p v-if="distRows.length" class="dist-note">
          「未记录」= 编排层 trace（不记 provider）、语音或 provider 列（2026-11-08）加列前的存量数据；
          llm-message 由寄语/卡点引导/倾听小结回落共用，计数不再向能力拆分；
          「修复前/后」按事故锚点分桶（见下方异常自查的锚点说明）。
        </p>
      </el-card>

      <!-- ③ 异常检查 -->
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>路由异常自查</span>
            <span class="card-hint">cloud-only 判定看权威数字（按事故锚点拆历史/现行）· 云端失败率提示线 >20%（云端调用 ≥5 次）</span>
          </div>
        </template>

        <!-- 事故锚点：声明的运维事实（非自动计算），默认服务端配置，可临时调整 -->
        <div class="anchor-bar">
          <span class="anchor-bar__text">
            事故锚点：<strong>{{ formatTime(anchorInfo?.at) }}</strong>
            <el-tag size="small" effect="plain" :type="anchorInfo?.source === 'override' ? 'warning' : 'info'">
              {{ anchorInfo?.source === 'override' ? '临时覆盖' : '服务端配置' }}
            </el-tag>
          </span>
          <label class="anchor-bar__label" for="anchor-override">临时调整</label>
          <el-date-picker
            id="anchor-override"
            v-model="anchorDraft"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择锚点时间"
            style="width: 220px"
          />
          <el-button size="small" :disabled="!anchorDraft" @click="applyAnchor">应用</el-button>
          <el-button size="small" :disabled="anchorInfo?.source !== 'override'" @click="resetAnchor">
            恢复默认
          </el-button>
        </div>
        <p class="anchor-note">
          锚点默认值 = 修复提交 ffbcd7c（2026-11-07 22:12:48）：此前 LlmClientRegistry 按父类型注入把本地 Ollama 误认云端，
          cloud-only 能力曾被本地顶替；修复后云槽精确排除子类。锚点前的记录为已修复事故的证据（只追加保留），不计入现行红线。
        </p>

        <DataState v-if="!distRows.length" :loading="false" empty-text="无分布数据，无法评估" />
        <template v-else-if="localCheck">
          <!-- 权威数字：修复后窗口内 cloud-only 本地记录 -->
          <el-alert
            v-if="localCheck.verdict === 'CLEAN'"
            title="修复后窗口内 cloud-only 本地记录：0 次"
            type="success"
            :closable="false"
            show-icon
            :description="`锚点（${formatTime(localCheck.anchor)}）之后，cloud-only 独占 scene（${localCheck.exclusiveScenes.join('、')}）无任何 ollama 记录——cloud-only 能力未被本地接管。`"
            class="anomaly-item"
          />
          <el-alert
            v-else
            :title="`现行违规：修复后窗口内 cloud-only 本地记录 ${num(localCheck.postFixLocalCalls)} 次`"
            type="error"
            :closable="false"
            show-icon
            :description="`锚点（${formatTime(localCheck.anchor)}）之后，cloud-only 独占 scene（${localCheck.exclusiveScenes.join('、')}）出现 ${num(localCheck.postFixLocalCalls)} 次 ollama 记录。此数应为 0——请立即核查路由配置或兜底代码。`"
            class="anomaly-item"
          />
          <el-alert
            v-if="num(localCheck.preFixLocalCalls) > 0"
            :title="`历史事故记录：锚点前 ${num(localCheck.preFixLocalCalls)} 次（不计入现行红线）`"
            type="info"
            :closable="false"
            show-icon
            description="已修复的注入歧义 bug 的证据，按只追加约定保留；单独标注，不混入上方现行判定。"
            class="anomaly-item"
          />
          <el-alert
            v-if="!severeCount"
            title="无云端故障告警"
            :closable="false"
            show-icon
            type="success"
            description="无「云端调用 ≥5 次且失败率 >20%」的 scene；下方 info 项为小样本云端失败、云缺席直供与存量降级的如实说明。"
            class="anomaly-item"
          />
          <el-alert
            v-for="(a, i) in anomalies"
            :key="i"
            :title="a.title"
            :type="a.level"
            :closable="false"
            show-icon
            :description="a.detail"
            class="anomaly-item"
          />
        </template>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>口径说明</span>
          </div>
        </template>
        <p class="note-text">{{ data.note }}</p>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.routing-page {
  max-width: 1280px;
  margin: 0 auto;
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
  font-size: 24px;
  color: var(--el-text-color-primary);
}
.metric-card strong.is-off {
  color: var(--el-text-color-secondary);
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
.cap-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cap-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.cap-task {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.scene-tag {
  margin-right: 6px;
}
.scene-none {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.range-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.range-bar__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.range-bar__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.anchor-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.anchor-bar__text {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.anchor-bar__text strong {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.anchor-bar__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.anchor-note {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
.dist-note {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
.anomaly-item {
  margin-bottom: 10px;
}
.anomaly-item:last-child {
  margin-bottom: 0;
}
.anomaly-item :deep(.el-alert__description) {
  line-height: 1.7;
}
.note-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}
@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
