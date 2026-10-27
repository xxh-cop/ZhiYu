<script setup lang="ts">
import VideoPreview from "./components/VideoPreview.vue";
const videoToReview=ref('');
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { computed, onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import {
  deleteWork,
  getWorkList,
  updateWorkStatus,
} from '#/api/zhiyu/admin-work';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

/** 当前用户角色码 */
const userStore = useUserStore();
const currentRole = computed(() => {
  const roles = userStore.userInfo?.roles ?? [];
  if (roles.includes('ROLE_3')) return 3;
  if (roles.includes('ROLE_2')) return 2;
  if (roles.includes('ROLE_1')) return 1;
  return 0;
});
const isAdmin = computed(() => currentRole.value === 3);

/** 筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待审' },
  { value: 1, label: '已发布' },
  { value: 2, label: '下架' },
];
const visibleOptions = [
  { value: '', label: '全部可见性' },
  { value: 0, label: '私密' },
  { value: 1, label: '公开' },
  { value: 2, label: '仅小组' },
];
const filterStatus = ref<number | string>('');
const filterVisible = ref<number | string>('');
const keyword = ref('');

/** 表格 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const filtersVisible = ref(true);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

/** 状态标签 */
function statusTagType(status: number) {
  switch (status) {
    case 0: return 'warning';
    case 1: return 'success';
    case 2: return 'info';
    default: return 'info';
  }
}

function statusLabel(status: number) {
  switch (status) {
    case 0: return '待审';
    case 1: return '已发布';
    case 2: return '下架';
    default: return '未知';
  }
}

function visibleLabel(visible: number) {
  switch (visible) {
    case 0: return '私密';
    case 1: return '公开';
    case 2: return '仅小组';
    default: return '未知';
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
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (filterVisible.value !== '') params.visible = filterVisible.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const data = await getWorkList(params);
    if (requestId !== listRequest) return;
    tableData.value = data.records || [];
    total.value = data.total || 0;
  } catch (e: any) {
    if (requestId !== listRequest) return;
    errorMsg.value = (e && e.message) || '加载失败';
    tableData.value = [];
    total.value = 0;
  } finally {
    if (requestId === listRequest) loading.value = false;
  }
}

function handleFilterChange() {
  currentPage.value = 1;
  fetchList();
}

function handleSearch() {
  currentPage.value = 1;
  fetchList();
}

function handlePageChange(page: number) {
  currentPage.value = page;
  fetchList();
}

/** 上架 / 下架 */
async function handleToggleStatus(row: any, status: number) {
  const label = status === 1 ? '上架' : '下架';
  try {
    await ElMessageBox.confirm(
      `确认${label}作品「${row.title}」？`,
      '提示',
      { type: 'warning' },
    );
    await updateWorkStatus(row.id, status);
    ElMessage.success(`${label}成功`);
    fetchList();
  } catch {
    // 用户取消或请求失败
  }
}

/** 删除 */
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除作品「${row.title}」？此操作不可逆。`,
      '删除确认',
      { type: 'error' },
    );
    await deleteWork(row.id);
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
    <BusinessPageHeader title="作品管理" description="维护社区作品与内容展示状态。" :loading="loading" @refresh="fetchList" />

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="zy-filter-heading"><span>筛选条件</span><el-button link type="primary" :aria-expanded="filtersVisible" @click="filtersVisible = !filtersVisible">{{ filtersVisible ? '收起筛选' : '展开筛选' }}</el-button></div>
      <div v-show="filtersVisible" class="zy-filter-content flex flex-wrap items-center gap-3">
        <el-select
          v-model="filterStatus"
          placeholder="状态"
          clearable
          style="width: 130px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select
          v-model="filterVisible"
          placeholder="可见性"
          clearable
          style="width: 130px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="opt in visibleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索作品标题"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
      >
        <template #empty>
          <DataState
            :loading="loading"
            :error="errorMsg"
            empty-text="暂无作品"
            @retry="fetchList"
          />
        </template>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="authorName" label="作者" width="100">
          <template #default="{ row }">
            {{ row.anonymous === 1 ? '匿名同学' : (row.authorName || '-') }}
          </template>
        </el-table-column>
        <el-table-column label="视频" width="110"><template #default="{row}"><el-button v-if="row.mediaType==='video' && row.videoUrl" link type="primary" @click="videoToReview=row.videoUrl">查看视频</el-button><span v-else>—</span></template></el-table-column><el-table-column label="封面" width="80">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              :preview-src-list="[row.coverUrl]"
              fit="cover"
              style="width: 40px; height: 40px; border-radius: 4px"
              preview-teleported
            />
            <span v-else class="text-xs text-gray-400">无</span>
          </template>
        </el-table-column>
        <el-table-column label="可见性" width="90">
          <template #default="{ row }">
            {{ visibleLabel(row.visible) }}
          </template>
        </el-table-column>
        <el-table-column prop="likeCount" label="点赞数" width="80" />
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
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="isAdmin && row.status !== 1"
              type="success"
              link
              size="small"
              @click="handleToggleStatus(row, 1)"
            >
              上架
            </el-button>
            <el-button
              v-if="isAdmin && row.status === 1"
              type="warning"
              link
              size="small"
              @click="handleToggleStatus(row, 2)"
            >
              下架
            </el-button>
            <el-button
              v-if="isAdmin"
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
        v-model:page-size="pageSize"
        :show-sizes="true"
        :total="total"
        @change="handlePageChange"
      />
    </el-card>
  </div>
<el-dialog :model-value="!!videoToReview" title="创作视频审核" width="min(720px, 94vw)" @close="videoToReview=''"> <VideoPreview v-if="videoToReview" :src="videoToReview"/><p>请确认内容与授权情况后，再通过审核。</p></el-dialog></template>
