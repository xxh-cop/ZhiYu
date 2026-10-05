<script setup>
import { ref, onMounted } from 'vue'

import { getCareReminders } from '../api/admin'

/** 状态筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待处理' },
  { value: 1, label: '已跟进' },
]
const filterStatus = ref('')

/** 表格数据 */
const tableData = ref([])
const loading = ref(false)
const errorMsg = ref('')

/** 分页 */
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10

/** 格式化时间 */
function formatTime(dt) {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 16)
}

/** 风险标签 */
function riskLabel(level) {
  return level === 2 ? '高危' : '一般'
}
function riskTagType(level) {
  return level === 2 ? 'danger' : 'info'
}

/** 状态标签 */
function statusLabel(status) {
  return status === 0 ? '待处理' : '已跟进'
}
function statusTagType(status) {
  return status === 0 ? 'warning' : 'success'
}

/** 拉取列表 */
async function fetchList() {
  loading.value = true
  errorMsg.value = ''
  try {
    const params = { page: currentPage.value }
    if (filterStatus.value !== '') {
      params.status = filterStatus.value
    }
    const data = await getCareReminders(params)
    tableData.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    errorMsg.value = (e && e.message) || '加载失败'
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 状态变化 */
function handleStatusChange() {
  currentPage.value = 1
  fetchList()
}

/** 翻页 */
function handlePageChange(page) {
  currentPage.value = page
  fetchList()
}

function handleView(row) {
  ElMessage.info(`关怀提醒 #${row.id} 详情功能开发中`)
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="care-page">
    <h2 class="page-title">关怀提醒</h2>
    <p class="page-desc">
      以下为系统自动生成的脱敏汇总，供授权角色查看。原始对话内容已加密存储，不在此展示。
    </p>

    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
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

    <el-card class="table-card" shadow="never">
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
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.care-page {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-16);
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--zy-text);
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #8a8378;
  line-height: 1.6;
}

.filter-card,
.table-card {
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);
}

.filter-bar {
  display: flex;
  gap: var(--zy-space-12);
  align-items: center;
}

.pagination {
  margin-top: var(--zy-space-16);
  justify-content: flex-end;
}

:deep(.el-table th.el-table__cell) {
  background-color: var(--zy-bg);
}
</style>
