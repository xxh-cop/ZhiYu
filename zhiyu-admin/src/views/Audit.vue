<script setup>
import { ref, onMounted } from 'vue'

import { getAuditQueue, auditProject } from '../api/admin'

/** 类型筛选选项 */
const typeOptions = [
  { value: 'project', label: '项目' },
]
const filterType = ref('project')
const searchText = ref('')

/** 表格数据 */
const tableData = ref([])
const loading = ref(false)
const errorMsg = ref('')

/** 分页 */
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10

/** 驳回弹窗 */
const rejectDialogVisible = ref(false)
const rejectRow = ref(null)
const rejectReason = ref('')
const rejectLoading = ref(false)

/** 格式化时间 */
function formatTime(dt) {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 16)
}

/** 拉取列表 */
async function fetchList() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAuditQueue({ type: filterType.value || 'project', page: currentPage.value })
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

/** 前端搜索（在已加载的数据中过滤标题） */
const filteredData = ref([])
function applyFilter() {
  if (!searchText.value) {
    filteredData.value = tableData.value
    return
  }
  const kw = searchText.value.toLowerCase()
  filteredData.value = tableData.value.filter(
    (row) => (row.title || '').toLowerCase().includes(kw),
  )
}

/** 监听 tableData 变化自动过滤 */
function syncFilter() {
  applyFilter()
}

/** 通过 */
async function handleApprove(row) {
  try {
    await auditProject(row.id, { action: 'approve' })
    ElMessage.success(`「${row.title}」已通过`)
    await fetchList()
    syncFilter()
  } catch (e) {
    // 拦截器已弹错误提示
  }
}

/** 打开驳回弹窗 */
function handleReject(row) {
  rejectRow.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

/** 确认驳回 */
async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  rejectLoading.value = true
  try {
    await auditProject(rejectRow.value.id, { action: 'reject', reason: rejectReason.value.trim() })
    ElMessage.success(`「${rejectRow.value.title}」已驳回`)
    rejectDialogVisible.value = false
    await fetchList()
    syncFilter()
  } catch (e) {
    // 拦截器已弹错误提示
  } finally {
    rejectLoading.value = false
  }
}

/** 翻页 */
function handlePageChange(page) {
  currentPage.value = page
  fetchList()
}

/** 类型变化 */
function handleTypeChange() {
  currentPage.value = 1
  fetchList()
}

onMounted(() => {
  fetchList().then(syncFilter)
})
</script>

<template>
  <div class="audit-page">
    <h2 class="page-title">内容审核</h2>

    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-select
          v-model="filterType"
          placeholder="类型"
          style="width: 140px"
          @change="handleTypeChange"
        >
          <el-option
            v-for="opt in typeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="searchText"
          placeholder="搜索标题"
          clearable
          style="width: 260px"
          @input="applyFilter"
          @clear="applyFilter"
        />
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="filteredData"
        style="width: 100%"
        :empty-text="errorMsg || '暂无待审核内容'"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column prop="category" label="品类" width="100" />
        <el-table-column label="难度" width="80">
          <template #default="{ row }">
            {{ row.level === 1 ? '入门' : row.level === 2 ? '进阶' : row.level === 3 ? '高阶' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="estMinutes" label="时长(分)" width="100" />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleApprove(row)">
              通过
            </el-button>
            <el-button type="danger" size="small" @click="handleReject(row)">
              驳回
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

    <!-- 驳回弹窗 -->
    <el-dialog
      v-model="rejectDialogVisible"
      title="驳回项目"
      width="440px"
    >
      <p class="reject-tip">请填写驳回理由（将通知传承人）</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="请输入驳回理由"
        maxlength="200"
        show-word-limit
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectLoading" @click="confirmReject">
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.audit-page {
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

.reject-tip {
  margin: 0 0 var(--zy-space-12);
  font-size: 13px;
  color: #8a8378;
}

/* 表头背景色 */
:deep(.el-table th.el-table__cell) {
  background-color: var(--zy-bg);
}
</style>
