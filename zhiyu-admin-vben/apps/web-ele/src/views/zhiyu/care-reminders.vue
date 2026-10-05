<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { getCareReminders } from '#/api/zhiyu/admin';

/** 状态筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待处理' },
  { value: 1, label: '已跟进' },
];
const filterStatus = ref<number | string>('');

/** 表格数据 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 16);
}

/** 风险标签 */
function riskLabel(level: number) {
  return level === 2 ? '高危' : '一般';
}
function riskTagType(level: number) {
  return level === 2 ? 'danger' : 'info';
}

/** 状态标签 */
function statusLabel(status: number) {
  return status === 0 ? '待处理' : '已跟进';
}
function statusTagType(status: number) {
  return status === 0 ? 'warning' : 'success';
}

/** 拉取列表 */
async function fetchList() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: any = { page: currentPage.value };
    if (filterStatus.value !== '') {
      params.status = filterStatus.value;
    }
    const data = await getCareReminders(params);
    tableData.value = data.records || [];
    total.value = data.total || 0;
  } catch (e: any) {
    errorMsg.value = (e && e.message) || '加载失败';
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 状态变化 */
function handleStatusChange() {
  currentPage.value = 1;
  fetchList();
}

/** 翻页 */
function handlePageChange(page: number) {
  currentPage.value = page;
  fetchList();
}

function handleView(row: any) {
  ElMessage.info(`关怀提醒 #${row.id} 详情功能开发中`);
}

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="flex flex-col gap-4 p-4">
    <h2 class="m-0 text-xl font-semibold">关怀提醒</h2>
    <p class="m-0 text-sm text-gray-500 leading-relaxed">
      以下为系统自动生成的脱敏汇总，供授权角色查看。原始对话内容已加密存储，不在此展示。
    </p>

    <el-card shadow="never">
      <div class="flex items-center gap-3">
        <el-select
          v-model="filterStatus"
          placeholder="状态"
          clearable
          style="width: 140px"
          @change="handleStatusChange"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        :empty-text="errorMsg || '暂无关怀提醒'"
      >
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small">
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="脱敏摘要" min-width="300" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="mt-4 flex justify-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>
