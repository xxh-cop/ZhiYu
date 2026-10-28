<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteProject, getMyProjects } from '#/api/zhiyu/inheritor';
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import DataErrorBanner from '#/components/data-state/DataErrorBanner.vue';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

const router = useRouter();

/** 筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待审' },
  { value: 1, label: '上架' },
  { value: 2, label: '下架' },
];
const filterStatus = ref<number | string>('');

/** 表格 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 状态标签 */
function statusTagType(status: number) {
  switch (status) {
    case 0:
      return 'warning';
    case 1:
      return 'success';
    case 2:
      return 'info';
    default:
      return 'info';
  }
}

function statusLabel(status: number) {
  switch (status) {
    case 0:
      return '待审';
    case 1:
      return '上架';
    case 2:
      return '下架';
    default:
      return '未知';
  }
}

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

/** 拉取列表 */
async function fetchList() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: any = { page: currentPage.value };
    if (filterStatus.value !== '') params.status = filterStatus.value;
    const data = await getMyProjects(params);
    tableData.value = data.records || [];
    total.value = data.total || 0;
  } catch (e: any) {
    errorMsg.value = (e && e.message) || '加载失败';
  } finally {
    loading.value = false;
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

/** 跳转编辑 */
function handleEdit(row: any) {
  router.push(`/my-inheritance/project-edit/${row.id}`);
}

/** 跳转新建 */
function handleCreate() {
  router.push('/my-inheritance/project-new');
}

/** 删除 */
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除项目「${row.title}」？此操作将级联删除所有步骤和材料，不可逆。`,
      '删除确认',
      { type: 'error' },
    );
    await deleteProject(row.id);
    ElMessage.success('删除成功');
    fetchList();
  } catch {
    // 用户取消或请求失败
  }
}

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader
      title="我的项目"
      description="维护你的手作项目、步骤与材料；提交后由平台审核。"
      :loading="loading"
      @refresh="fetchList"
    >
      <div class="flex flex-wrap gap-2">
        <el-button :loading="loading" @click="fetchList">刷新列表</el-button>
        <el-button type="primary" @click="handleCreate">新建项目</el-button>
      </div>
    </BusinessPageHeader>

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="flex flex-wrap items-center gap-3">
        <label class="filter-label" for="my-project-status">项目状态</label>
        <el-select
          id="my-project-status"
          v-model="filterStatus"
          placeholder="全部状态"
          clearable
          style="width: 150px"
          aria-label="项目状态"
          @change="handleFilterChange"
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
            empty-text="暂无项目"
            empty-hint="点击「新建项目」开始创建"
            @retry="fetchList"
          />
        </template>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column
          prop="title"
          label="标题"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              :preview-src-list="[row.coverUrl]"
              fit="cover"
              style="width: 40px; height: 40px; border-radius: 4px"
              preview-teleported
            >
              <template #error>
                <img class="cover-fallback" src="/library/paper.jpg" alt="项目封面占位" />
              </template>
            </el-image>
            <img v-else class="cover-fallback" src="/library/paper.jpg" alt="项目封面占位" />
          </template>
        </el-table-column>
        <el-table-column
          prop="category"
          label="品类"
          width="100"
        />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.status === 0"
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <TablePagination
        v-model:page="currentPage"
        :page-size="pageSize"
        :total="total"
        @change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.filter-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.muted-inline {
  color: var(--el-text-color-regular);
}
.cover-fallback {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
}
</style>
