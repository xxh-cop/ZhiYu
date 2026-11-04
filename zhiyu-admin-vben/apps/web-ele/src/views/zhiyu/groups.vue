<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useUserStore } from '@vben/stores';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteGroup,
  getGroupDetail,
  getGroupList,
  updateGroupStatus,
} from '#/api/zhiyu/admin-group';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { GROUP_STATUS } from '#/constants/status-dict';
import { useSearchRegistryStore } from '#/store/search-registry';

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
  { value: 0, label: '关闭' },
  { value: 1, label: '开放' },
];
const filterStatus = ref<number | string>('');
const keyword = ref('');

/**
 * 通知中心落点：?groupId= 定位小组行。
 * 与 users 页 ?keyword= 同一范式（先确定筛选值再拉列表）：
 * 先按 id 取详情拿名称 → 名称进关键字 → 列表过滤到该行 → 高亮 + 滚动到可视区；
 * 详情 404 说明小组已删除，给明确空态说明，不假装定位成功。
 */
const route = useRoute();
const locatedGroupId = ref('');
const groupNotice = ref('');

function rowClassName({ row }: { row: any }) {
  return String(row.id) === locatedGroupId.value ? 'zy-row-located' : '';
}

async function scrollToLocated() {
  await nextTick();
  document
    .querySelector('.zy-row-located')
    ?.scrollIntoView({ block: 'center', behavior: 'smooth' });
}

/**
 * ?groupId= 定位流程（通知中心 / 全局搜索共用落点）。
 * 页面被标签页 keepAlive 缓存时，再次带 query 进入不触发 onMounted，
 * 故抽成函数并由 onMounted + watch 双通道调用。
 */
async function locateGroup(groupId: string) {
  if (!groupId) return;
  try {
    const detail = await getGroupDetail(groupId as any);
    locatedGroupId.value = String(groupId);
    keyword.value = detail?.name || '';
    currentPage.value = 1;
    await fetchList();
    await scrollToLocated();
    ElMessage.success(`已定位小组「${detail?.name || groupId}」`);
  } catch {
    groupNotice.value = '未找到该小组（可能已删除）。可尝试用名称关键字搜索。';
  }
}

onMounted(() => {
  const groupId = route.query.groupId;
  if (typeof groupId === 'string') locateGroup(groupId);
});

watch(
  () => route.query.groupId,
  (val) => {
    if (typeof val === 'string') locateGroup(val);
  },
);

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
  handleSearch,
  handlePageChange,
} = useListPage({
  fetcher: ({ page, pageSize: size }) => {
    const params: any = { page, pageSize: size };
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    return getGroupList(params);
  },
});

/** 全局搜索实体注册：本会话加载过的小组行进入命令面板候选（AB2，不查远端） */
const searchRegistry = useSearchRegistryStore();
watch(tableData, (rows) => {
  searchRegistry.register(
    'group',
    rows.map((r: any) => ({ id: r.id, label: r.name })),
  );
});

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

/** 开放 / 关闭 */
async function handleToggleStatus(row: any, status: number) {
  const label = status === 1 ? '开放' : '关闭';
  try {
    await ElMessageBox.confirm(
      `确认${label}小组「${row.name}」？`,
      '提示',
      { type: 'warning' },
    );
    await updateGroupStatus(row.id, status);
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
      `确认删除小组「${row.name}」？此操作不可逆。`,
      '删除确认',
      { type: 'error' },
    );
    await deleteGroup(row.id);
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
    title="小组管理"
    description="维护互助空间，连接共同创作的伙伴。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无小组"
    empty-hint="学生创建互助小组后会出现在这里；可调整状态或名称关键字"
    :row-class-name="rowClassName"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template v-if="groupNotice" #banner>
      <el-alert :title="groupNotice" type="warning" :closable="false" show-icon />
    </template>
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
      <el-input
        v-model="keyword"
        placeholder="搜索小组名称"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </template>

    <el-table-column prop="id" label="ID" width="180" />
    <el-table-column prop="name" label="名称" width="160" show-overflow-tooltip />
    <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
      <template #default="{ row }">
        {{ row.description || '-' }}
      </template>
    </el-table-column>
    <el-table-column label="成员数" width="100">
      <template #default="{ row }">
        {{ row.memberCount }} / {{ row.maxCount }}
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <ZyStatusTag :dict="GROUP_STATUS" :value="row.status" />
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
          v-if="isAdmin && row.status === 0"
          type="success"
          link
          size="small"
          @click="handleToggleStatus(row, 1)"
        >
          开放
        </el-button>
        <el-button
          v-if="isAdmin && row.status === 1"
          type="warning"
          link
          size="small"
          @click="handleToggleStatus(row, 0)"
        >
          关闭
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
  </ZyListPage>
</template>

<style scoped>
:deep(.zy-row-located td) {
  background: var(--el-color-primary-light-9) !important;
}
</style>
