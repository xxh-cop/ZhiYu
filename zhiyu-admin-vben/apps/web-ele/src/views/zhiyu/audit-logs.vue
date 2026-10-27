<script setup lang="ts">
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { getAuditLogDetail, getAuditLogList } from '#/api/zhiyu/admin-audit-log';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

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

/** 表格 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const filtersVisible = ref(true);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

/** 详情抽屉 */
const drawerVisible = ref(false);
const detailData = ref<any>(null);
const detailLoading = ref(false);

/** 动作标签颜色 */
function actionTagType(action: string) {
  switch (action) {
    case 'PASS': return 'success';
    case 'REVIEW': return 'warning';
    case 'REWRITE': return 'warning';
    case 'BLOCK': return 'danger';
    default: return 'info';
  }
}

/** 场景中文 */
function sceneLabel(scene: string) {
  switch (scene) {
    case 'chat': return '对话';
    case 'recommend': return '推荐';
    case 'work_message': return '作品寄语';
    default: return scene;
  }
}

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

/** 拉取列表 */
let listRequest = 0;
async function fetchList() {
  const requestId = ++listRequest;
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: any = {
      page: currentPage.value,
      pageSize: pageSize.value,
    };
    if (filterScene.value !== '') params.scene = filterScene.value;
    if (filterAction.value !== '') params.action = filterAction.value;
    const data = await getAuditLogList(params);
    if (requestId !== listRequest) return;
    tableData.value = data.records || [];
    total.value = data.total || 0;
  } catch (e: any) {
    if (requestId !== listRequest) return;
    errorMsg.value = (e && e.message) || '加载失败';
  } finally {
    if (requestId === listRequest) loading.value = false;
  }
}

function handleFilterChange() {
  currentPage.value = 1;
  fetchList();
}

function handlePageChange(page: number) {
  currentPage.value = page;
  fetchList();
}

/** 查看详情 */
async function handleDetail(row: any) {
  drawerVisible.value = true;
  detailLoading.value = true;
  detailData.value = null;
  try {
    const data = await getAuditLogDetail(row.id);
    detailData.value = data;
  } catch (e: any) {
    ElMessage.error('加载详情失败');
  } finally {
    detailLoading.value = false;
  }
}

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader title="AI 审计日志" description="追溯 AI 调用与安全过滤记录。" :loading="loading" @refresh="fetchList" />
    <p class="m-0 text-sm text-gray-500 leading-relaxed">
      AI 调用的内容安全过滤记录。列表仅展示元数据，详情含解密后的原始输入与 AI 输出（仅管理员可查看）。
    </p>

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="zy-filter-heading"><span>筛选条件</span><el-button link type="primary" :aria-expanded="filtersVisible" @click="filtersVisible = !filtersVisible">{{ filtersVisible ? '收起筛选' : '展开筛选' }}</el-button></div>
      <div v-show="filtersVisible" class="zy-filter-content flex flex-wrap items-center gap-3">
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
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <DataErrorBanner
        v-if="errorMsg && total > 0"
        :error="errorMsg"
        :retrying="loading"
        @retry="fetchList"
      />
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
      >
        <template #empty>
          <DataState
            :loading="loading"
            :error="errorMsg"
            empty-text="暂无审计日志"
            @retry="fetchList"
          />
        </template>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column label="场景" width="100">
          <template #default="{ row }">
            {{ sceneLabel(row.scene) }}
          </template>
        </el-table-column>
        <el-table-column label="动作" width="110">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">
              {{ row.action }}
            </el-tag>
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
      </el-table>

      <TablePagination
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :show-sizes="true"
        :total="total"
        @change="handlePageChange"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="审计日志详情"
      size="min(560px, 100vw)"
      direction="rtl"
    >
      <div v-loading="detailLoading" class="flex flex-col gap-4">
        <template v-if="detailData">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
            <el-descriptions-item label="场景">{{ sceneLabel(detailData.scene) }}</el-descriptions-item>
            <el-descriptions-item label="动作">
              <el-tag :type="actionTagType(detailData.action)" size="small">
                {{ detailData.action }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="命中原因">{{ detailData.reason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ formatTime(detailData.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <div>
            <h4 class="mb-2 text-sm font-medium text-gray-700">原始输入（rawInput）</h4>
            <pre class="m-0 max-h-48 overflow-auto rounded bg-gray-50 p-3 text-sm whitespace-pre-wrap break-words">{{ detailData.rawInput || '-' }}</pre>
          </div>

          <div>
            <h4 class="mb-2 text-sm font-medium text-gray-700">AI 输出（aiOutput）</h4>
            <pre class="m-0 max-h-48 overflow-auto rounded bg-gray-50 p-3 text-sm whitespace-pre-wrap break-words">{{ detailData.aiOutput || '-' }}</pre>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
