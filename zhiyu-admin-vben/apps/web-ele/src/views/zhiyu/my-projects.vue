<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteProject, getMyProjects } from '#/api/zhiyu/inheritor';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { PROJECT_STATUS } from '#/constants/status-dict';

const router = useRouter();

/** 筛选（文案与状态字典 PROJECT_STATUS 一致，源自 DDL 注释；3=归档 自分层下架起） */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待审' },
  { value: 1, label: '上架' },
  { value: 2, label: '下架' },
  { value: 3, label: '归档' },
];
const filterStatus = ref<number | string>('');

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
  pageSize: 10,
  fetcher: ({ page, pageSize: size }) => {
    const params: any = { page, pageSize: size };
    if (filterStatus.value !== '') params.status = filterStatus.value;
    return getMyProjects(params);
  },
});

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
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
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="我的项目"
    description="维护你的手作项目、步骤与材料；提交后由平台审核。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无项目"
    empty-hint="点击「新建项目」开始创建"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #headerActions>
      <div class="flex flex-wrap gap-2">
        <el-button :loading="loading" @click="fetchList">刷新列表</el-button>
        <el-button type="primary" @click="handleCreate">新建项目</el-button>
      </div>
    </template>

    <template #filters>
      <el-select
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
    <el-table-column prop="category" label="品类" width="100" />
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <ZyStatusTag :dict="PROJECT_STATUS" :value="row.status" />
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
  </ZyListPage>
</template>

<style scoped>
.cover-fallback {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
}
</style>
