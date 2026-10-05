<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { auditProject, getAuditQueue } from '#/api/zhiyu/admin';

/** 类型筛选选项 */
const typeOptions = [{ value: 'project', label: '项目' }];
const filterType = ref('project');
const searchText = ref('');

/** 表格数据 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 驳回弹窗 */
const rejectDialogVisible = ref(false);
const rejectRow = ref<any>(null);
const rejectReason = ref('');
const rejectLoading = ref(false);

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 16);
}

/** 拉取列表 */
async function fetchList() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await getAuditQueue({
      type: filterType.value || 'project',
      page: currentPage.value,
    });
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

/** 前端搜索（在已加载的数据中过滤标题） */
const filteredData = ref<any[]>([]);
function applyFilter() {
  if (!searchText.value) {
    filteredData.value = tableData.value;
    return;
  }
  const kw = searchText.value.toLowerCase();
  filteredData.value = tableData.value.filter((row) =>
    (row.title || '').toLowerCase().includes(kw),
  );
}

/** 监听 tableData 变化自动过滤 */
function syncFilter() {
  applyFilter();
}

/** 通过 */
async function handleApprove(row: any) {
  try {
    await auditProject(row.id, { action: 'approve' });
    ElMessage.success(`「${row.title}」已通过`);
    await fetchList();
    syncFilter();
  } catch {
    // 拦截器已弹错误提示
  }
}

/** 打开驳回弹窗 */
function handleReject(row: any) {
  rejectRow.value = row;
  rejectReason.value = '';
  rejectDialogVisible.value = true;
}

/** 确认驳回 */
async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由');
    return;
  }
  rejectLoading.value = true;
  try {
    await auditProject(rejectRow.value.id, {
      action: 'reject',
      reason: rejectReason.value.trim(),
    });
    ElMessage.success(`「${rejectRow.value.title}」已驳回`);
    rejectDialogVisible.value = false;
    await fetchList();
    syncFilter();
  } catch {
    // 拦截器已弹错误提示
  } finally {
    rejectLoading.value = false;
  }
}

/** 翻页 */
function handlePageChange(page: number) {
  currentPage.value = page;
  fetchList();
}

/** 类型变化 */
function handleTypeChange() {
  currentPage.value = 1;
  fetchList();
}

onMounted(() => {
  fetchList().then(syncFilter);
});
</script>

<template>
  <div class="flex flex-col gap-4 p-4">
    <h2 class="m-0 text-xl font-semibold">内容审核</h2>

    <el-card shadow="never">
      <div class="flex items-center gap-3">
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

    <el-card shadow="never">
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
        class="mt-4 flex justify-end"
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
      <p class="mb-3 text-sm text-gray-500">请填写驳回理由（将通知传承人）</p>
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
