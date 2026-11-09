<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage } from 'element-plus';

import {
  type AdminSoftTierApi,
  getSoftTierAppeals,
  getSoftTierPoolDetail,
  getSoftTierPoolList,
  postSoftTierVerdict,
} from '#/api/zhiyu/admin-soft-tier';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import {
  COMMENT_STATUS,
  SOFT_TIER_APPEAL_STATUS,
  SOFT_TIER_POOL_STATUS,
  SOFT_TIER_SAMPLE_LAYER,
  SOFT_TIER_TARGET_TYPE,
  WORK_STATUS,
  dictLabel,
} from '#/constants/status-dict';

/**
 * 软档复核（2026-11-07 处置链）：抽检队列 + 申诉处理 + 看板小结。
 *
 * 文案纪律：禁用「违规/处罚/降权」，基调是「保护」；裁决动作官方中文名
 * CLEAR=恢复展示 / UPHOLD=维持至窗口结束 / BLOCK=拦截下架（沿用设计文档 §3）。
 *
 * 契约现状（2026-11-07 联调对齐，F2/F3）：
 * - 申诉有独立列表端点 /admin/soft-tier-appeals（含理由与作者昵称），「申诉处理」页签直读；
 * - 复核详情直接下发 authorNickname 与 appeal_reason，作者栏不再依赖二次解析；
 * - 「当日入池量」聚合端点 /admin/soft-tier-pool/stats 已存在，看板收敛留作后续优化；
 * - 详情无 rid/tid/aid/alid 别名，一律用 r.* 原键。
 */

/** 处置链开关未开启（1001「软档处置链未开启」）——配置态而非故障态 */
const chainOff = ref(false);

/**
 * 1001 有两种错误形态（F4）：
 * HTTP 200+业务码 → defaultResponseInterceptor 抛出的克隆响应（e.data.message）；
 * HTTP 400 → AxiosError（e.response.data.message，e.message 是英文状态文案，不可用）。
 */
function isChainOffError(e: any) {
  const texts = [e?.data?.message, e?.response?.data?.message, e?.message];
  return texts.some(
    (t) => typeof t === 'string' && t.includes('处置链未开启'),
  );
}

/** 页签：抽检队列（全状态可筛）/ 申诉处理（独立申诉端点，含理由与作者昵称） */
const activeTab = ref<'appeals' | 'queue'>('queue');

/** 列表行：队列入参 PoolRecord 与申诉入参 AppealRecord 字段名一致（申诉行多出昵称/理由/申诉时间） */
type PoolRow = AdminSoftTierApi.AppealRecord & AdminSoftTierApi.PoolRecord;

const statusOptions: Array<{
  label: string;
  value: AdminSoftTierApi.PoolStatusFilter;
}> = [
  { value: 'OPEN', label: '待处理（待复核 + 已升级）' },
  { value: 'PENDING', label: '待复核' },
  { value: 'ESCALATED', label: '已升级待审' },
  { value: 'NOT_SAMPLED', label: '未抽中（窗口期）' },
  { value: 'CLEAR', label: '已恢复展示' },
  { value: 'UPHOLD', label: '维持展示受限' },
  { value: 'BLOCK', label: '已拦截' },
];
const filterStatus = ref<AdminSoftTierApi.PoolStatusFilter>('OPEN');

const {
  tableData,
  loading,
  errorMsg,
  total,
  currentPage,
  pageSize,
  fetchList,
  handleFilterChange,
  handlePageChange,
} = useListPage<PoolRow>({
  pageSize: 20, // 服务端固定 20，无 pageSize 参数
  // 由 onMounted 先拉列表、再决定是否拉小结，避免 6 条并发 1001 各弹一次全局提示
  immediate: false,
  fetcher: async ({ page }) => {
    try {
      const res =
        activeTab.value === 'appeals'
          ? await getSoftTierAppeals({ page })
          : await getSoftTierPoolList({ page, status: filterStatus.value });
      chainOff.value = false;
      return res;
    } catch (e: any) {
      if (isChainOffError(e)) {
        chainOff.value = true;
        return { records: [], total: 0 };
      }
      throw e;
    }
  },
});

function handleTabChange() {
  handleFilterChange();
}

function resetFilters() {
  filterStatus.value = 'OPEN';
  handleFilterChange();
}

const emptyText = computed(() => {
  if (chainOff.value) return '软档处置链未开启';
  return activeTab.value === 'appeals' ? '暂无待处理申诉' : '复核队列已清空';
});
const emptyHint = computed(() => {
  if (chainOff.value) {
    return '后端开关 zhiyu.moderation.soft-tier.disposition.enabled 默认关闭；开启后复核队列在此可用，点右上角「刷新」重新检查。';
  }
  return activeTab.value === 'appeals'
    ? '作者对「展示受限」处置提交申诉后，申诉单会按提交时间倒序列在这里（含申诉理由与作者昵称）。'
    : '审核员「通过并标记软档」后按抽样分层入池；作者申诉与超时升级也会进入队列。';
});

/* ── 看板小结（无聚合端点：各状态 total 分查，口径如实标注） ── */

const summary = ref<Record<string, number>>({});
const summaryLoading = ref(false);
const summaryError = ref('');
const SUMMARY_STATUSES = [
  'OPEN',
  'ESCALATED',
  'CLEAR',
  'UPHOLD',
  'BLOCK',
  'NOT_SAMPLED',
] as const;

async function loadSummary() {
  // 处置链未开启属配置态：不再发 6 条注定 1001 的请求
  if (chainOff.value) return;
  summaryLoading.value = true;
  summaryError.value = '';
  try {
    const results = await Promise.all(
      SUMMARY_STATUSES.map((status) => getSoftTierPoolList({ page: 1, status })),
    );
    const map: Record<string, number> = {};
    SUMMARY_STATUSES.forEach((status, i) => {
      map[status] = Number(results[i]?.total ?? 0);
    });
    summary.value = map;
    chainOff.value = false;
  } catch (e: any) {
    if (isChainOffError(e)) {
      chainOff.value = true;
      summary.value = {};
    } else {
      summaryError.value =
        // 中文业务文案原样上屏；纯 ASCII 底层错误归一为网络异常
        e?.message && !/^[\x20-\x7E]*$/.test(e.message)
          ? e.message
          : '小结加载失败，请稍后重试';
    }
  } finally {
    summaryLoading.value = false;
  }
}

async function refreshPage() {
  await fetchList();
  // 列表已失败（网络/权限）时不再叠加小结请求，提示只保留一条
  if (!chainOff.value && !errorMsg.value) loadSummary();
}

/* ── 时间与超时 ── */

function formatTime(dt?: null | string) {
  if (!dt) return '-';
  return String(dt).replace('T', ' ').slice(0, 16);
}

function parseTime(dt?: null | string): null | number {
  if (!dt) return null;
  const t = new Date(String(dt).replace(' ', 'T')).getTime();
  return Number.isNaN(t) ? null : t;
}

function formatDuration(ms: number): string {
  const minutes = Math.max(1, Math.round(ms / 60000));
  if (minutes >= 1440) {
    return `${Math.floor(minutes / 1440)} 天 ${Math.floor((minutes % 1440) / 60)} 小时`;
  }
  if (minutes >= 60) return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`;
  return `${minutes} 分`;
}

/** SLA 展示口径：仅未终裁（PENDING/ESCALATED）计算超时；终裁后时限失去意义 */
function slaInfo(row: {
  poolStatus?: string;
  slaDueAt?: null | string;
}): { overdue: boolean; text: string } | null {
  if (row.poolStatus !== 'PENDING' && row.poolStatus !== 'ESCALATED') return null;
  const due = parseTime(row.slaDueAt);
  if (due === null) return null;
  const diff = due - Date.now();
  return diff < 0
    ? { overdue: true, text: `已超时 ${formatDuration(-diff)}` }
    : { overdue: false, text: `剩余 ${formatDuration(diff)}` };
}

const isActionable = (status?: string) =>
  status === 'PENDING' || status === 'ESCALATED';

/** ESCALATED 行（申诉/超时升级）整行警示底色，与服务端置顶呼应 */
function rowClassName({ row }: { row: PoolRow }) {
  return row.poolStatus === 'ESCALATED' ? 'soft-row-escalated' : '';
}

/* ── 详情抽屉 ── */

const drawer = ref(false);
const detail = ref<AdminSoftTierApi.PoolDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref('');
const detailId = ref('');
let detailRequest = 0;

async function openDetail(id: string) {
  const requestId = ++detailRequest;
  detailId.value = id;
  drawer.value = true;
  detail.value = null;
  detailError.value = '';
  detailLoading.value = true;
  try {
    const result = await getSoftTierPoolDetail(id);
    if (requestId !== detailRequest) return;
    detail.value = result;
  } catch (e: any) {
    if (requestId !== detailRequest) return;
    detailError.value = e?.message || '详情加载失败';
  } finally {
    if (requestId === detailRequest) detailLoading.value = false;
  }
}

function resetDetail() {
  detailRequest++;
  detail.value = null;
  detailId.value = '';
  detailError.value = '';
  detailLoading.value = false;
}

/** 详情侧 SLA（snake_case 字段口径） */
const detailSla = computed(() => {
  const d = detail.value;
  if (!d) return null;
  return slaInfo({ poolStatus: d.pool_status, slaDueAt: d.sla_due_at });
});

/* ── 裁决 ── */

const verdictVisible = ref(false);
const verdictAction = ref<AdminSoftTierApi.VerdictAction>('CLEAR');
const verdictReason = ref('');
const verdictError = ref('');
const verdictBusy = ref(false);

const VERDICT_COPY: Record<
  AdminSoftTierApi.VerdictAction,
  {
    confirmText: string;
    hint: string;
    placeholder: string;
    required: boolean;
    successText: string;
    title: string;
  }
> = {
  CLEAR: {
    title: '恢复展示',
    confirmText: '确认恢复展示',
    hint: '确认本条软档为误判：立即恢复内容在社区流的正常展示，并通知作者「展示受限已解除」。理由可空（≤255 字），随裁决留痕。',
    placeholder: '如：情绪自述内容，无需要拦截的关注点',
    required: false,
    successText: '已恢复展示',
  },
  UPHOLD: {
    title: '维持展示受限至窗口结束',
    confirmText: '确认维持',
    hint: '维持当前展示受限，至 72 小时窗口结束自动恢复；不额外通知作者（首条通知已说明时限）。理由可空（≤255 字）。',
    placeholder: '如：内容适宜公开但窗口期内保持受限展示',
    required: false,
    successText: '已维持展示受限，窗口结束自动恢复',
  },
  BLOCK: {
    title: '拦截下架',
    confirmText: '确认拦截下架',
    hint: '内容转为下架，走既有审核状态机；理由必填（≤255 字），落库并随审核结果通知作者。',
    placeholder: '如：包含联系方式 / 超出社区内容边界',
    required: true,
    successText: '已拦截并下架',
  },
};

const verdictCopy = computed(() => VERDICT_COPY[verdictAction.value]);

function openVerdict(action: AdminSoftTierApi.VerdictAction) {
  if (verdictBusy.value) return;
  verdictAction.value = action;
  verdictReason.value = '';
  verdictError.value = '';
  verdictVisible.value = true;
}

async function confirmVerdict() {
  const id = detailId.value;
  if (!id || verdictBusy.value) return;
  const reason = verdictReason.value.trim();
  if (verdictCopy.value.required && !reason) {
    verdictError.value = '拦截下架必须填写理由';
    return;
  }
  verdictBusy.value = true;
  try {
    await postSoftTierVerdict(id, {
      action: verdictAction.value,
      ...(reason ? { reason } : {}),
    });
    ElMessage.success(verdictCopy.value.successText);
    verdictVisible.value = false;
    await openDetail(id);
    await fetchList();
    if (!chainOff.value && !errorMsg.value) loadSummary();
  } catch {
    // 拦截器已提示；3001「已终裁」等并发冲突时刷新列表与详情对齐真实状态
    await fetchList();
    openDetail(id);
  } finally {
    verdictBusy.value = false;
  }
}

/** 通知中心跳转落点：?reviewId= 直达复核详情抽屉（id 为字符串雪花，原样传递） */
const route = useRoute();
onMounted(async () => {
  await fetchList();
  if (!chainOff.value && !errorMsg.value) loadSummary();
  const reviewId = route.query.reviewId;
  if (typeof reviewId === 'string' && reviewId) {
    openDetail(reviewId);
  }
});
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="软档复核"
    description="软档内容的抽检复核与申诉处理。裁决只决定内容的展示方式，基调是保护而非惩戒。"
    eyebrow="SOFT-TIER REVIEW"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    :empty-text="emptyText"
    :empty-hint="emptyHint"
    :show-sizes="false"
    :row-class-name="rowClassName"
    @refresh="refreshPage"
    @page-change="handlePageChange"
  >
    <template #banner>
      <!-- 处置链未开启：配置态说明（非故障），刷新按钮即重试入口 -->
      <el-alert
        v-if="chainOff"
        type="info"
        :closable="false"
        show-icon
        title="软档处置链当前未开启"
      >
        <template #default>
          后端开关 zhiyu.moderation.soft-tier.disposition.enabled 默认关闭。
          学生端「展示受限」徽标、申诉页与通知类型先行发布后才会开启；开启前本页无复核数据。
        </template>
      </el-alert>

      <!-- 看板小结：各状态 total 分查；聚合端点 /stats 已存在，收敛留作后续优化 -->
      <section class="pool-summary" aria-label="看板小结">
        <div class="pool-summary__head">
          <h2 class="pool-summary__title">看板小结</h2>
          <p class="pool-summary__note">实时口径：对应状态的复核单总数</p>
        </div>
        <DataErrorBanner
          v-if="summaryError"
          :error="summaryError"
          :retrying="summaryLoading"
          @retry="loadSummary"
        />
        <el-skeleton v-else-if="summaryLoading && !Object.keys(summary).length" :rows="1" animated />
        <div v-else class="pool-summary__stats">
          <div class="stat">
            <span class="stat__label">待复核</span>
            <span class="stat__value stat__value--warn">{{ summary.OPEN ?? '—' }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">已升级待审 *</span>
            <span
              class="stat__value"
              :class="{ 'stat__value--danger': (summary.ESCALATED ?? 0) > 0 }"
            >
              {{ summary.ESCALATED ?? '—' }}
            </span>
          </div>
          <div class="stat">
            <span class="stat__label">当日入池 *</span>
            <span class="stat__value stat__value--muted">—</span>
          </div>
          <div class="stat">
            <span class="stat__label">已恢复展示</span>
            <span class="stat__value stat__value--ok">{{ summary.CLEAR ?? '—' }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">维持展示受限</span>
            <span class="stat__value">{{ summary.UPHOLD ?? '—' }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">已拦截</span>
            <span class="stat__value">{{ summary.BLOCK ?? '—' }}</span>
          </div>
          <div class="stat">
            <span class="stat__label">未抽中（窗口期）</span>
            <span class="stat__value stat__value--muted">{{ summary.NOT_SAMPLED ?? '—' }}</span>
          </div>
        </div>
        <p class="pool-summary__footnote">
          * 「已升级待审」含作者申诉升级与复核超时升级两种来源，此处为合并计数；
          「当日入池量」可由聚合端点 /stats 提供，看板收敛留作后续优化（不以估算冒充）。
        </p>
      </section>

      <!-- 页签：抽检队列 / 申诉处理（独立申诉端点，含理由与作者昵称） -->
      <el-tabs
        v-model="activeTab"
        class="pool-tabs"
        @tab-change="handleTabChange"
      >
        <el-tab-pane label="抽检队列" name="queue" />
        <el-tab-pane label="申诉处理" name="appeals" />
      </el-tabs>
      <el-alert
        v-if="activeTab === 'appeals'"
        class="appeals-note"
        type="info"
        :closable="false"
        show-icon
        title="本页为作者提交的申诉单（按申诉时间倒序），申诉即升级为人工必审"
      >
        <template #default>
          列表含申诉理由与作者昵称；裁决入口在「复核」详情中。
        </template>
      </el-alert>
    </template>

    <template #filters>
      <template v-if="activeTab === 'queue'">
        <label for="soft-tier-status" class="text-sm">状态</label>
        <el-select
          id="soft-tier-status"
          v-model="filterStatus"
          style="width: 220px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-button @click="resetFilters">重置</el-button>
      </template>
      <p v-else class="m-0 text-sm appeals-filter-note">
        申诉单按提交时间倒序；申诉即升级为人工必审（已升级队列置顶）。
      </p>
    </template>

    <el-table-column :label="activeTab === 'appeals' ? '申诉时间' : '入池时间'" width="150">
      <template #default="{ row }">
        {{ formatTime(activeTab === 'appeals' ? row.appealFiledAt || row.createTime : row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="对象" width="80">
      <template #default="{ row }">
        <ZyStatusTag :dict="SOFT_TIER_TARGET_TYPE" :value="row.targetType" />
      </template>
    </el-table-column>
    <el-table-column v-if="activeTab === 'appeals'" label="作者" width="120">
      <template #default="{ row }">
        {{ row.authorNickname || '已注销用户' }}
      </template>
    </el-table-column>
    <el-table-column v-if="activeTab === 'appeals'" label="申诉理由" min-width="220" show-overflow-tooltip>
      <template #default="{ row }">
        {{ row.appealReason || '—' }}
      </template>
    </el-table-column>
    <el-table-column label="分层来源" width="150">
      <template #default="{ row }">
        <ZyStatusTag :dict="SOFT_TIER_SAMPLE_LAYER" :value="row.sampleLayer" />
      </template>
    </el-table-column>
    <el-table-column label="状态" width="140">
      <template #default="{ row }">
        <ZyStatusTag :dict="SOFT_TIER_POOL_STATUS" :value="row.poolStatus" />
      </template>
    </el-table-column>
    <el-table-column label="复核时限" width="170">
      <template #default="{ row }">
        <span
          v-if="slaInfo(row)"
          class="sla-text"
          :class="{ 'sla-text--overdue': slaInfo(row)?.overdue }"
        >
          {{ slaInfo(row)?.text }}
        </span>
        <span v-else class="sla-text sla-text--none">—</span>
      </template>
    </el-table-column>
    <el-table-column v-if="activeTab === 'queue'" label="申诉" width="90">
      <template #default="{ row }">
        <el-tag v-if="row.appealStatus === 'FILED'" type="danger" size="small" effect="dark">
          已申诉
        </el-tag>
        <el-tag v-else-if="row.appealStatus === 'RESOLVED'" type="success" size="small">
          已处理
        </el-tag>
        <span v-else class="sla-text--none">—</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="openDetail(row.id)">
          {{ isActionable(row.poolStatus) ? '复核' : '查看' }}
        </el-button>
      </template>
    </el-table-column>

    <template #append>
      <p class="business-note">
        复核只决定内容的展示方式，不构成对作者的评价；本系统不能替代专业心理咨询。
      </p>

      <!-- 复核详情抽屉 -->
      <el-drawer
        v-model="drawer"
        title="软档复核详情"
        size="min(560px, 100vw)"
        @closed="resetDetail"
      >
        <el-skeleton v-if="detailLoading" :rows="5" animated />
        <el-alert v-else-if="detailError" :title="detailError" type="error" :closable="false">
          <template #default>
            <el-button size="small" @click="openDetail(detailId)">重试</el-button>
          </template>
        </el-alert>
        <template v-else-if="detail">
          <div class="detail-tags">
            <ZyStatusTag :dict="SOFT_TIER_POOL_STATUS" :value="detail.pool_status" />
            <ZyStatusTag :dict="SOFT_TIER_TARGET_TYPE" :value="detail.target_type" />
            <ZyStatusTag :dict="SOFT_TIER_SAMPLE_LAYER" :value="detail.sample_layer" />
            <el-tag
              v-if="detail.appeal_status === 'FILED'"
              type="danger"
              size="small"
              effect="dark"
            >
              已申诉
            </el-tag>
          </div>

          <section class="detail-section">
            <h3>内容原文</h3>
            <template v-if="detail.target_type === 'work'">
              <template v-if="detail.work">
                <p class="content-title">{{ detail.work.title || '未命名作品' }}</p>
                <p v-if="detail.work.message" class="content-message">
                  <span class="content-label">AI 寄语</span>{{ detail.work.message }}
                </p>
                <p class="detail-note">
                  内容状态：{{ dictLabel(WORK_STATUS, detail.work.status) }}。
                  作品媒体内容不在本接口返回范围（接口边界），
                  <router-link
                    :to="{ path: '/zhiyu/business/works', query: { workId: detail.target_id } }"
                    class="detail-link"
                  >去作品管理核对 →</router-link>
                </p>
              </template>
              <p v-else class="detail-note">作品内容已不可用（可能已被删除）。</p>
            </template>
            <template v-else>
              <template v-if="detail.comment">
                <p class="content-main">{{ detail.comment.content }}</p>
                <p class="detail-note">
                  内容状态：{{ dictLabel(COMMENT_STATUS, detail.comment.status) }}。
                  <router-link
                    v-if="detail.comment.workId"
                    :to="{ path: '/zhiyu/business/works', query: { workId: detail.comment.workId } }"
                    class="detail-link"
                  >查看所属作品 →</router-link>
                </p>
              </template>
              <p v-else class="detail-note">评论内容已不可用（可能已被删除）。</p>
            </template>
          </section>

          <section class="detail-section">
            <h3>软档依据</h3>
            <dl class="detail-list">
              <div class="detail-row">
                <dt>AI 建议档位</dt>
                <dd>{{ detail.ai_tendency || '—' }}（审核员据此选择「通过并标记软档」）</dd>
              </div>
              <div class="detail-row">
                <dt>窄门留证 narrative</dt>
                <dd>{{ detail.narrative || '—' }}</dd>
              </div>
              <div class="detail-row">
                <dt>抽样分层</dt>
                <dd>{{ dictLabel(SOFT_TIER_SAMPLE_LAYER, detail.sample_layer) }}</dd>
              </div>
              <div class="detail-row">
                <dt>AI 审计关联</dt>
                <dd>{{ detail.audit_log_id ? `记录 ${detail.audit_log_id}` : '—' }}</dd>
              </div>
            </dl>
          </section>

          <section v-if="detail.appeal_status && detail.appeal_status !== 'NONE'" class="detail-section">
            <h3>申诉信息</h3>
            <template v-if="detail.appeal_status === 'FILED'">
              <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="作者已发起申诉，本单已升级为人工必审"
              />
              <dl class="detail-list">
                <div class="detail-row">
                  <dt>申诉理由</dt>
                  <dd>{{ detail.appeal_reason || '（作者未填写理由）' }}</dd>
                </div>
              </dl>
            </template>
            <p v-else class="detail-note">申诉已随裁决处理（{{
              dictLabel(SOFT_TIER_APPEAL_STATUS, detail.appeal_status)
            }}）。</p>
          </section>

          <section class="detail-section">
            <h3>时间与窗口</h3>
            <dl class="detail-list">
              <div class="detail-row">
                <dt>作者</dt>
                <dd>{{ detail.authorNickname || '已注销用户' }}（ID {{ detail.author_id || '—' }}）</dd>
              </div>
              <div class="detail-row">
                <dt>入池时间</dt>
                <dd>{{ formatTime(detail.create_time) }}</dd>
              </div>
              <div class="detail-row">
                <dt>复核时限</dt>
                <dd>
                  {{ formatTime(detail.sla_due_at) }}
                  <span
                    v-if="detailSla"
                    class="sla-text"
                    :class="{ 'sla-text--overdue': detailSla.overdue }"
                  >（{{ detailSla.text }}）</span>
                </dd>
              </div>
              <div class="detail-row">
                <dt>展示受限截止</dt>
                <dd>{{ formatTime(detail.demote_until) }}</dd>
              </div>
              <div v-if="detail.reviewed_at" class="detail-row">
                <dt>裁决</dt>
                <dd>
                  {{ formatTime(detail.reviewed_at) }} · 复核人 ID {{ detail.reviewer_id || '—' }}
                  <template v-if="detail.reason"> · 理由：{{ detail.reason }}</template>
                </dd>
              </div>
            </dl>
          </section>

          <section v-if="isActionable(detail.pool_status)" class="detail-section">
            <h3>裁决</h3>
            <div class="verdict-list">
              <div class="verdict-item">
                <el-button type="success" :disabled="verdictBusy" @click="openVerdict('CLEAR')">
                  恢复展示
                </el-button>
                <span class="verdict-hint">确认误判：立即恢复正常展示，通知作者「展示受限已解除」。</span>
              </div>
              <div class="verdict-item">
                <el-button :disabled="verdictBusy" @click="openVerdict('UPHOLD')">
                  维持至窗口结束
                </el-button>
                <span class="verdict-hint">维持展示受限，72 小时窗口结束自动恢复；不额外通知作者。</span>
              </div>
              <div class="verdict-item">
                <el-button type="danger" plain :disabled="verdictBusy" @click="openVerdict('BLOCK')">
                  拦截下架
                </el-button>
                <span class="verdict-hint">内容转为下架（走既有审核状态机），理由必填并随审核结果通知作者。</span>
              </div>
            </div>
          </section>

          <p class="detail-footnote">
            复核只决定内容的展示方式，不构成对作者的评价；本系统不能替代专业心理咨询。
          </p>
        </template>
      </el-drawer>

      <!-- 裁决确认弹窗（BLOCK 理由必填，复用既有驳回约束） -->
      <el-dialog
        v-model="verdictVisible"
        :title="verdictCopy.title"
        width="min(480px, 94vw)"
        :close-on-click-modal="false"
      >
        <p class="verdict-dialog-hint">{{ verdictCopy.hint }}</p>
        <el-input
          v-model="verdictReason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          :placeholder="verdictCopy.placeholder"
          @keyup.ctrl.enter="confirmVerdict"
        />
        <p v-if="verdictError" class="verdict-dialog-error">{{ verdictError }}</p>
        <template #footer>
          <el-button @click="verdictVisible = false">取消</el-button>
          <el-button
            :type="verdictAction === 'CLEAR' ? 'success' : verdictAction === 'BLOCK' ? 'danger' : 'primary'"
            :loading="verdictBusy"
            @click="confirmVerdict"
          >
            {{ verdictCopy.confirmText }}
          </el-button>
        </template>
      </el-dialog>
    </template>
  </ZyListPage>
</template>

<style scoped>
.business-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

/* ── 看板小结（复用 flow-metrics 的 stat 视觉语言，el 设计令牌） ── */
.pool-summary {
  padding: 16px 20px;
  margin-bottom: 4px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.pool-summary__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.pool-summary__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.pool-summary__note {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pool-summary__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 18px 32px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 88px;
}

.stat__label {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.stat__value {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.2;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.stat__value--warn {
  color: var(--el-color-warning-dark-2);
}

.stat__value--danger {
  color: var(--el-color-danger);
}

.stat__value--ok {
  color: var(--el-color-success-dark-2);
}

.stat__value--muted {
  color: var(--el-text-color-secondary);
}

.pool-summary__footnote {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

/* ── 页签与申诉说明 ── */
.pool-tabs {
  margin-bottom: 4px;
}

.pool-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.appeals-note {
  margin-bottom: 4px;
}

.appeals-filter-note {
  color: var(--el-text-color-regular);
}

/* ── 时限与行高亮 ── */
.sla-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.sla-text--overdue {
  font-weight: 600;
  color: var(--el-color-danger);
}

.sla-text--none {
  color: var(--el-text-color-secondary);
}

:deep(.soft-row-escalated) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9);
}

/* ── 详情抽屉 ── */
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.detail-section {
  padding: 14px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.content-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.7;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.content-main {
  margin: 0 0 8px;
  font-size: 15px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
}

.content-message {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.content-label {
  display: inline-block;
  padding: 1px 8px;
  margin-right: 8px;
  font-size: 12px;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
}

.detail-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
  line-height: 1.7;
}

.detail-row dt {
  flex-shrink: 0;
  min-width: 120px;
  color: var(--el-text-color-secondary);
}

.detail-row dd {
  margin: 0;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.detail-note {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.detail-link {
  color: var(--el-color-primary-dark-2);
  text-decoration: none;
}

.detail-link:hover {
  text-decoration: underline;
}

.detail-footnote {
  margin: 16px 0 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

/* ── 裁决 ── */
.verdict-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.verdict-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.verdict-item .el-button {
  flex-shrink: 0;
  min-width: 132px;
}

.verdict-hint {
  padding-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.verdict-dialog-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.verdict-dialog-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}

@media (max-width: 640px) {
  .verdict-item {
    flex-direction: column;
    gap: 4px;
  }

  .verdict-item .el-button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .detail-link {
    transition: none;
  }
}
</style>
