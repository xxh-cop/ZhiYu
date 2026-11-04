<script setup lang="ts">
import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { getAuditLogDetail, getAuditLogList } from '#/api/zhiyu/admin-audit-log';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { AUDIT_ACTION, AUDIT_SCENE, dictLabel } from '#/constants/status-dict';

/** 场景下拉 */
const sceneOptions = [
  { value: '', label: '全部场景' },
  { value: 'chat', label: '对话' },
  { value: 'recommend', label: '推荐' },
  { value: 'work_message', label: '作品寄语' },
];

/** 动作下拉 */
const actionOptions = [
  { value: '', label: '全部动作' },
  { value: 'PASS', label: 'PASS' },
  { value: 'REVIEW', label: 'REVIEW' },
  { value: 'REWRITE', label: 'REWRITE' },
  { value: 'BLOCK', label: 'BLOCK' },
];

/** 筛选 */
const filterScene = ref('');
const filterAction = ref('');

/** 列表状态机（页码/加载/错误/竞态守卫由封装负责） */
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
  fetcher: ({ page, pageSize: size }) => {
    const params: any = { page, pageSize: size };
    if (filterScene.value !== '') params.scene = filterScene.value;
    if (filterAction.value !== '') params.action = filterAction.value;
    return getAuditLogList(params);
  },
});

/** 详情抽屉 */
const drawerVisible = ref(false);
const detailData = ref<any>(null);
const detailLoading = ref(false);

/** 动作/场景文案与色型统一取状态字典（AUDIT_ACTION / AUDIT_SCENE） */

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

/** 查看详情 */
async function handleDetail(row: any) {
  drawerVisible.value = true;
  detailLoading.value = true;
  detailData.value = null;
  try {
    const data = await getAuditLogDetail(row.id);
    detailData.value = data;
  } catch {
    ElMessage.error('加载详情失败');
  } finally {
    detailLoading.value = false;
  }
}
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="AI 审计日志"
    description="追溯 AI 调用与安全过滤记录。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无审计日志"
    empty-hint="AI 调用产生后会在这里留下审计记录"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #banner>
      <p class="m-0 business-note leading-relaxed">
        AI 调用的内容安全过滤记录。列表与详情只展示元数据；按隐私规则，详情不返回原始输入/输出，以固定占位文本替代。
      </p>
    </template>

    <template #filters>
      <el-select
        v-model="filterScene"
        placeholder="场景"
        clearable
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in sceneOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-select
        v-model="filterAction"
        placeholder="动作"
        clearable
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in actionOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>

    <el-table-column prop="id" label="ID" width="180" />
    <el-table-column label="场景" width="100">
      <template #default="{ row }">
        {{ dictLabel(AUDIT_SCENE, row.scene) }}
      </template>
    </el-table-column>
    <el-table-column label="动作" width="110">
      <template #default="{ row }">
        <ZyStatusTag :dict="AUDIT_ACTION" :value="row.action" />
      </template>
    </el-table-column>
    <el-table-column prop="reason" label="命中原因" min-width="200" />
    <el-table-column label="时间" width="160">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="handleDetail(row)">
          详情
        </el-button>
      </template>
    </el-table-column>

    <template #append>
      <!-- 详情抽屉 -->
      <el-drawer
        v-model="drawerVisible"
        title="审计日志详情"
        size="min(560px, 100vw)"
        direction="rtl"
      >
        <div class="flex flex-col gap-4">
          <el-skeleton v-if="detailLoading" :rows="4" animated />
          <template v-else-if="detailData">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
              <el-descriptions-item label="场景">{{ dictLabel(AUDIT_SCENE, detailData.scene) }}</el-descriptions-item>
              <el-descriptions-item label="动作">
                <ZyStatusTag :dict="AUDIT_ACTION" :value="detailData.action" />
              </el-descriptions-item>
              <el-descriptions-item label="命中原因">{{ detailData.reason || '-' }}</el-descriptions-item>
              <el-descriptions-item label="时间">{{ formatTime(detailData.createTime) }}</el-descriptions-item>
            </el-descriptions>

            <div>
              <h4 class="mb-2 text-sm font-medium">原始输入（rawInput）</h4>
              <pre class="raw-preview m-0 max-h-48 overflow-auto rounded p-3 text-sm whitespace-pre-wrap break-words">{{ detailData.rawInput || '-' }}</pre>
            </div>

            <div>
              <h4 class="mb-2 text-sm font-medium">AI 输出（aiOutput）</h4>
              <pre class="raw-preview m-0 max-h-48 overflow-auto rounded p-3 text-sm whitespace-pre-wrap break-words">{{ detailData.aiOutput || '-' }}</pre>
            </div>
          </template>
        </div>
      </el-drawer>
    </template>
  </ZyListPage>
</template>

<style scoped>
/* 用令牌派生底色，暗色态自动跟随（替代硬编码灰底） */
.raw-preview {
  background: var(--el-fill-color-light);
}
</style>
