<script setup lang="ts">
import { computed, ref } from 'vue';

import { ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import { getCrisisLogDetail, getCrisisLogs } from '#/api/zhiyu/crisis-log';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { CARE_RISK } from '#/constants/status-dict';

/**
 * 危机日志（AN 2026-11-04）：危机路径「留存但降级」的授权查看页。
 *
 * 安全口径（与后端 AJ 契约一致，不越界）：
 * - 列表只展示元数据（时间/风险等级/场景/用户标识），不预取摘要；
 * - 摘要点「查看详情」并经「本次查看将记入操作审计」确认后才请求——
 *   服务端每次查看落 crisis_log.view，避免无意留痕；
 * - 不提供导出、不提供复制按钮、不提供批量查看（导出审计未放开）；
 * - 咨询师（role=2）只可见本人认领个案关联用户的记录，空态明确说明口径。
 */

/** 当前用户角色码（与 works.vue 同一口径） */
const userStore = useUserStore();
const currentRole = computed(() => {
  const roles = userStore.userInfo?.roles ?? [];
  if (roles.includes('ROLE_3')) return 3;
  if (roles.includes('ROLE_2')) return 2;
  if (roles.includes('ROLE_1')) return 1;
  return 0;
});
const isCounselor = computed(() => currentRole.value === 2);

/**
 * 列表状态机。
 * 后端 /admin/crisis-logs 仅接受 page（固定每页 20 条），
 * 故 show-sizes 关闭、fetcher 只透传页码。
 */
const {
  tableData,
  loading,
  errorMsg,
  total,
  currentPage,
  pageSize,
  fetchList,
  handlePageChange,
} = useListPage({
  pageSize: 20,
  fetcher: ({ page }) => getCrisisLogs(page),
});

/** 时间格式化：兼容本地串「yyyy-MM-dd HH:mm:ss」与带时区 ISO 串，统一按本地时间展示 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  const d = new Date(String(dt));
  if (Number.isNaN(d.getTime())) return String(dt).replace('T', ' ').slice(0, 19);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/**
 * 风险等级原样透传给 ZyStatusTag（AS 2026-11-04，AQ2 起 1/2/3 真实分级）。
 * CARE_RISK 字典当前只定义 1 一般 / 2 高危；3（紧急）尚无字典定义——
 * 按任务纪律「没有就只报告不要自造」，字典外值由 ZyStatusTag 兜底为原值 + info，
 * 绝不折叠映射（旧 riskKey 会把 3 错标成「一般」，已移除）。
 */

/** 空态按角色区分：咨询师的范围过滤可能天然为空，需说明口径而非「系统无数据」 */
const emptyText = computed(() => (isCounselor.value ? '暂无可见的危机记录' : '暂无危机记录'));
const emptyHint = computed(() =>
  isCounselor.value
    ? '仅显示你认领关怀个案关联用户的危机记录；未认领个案的记录不在此显示'
    : '用户输入命中危机关键词并被拦截后由系统自动留存，仅保存元数据与脱敏摘要',
);

/** ── 详情（确认留痕后才请求） ── */
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<any>(null);
let detailRequest = 0;

async function handleView(row: any) {
  if (!row) return;
  try {
    await ElMessageBox.confirm(
      '本次查看将记入操作审计（crisis_log.view）。确认查看该记录的脱敏摘要？',
      '查看危机记录详情',
      {
        type: 'warning',
        confirmButtonText: '确认查看',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return; // 用户取消，不发请求、不留痕
  }
  const requestId = ++detailRequest;
  detailVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  try {
    const result = await getCrisisLogDetail(String(row.id));
    if (requestId !== detailRequest) return;
    detail.value = result;
  } catch (e: any) {
    if (requestId !== detailRequest) return;
    // 业务 message（中文，含 1004「记录不存在或无权查看」）原样展示
    detailError.value =
      e?.response?.data?.message || (/^[\x20-\x7E]*$/.test(e?.message || '') ? '' : e?.message) || '详情加载失败，请稍后重试。';
  } finally {
    if (requestId === detailRequest) detailLoading.value = false;
  }
}

function resetDetail() {
  detailRequest++;
  detail.value = null;
  detailError.value = '';
  detailLoading.value = false;
}
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="危机日志"
    description="危机关键词命中后的留存记录，仅含元数据与脱敏摘要，供授权角色查看。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    :empty-text="emptyText"
    :empty-hint="emptyHint"
    :show-sizes="false"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #banner>
      <p class="m-0 text-sm crisis-note leading-relaxed">
        本页为危机路径「留存但降级」记录：不保存对话原文，详情仅展示规则式脱敏摘要；每次查看详情都会记入操作审计。本系统不能替代专业心理咨询；遇紧急危机请引导当事人联系当地心理援助热线。
      </p>
    </template>

    <el-table-column label="时间" width="170">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="风险等级" width="100">
      <template #default="{ row }">
        <ZyStatusTag :dict="CARE_RISK" :value="row.riskLevel" />
      </template>
    </el-table-column>
    <el-table-column prop="scene" label="场景" min-width="120" />
    <el-table-column label="用户标识" min-width="180">
      <template #default="{ row }">
        <span class="mono">{{ row.userId }}</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="110" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="handleView(row)">
          查看详情
        </el-button>
      </template>
    </el-table-column>

    <template #append>
      <el-dialog
        v-model="detailVisible"
        title="危机记录详情"
        width="min(520px, 92vw)"
        @closed="resetDetail"
      >
        <el-skeleton v-if="detailLoading" :rows="4" animated />
        <el-alert v-else-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
        <template v-else-if="detail">
          <dl class="detail-grid">
            <div><dt>风险等级</dt><dd><ZyStatusTag :dict="CARE_RISK" :value="detail.riskLevel" /></dd></div>
            <div><dt>场景</dt><dd>{{ detail.scene || '-' }}</dd></div>
            <div><dt>时间</dt><dd>{{ formatTime(detail.createTime) }}</dd></div>
            <div><dt>用户标识</dt><dd class="mono">{{ detail.userId || '-' }}</dd></div>
            <div><dt>会话标识</dt><dd class="mono">{{ detail.sessionId || '-' }}</dd></div>
          </dl>
          <h4 class="summary-title">脱敏摘要</h4>
          <p class="summary-text">{{ detail.summary }}</p>
          <p class="crisis-note text-sm">
            仅展示规则式脱敏摘要，不含对话原文；本次查看已记入操作审计。本系统不能替代专业心理咨询。
          </p>
        </template>
      </el-dialog>
    </template>
  </ZyListPage>
</template>

<style scoped>
.crisis-note {
  color: var(--el-text-color-regular);
}
.mono {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin: 0 0 12px;
}
.detail-grid dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.detail-grid dd {
  margin: 2px 0 0;
}
.summary-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
}
.summary-text {
  margin: 0 0 12px;
  padding: 10px 12px;
  line-height: 1.7;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
</style>
