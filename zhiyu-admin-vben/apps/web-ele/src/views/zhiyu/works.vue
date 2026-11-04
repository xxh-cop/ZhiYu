<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import {
  deleteWork,
  getWorkList,
  updateWorkStatus,
} from '#/api/zhiyu/admin-work';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import {
  dictLabel,
  WORK_STATUS,
  WORK_VISIBLE,
} from '#/constants/status-dict';
import { useSearchRegistryStore } from '#/store/search-registry';
import { exportCsvWithConfirm } from '#/utils/csv-export';

import VideoPreview from './components/VideoPreview.vue';
import WorkDetailDrawer from './components/WorkDetailDrawer.vue';

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

/** 列表状态机 */
const {
  tableData,
  loading,
  errorMsg,
  total,
  currentPage,
  pageSize,
  fetchList,
  handleFilterChange,
  handleSearch,
  handlePageChange,
} = useListPage({
  fetcher: ({ page, pageSize: size }) => {
    const params: any = { page, pageSize: size };
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (filterVisible.value !== '') params.visible = filterVisible.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    return getWorkList(params);
  },
});

/** 全局搜索实体注册：本会话加载过的作品行进入命令面板候选（AB2，不查远端） */
const searchRegistry = useSearchRegistryStore();
watch(tableData, (rows) => {
  searchRegistry.register(
    'work',
    rows.map((r: any) => ({ id: r.id, label: r.title })),
  );
});

/** 状态标签（文案与色型统一取状态字典，选项含「全部」故保留页内） */
function visibleLabel(visible: number) {
  return dictLabel(WORK_VISIBLE, visible);
}

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

/** 视频审核 */
const videoToReview = ref('');

/** 上架 / 下架（下架必填理由：服务端 I1 校验，理由落库并随通知送达作者） */
async function handleToggleStatus(row: any, status: number) {
  const label = status === 1 ? '上架' : '下架';
  try {
    let reason: string | undefined;
    if (status === 2) {
      const { value } = await ElMessageBox.prompt(
        `下架后作者会看到该理由。请填写下架「${row.title}」的理由：`,
        '下架作品',
        {
          type: 'warning',
          confirmButtonText: '确认下架',
          inputPlaceholder: '必填，不超过 255 字',
          inputValidator: (v: string) => (v && v.trim() ? true : '请填写下架理由'),
          inputErrorMessage: '请填写下架理由',
        },
      );
      reason = value.trim();
    } else {
      await ElMessageBox.confirm(`确认上架作品「${row.title}」？`, '提示', {
        type: 'warning',
      });
    }
    await updateWorkStatus(row.id, status, reason);
    ElMessage.success(`${label}成功`);
    fetchList();
  } catch {
    // 用户取消或请求失败
  }
}

/** 当前筛选的人类可读摘要（导出审计留痕，不含敏感值） */
function exportFilterSummary() {
  const parts: string[] = [];
  if (filterStatus.value !== '') parts.push(`状态=${dictLabel(WORK_STATUS, filterStatus.value)}`);
  if (filterVisible.value !== '') parts.push(`可见性=${dictLabel(WORK_VISIBLE, filterVisible.value)}`);
  if (keyword.value.trim()) parts.push(`关键字=${keyword.value.trim()}`);
  return parts.length ? parts.join('；') : '全部作品';
}

/** 导出当前筛选结果 CSV（字段 = 当前列；先登记审计留痕再下载，登记失败即中止） */
const exporting = ref(false);
async function handleExport() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    await exportCsvWithConfirm({
      total: total.value,
      targetType: 'works',
      filterSummary: exportFilterSummary(),
      columns: [
        { header: '作品 ID', value: (r: any) => r.id },
        { header: '标题', value: (r: any) => r.title },
        { header: '作者', value: (r: any) => r.authorName },
        { header: '可见性', value: (r: any) => dictLabel(WORK_VISIBLE, r.visible) },
        { header: '点赞数', value: (r: any) => r.likeCount },
        { header: '状态', value: (r: any) => dictLabel(WORK_STATUS, r.status) },
        { header: '创建时间', value: (r: any) => formatTime(r.createTime) },
      ],
      fetchPage: (page) => {
        const params: any = { page, pageSize: 100 };
        if (filterStatus.value !== '') params.status = filterStatus.value;
        if (filterVisible.value !== '') params.visible = filterVisible.value;
        if (keyword.value.trim()) params.keyword = keyword.value.trim();
        return getWorkList(params);
      },
    });
  } finally {
    exporting.value = false;
  }
}

/** 详情抽屉（雪花 ID 字符串原样传递） */
const drawerWorkId = ref<null | string>(null);
function openDetail(row: any) {
  drawerWorkId.value = String(row.id);
}
function closeDetail() {
  drawerWorkId.value = null;
}
function onReviewed() {
  drawerWorkId.value = null;
  fetchList();
}

/** 通知中心 / 全局搜索落点：?workId= 直达详情抽屉（keepAlive 缓存页靠 watch 再触发） */
const route = useRoute();
onMounted(() => {
  const workId = route.query.workId;
  if (typeof workId === 'string' && workId) {
    drawerWorkId.value = workId;
  }
});
watch(
  () => route.query.workId,
  (workId) => {
    if (typeof workId === 'string' && workId) {
      drawerWorkId.value = workId;
    }
  },
);

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
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="作品管理"
    description="维护社区作品与内容展示状态。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无作品"
    empty-hint="学生发布作品后会出现在这里；可调整状态、可见性与关键字筛选"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #filters>
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
    </template>
    <template #actions>
      <el-button :loading="exporting" :disabled="!total" @click="handleExport">
        导出 CSV
      </el-button>
    </template>

    <el-table-column prop="id" label="ID" width="180" />
    <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
    <el-table-column prop="authorName" label="作者" width="100">
      <template #default="{ row }">
        {{ row.anonymous === 1 ? '匿名同学' : (row.authorName || '-') }}
      </template>
    </el-table-column>
    <el-table-column label="视频" width="110">
      <template #default="{ row }">
        <el-button
          v-if="row.mediaType === 'video' && row.videoUrl"
          link
          type="primary"
          @click="videoToReview = row.videoUrl"
        >
          查看视频
        </el-button>
        <span v-else>—</span>
      </template>
    </el-table-column>
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
            <img class="cover-fallback" src="/library/paper.jpg" alt="作品封面占位" />
          </template>
        </el-image>
        <img v-else class="cover-fallback" src="/library/paper.jpg" alt="作品封面占位" />
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
        <ZyStatusTag :dict="WORK_STATUS" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="创建时间" width="160">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="openDetail(row)">
          查看
        </el-button>
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

    <template #append>
      <WorkDetailDrawer
        :work-id="drawerWorkId"
        @close="closeDetail"
        @reviewed="onReviewed"
      />
      <el-dialog
        :model-value="!!videoToReview"
        title="创作视频审核"
        width="min(720px, 94vw)"
        @close="videoToReview = ''"
      >
        <VideoPreview v-if="videoToReview" :src="videoToReview" />
        <p>请确认内容与授权情况后，再通过审核。</p>
      </el-dialog>
    </template>
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
