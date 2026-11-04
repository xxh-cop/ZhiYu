<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage } from 'element-plus';

import { getCareReminders } from '#/api/zhiyu/admin';
import { requestClient } from '#/api/request';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { CARE_RISK, CARE_STATUS } from '#/constants/status-dict';

/** 状态筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待处理' },
  { value: 1, label: '已跟进' },
];
const filterStatus = ref<number | string>('');

/**
 * 列表状态机。
 * 后端 /admin/care-reminders 仅接受 page + status（固定每页条数），
 * 故 show-sizes 关闭、fetcher 只透传页码与状态。
 */
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
} = useListPage({
  pageSize: 10,
  fetcher: ({ page }) => {
    const params: any = { page };
    if (filterStatus.value !== '') params.status = filterStatus.value;
    return getCareReminders(params);
  },
});

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 16);
}

/**
 * AW1（2026-11-05）：risk_level 原值透传（后端 AQ2 起真实写 1 关注/2 高危/3 紧急）。
 * 旧 riskKey 折叠（level===2?2:1）会把 3 级紧急错标成「一般」，已移除，
 * 与 crisis-logs.vue 的透传口径对齐。
 */

/** 重置筛选 */
function resetFilters() {
  filterStatus.value = '';
  handleFilterChange();
}

const detail = ref<any>(null);
const drawer = ref(false);
const busy = reactive<Record<string, string>>({});
const detailLoading = ref(false);
const detailError = ref('');
const currentRow = ref<any>(null);
let detailRequest = 0;
const actionLabels: Record<string, string> = {
  CLAIM: '领取任务',
  REVIEWED: '已查看并评估',
  CONTACTED: '记录已联系',
  REFERRED: '记录已转介',
  CLOSE: '完成任务',
  REOPEN: '重新打开',
};

async function handleView(row: any) {
  if (!row) return;
  const requestId = ++detailRequest;
  currentRow.value = row;
  drawer.value = true;
  detail.value = null;
  detailError.value = '';
  detailLoading.value = true;
  try {
    const result = await requestClient.get('/admin/care-reminders/' + row.id);
    if (requestId !== detailRequest) return;
    detail.value = result;
  } catch (e: any) {
    if (requestId !== detailRequest) return;
    detailError.value = e.message || '详情加载失败';
  } finally {
    if (requestId === detailRequest) detailLoading.value = false;
  }
}

function resetDetail() {
  detailRequest++;
  detail.value = null;
  currentRow.value = null;
  detailError.value = '';
  detailLoading.value = false;
}

async function follow(action: string) {
  const id = detail.value?.id;
  if (!id || busy[id]) return;
  busy[id] = action;
  try {
    await requestClient.post('/admin/care-reminders/' + id + '/followups', { action });
    ElMessage.success('跟进已保存');
    const refreshed = await requestClient.get('/admin/care-reminders/' + id);
    if (detail.value?.id === id) detail.value = refreshed;
    await fetchList();
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败');
  } finally {
    delete busy[id];
  }
}

async function claim(row: any) {
  if (busy[row.id]) return;
  busy[row.id] = 'CLAIM';
  try {
    await requestClient.post('/admin/care-reminders/' + row.id + '/followups', { action: 'CLAIM' });
    ElMessage.success('任务已领取');
    await fetchList();
    await handleView(row);
  } catch (e: any) {
    ElMessage.error(e.message || '领取失败');
  } finally {
    delete busy[row.id];
  }
}

/** 通知中心跳转落点：?reminderId= 直达关怀详情抽屉（id 为字符串雪花，原样传递） */
const route = useRoute();
onMounted(() => {
  const reminderId = route.query.reminderId;
  if (typeof reminderId === 'string' && reminderId) {
    handleView({ id: reminderId });
  }
});
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="关怀提醒"
    description="在授权范围内查看脱敏摘要，及时跟进关怀。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无关怀提醒"
    empty-hint="达到关怀阈值且获用户授权后由系统生成，仅显示脱敏摘要"
    :show-sizes="false"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #banner>
      <p class="m-0 text-sm care-note leading-relaxed">
        以下为系统自动生成的脱敏汇总，供授权角色查看。原始对话内容已加密存储，不在此展示。本系统不能替代专业心理咨询。
      </p>
    </template>

    <template #filters>
      <label for="care-status" class="text-sm">状态</label>
      <el-select
        id="care-status"
        v-model="filterStatus"
        placeholder="状态"
        clearable
        style="width: 140px"
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

    <el-table-column label="时间" width="160">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="风险等级" width="100">
      <template #default="{ row }">
        <ZyStatusTag :dict="CARE_RISK" :value="row.riskLevel" />
      </template>
    </el-table-column>
    <el-table-column label="脱敏摘要" min-width="300">
      <template #default="{ row }">
        {{ row.summary || (row.claimed ? '-' : '领取后可见脱敏摘要') }}
      </template>
    </el-table-column>
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <ZyStatusTag :dict="CARE_STATUS" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="操作" width="150" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="handleView(row)">
          查看
        </el-button>
        <el-button
          v-if="!row.claimed"
          type="primary"
          link
          size="small"
          :loading="busy[row.id] === 'CLAIM'"
          @click="claim(row)"
        >
          领取
        </el-button>
      </template>
    </el-table-column>

    <template #append>
      <el-drawer v-model="drawer" title="关怀跟进" size="min(560px, 100vw)" @closed="resetDetail">
        <el-skeleton v-if="detailLoading" :rows="4" animated />
        <el-alert v-else-if="detailError" :title="detailError" type="error" :closable="false">
          <template #default>
            <el-button size="small" @click="handleView(currentRow)">重试</el-button>
          </template>
        </el-alert>
        <template v-else-if="detail">
          <el-alert
            v-if="detail.claimed"
            :title="Number(detail.can_contact) === 1 ? '用户已授权联系；记录联系前仍会校验授权' : '用户未授权联系，请尊重其选择'"
            :type="Number(detail.can_contact) === 1 ? 'success' : 'warning'"
            :closable="false"
          />
          <p v-if="detail.summary" class="my-4 leading-relaxed">{{ detail.summary }}</p>
          <p v-else class="my-4 leading-relaxed care-note">领取后可见脱敏摘要与关联信息。</p>
          <p>任务状态：{{ detail.state === 'CLOSED' ? '已完成' : detail.claimed ? '已领取' : '待领取' }}</p>
          <div class="my-4 flex flex-wrap gap-2">
            <el-button
              v-if="!detail.claimed"
              type="primary"
              :loading="busy[detail.id] === 'CLAIM'"
              @click="follow('CLAIM')"
            >
              领取任务
            </el-button>
            <template v-else-if="detail.state !== 'CLOSED'">
              <el-button
                v-for="a in ['REVIEWED', 'CONTACTED', 'REFERRED', 'CLOSE']"
                :key="a"
                :loading="busy[detail.id] === a"
                :disabled="(!!busy[detail.id] && busy[detail.id] !== a) || (a === 'CONTACTED' && Number(detail.can_contact) !== 1)"
                @click="follow(a)"
              >
                {{ actionLabels[a] }}
              </el-button>
            </template>
            <el-button v-else :loading="busy[detail.id] === 'REOPEN'" @click="follow('REOPEN')">
              重新打开
            </el-button>
          </div>
          <el-timeline v-if="detail.events && detail.events.length" class="mt-6">
            <el-timeline-item
              v-for="(e, i) in detail.events"
              :key="i"
              :timestamp="formatTime(e.create_time)"
            >
              {{ actionLabels[e.action] || e.action }}
            </el-timeline-item>
          </el-timeline>
          <p class="text-sm care-note">只记录处理动作，不收集可识别个人身份的跟进正文。本系统不能替代专业心理咨询。</p>
        </template>
      </el-drawer>
    </template>
  </ZyListPage>
</template>

<style scoped>
.care-note {
  color: var(--el-text-color-regular);
}
</style>
