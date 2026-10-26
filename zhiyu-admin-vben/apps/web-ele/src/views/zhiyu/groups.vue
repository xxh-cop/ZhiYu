<script setup lang="ts">
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { computed, onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import {
  deleteGroup,
  getGroupList,
  updateGroupStatus,
} from '#/api/zhiyu/admin-group';

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

/** 表格 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const filtersVisible = ref(true);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

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
    const params: any = { page: currentPage.value };
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const data = await getGroupList(params);
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

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader title="小组管理" description="维护互助空间，连接共同创作的伙伴。" :loading="loading" @refresh="fetchList" />

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
        <el-input
          v-model="keyword"
          placeholder="搜索小组名称"
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
        :empty-text="errorMsg || '暂无小组'"
      >
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
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '开放' : '关闭' }}
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
