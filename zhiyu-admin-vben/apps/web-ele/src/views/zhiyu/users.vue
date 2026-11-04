<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteUser,
  getUserList,
  updateUser,
  updateUserRole,
  updateUserStatus,
} from '#/api/zhiyu/admin-user';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { ACCOUNT_STATUS, dictLabel, ROLE } from '#/constants/status-dict';
import { useSearchRegistryStore } from '#/store/search-registry';
import { exportCsvWithConfirm } from '#/utils/csv-export';

import CreateUserDialog from './components/CreateUserDialog.vue';

/** 筛选（展示文案以状态字典为准，选项含「全部」故保留页内） */
const roleOptions = [
  { value: '', label: '全部角色' },
  { value: 0, label: '学生' },
  { value: 1, label: '传承人' },
  { value: 2, label: '咨询师' },
  { value: 3, label: '管理员' },
];
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 1, label: '正常' },
  { value: 0, label: '禁用' },
];
const filterRole = ref<number | string>('');
const filterStatus = ref<number | string>('');
/** 支持 ?keyword= 下钻落点：从路由 query 初始化关键字，筛选框与首次请求一致 */
const route = useRoute();
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '');
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
    if (filterRole.value !== '') params.role = filterRole.value;
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    return getUserList(params);
  },
});

/** 全局搜索实体注册：本会话加载过的用户行进入命令面板候选（AB2，不查远端） */
const searchRegistry = useSearchRegistryStore();
watch(tableData, (rows) => {
  searchRegistry.register(
    'user',
    rows.map((r: any) => ({
      id: r.id,
      label: r.nickname || r.username,
      query: r.username,
    })),
  );
});

/** keepAlive 缓存下再次带 ?keyword= 进入时不走 setup，监听 query 变化同步筛选 */
watch(
  () => route.query.keyword,
  (val) => {
    if (typeof val === 'string' && val && val !== keyword.value) {
      keyword.value = val;
      handleSearch();
    }
  },
);

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 19);
}

/** 编辑弹窗 */
const editDialogVisible = ref(false);
const editForm = ref({ id: 0, nickname: '', school: '' });

function handleEdit(row: any) {
  editForm.value = { id: row.id, nickname: row.nickname || '', school: row.school || '' };
  editDialogVisible.value = true;
}

async function submitEdit() {
  try {
    await updateUser(editForm.value.id, {
      nickname: editForm.value.nickname,
      school: editForm.value.school,
    });
    ElMessage.success('编辑成功');
    editDialogVisible.value = false;
    fetchList();
  } catch {
    // 拦截器已提示
  }
}

/** 状态切换 */
async function handleToggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1;
  const label = newStatus === 0 ? '禁用' : '启用';
  try {
    await ElMessageBox.confirm(`确认${label}用户「${row.nickname}」？`, '提示', {
      type: 'warning',
    });
    await updateUserStatus(row.id, newStatus);
    ElMessage.success(`${label}成功`);
    fetchList();
  } catch {
    // 用户取消或请求失败
  }
}

/** 变更角色弹窗 */
const roleDialogVisible = ref(false);
const roleForm = ref({ id: 0, role: 0 });

function handleChangeRole(row: any) {
  roleForm.value = { id: row.id, role: row.role };
  roleDialogVisible.value = true;
}

async function submitRole() {
  try {
    await updateUserRole(roleForm.value.id, roleForm.value.role);
    ElMessage.success('角色变更成功');
    roleDialogVisible.value = false;
    fetchList();
  } catch {
    // 拦截器已提示
  }
}

/** 删除 */
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.nickname}」？此操作不可逆。`,
      '删除确认',
      { type: 'error' },
    );
    await deleteUser(row.id);
    ElMessage.success('删除成功');
    fetchList();
  } catch {
    // 用户取消或请求失败
  }
}

/** 新建用户弹窗 */
const createDialogRef = ref<InstanceType<typeof CreateUserDialog>>();
function openCreateDialog() {
  createDialogRef.value?.open();
}

/** 当前筛选的人类可读摘要（导出审计留痕，不含敏感值） */
function exportFilterSummary() {
  const parts: string[] = [];
  if (filterRole.value !== '') parts.push(`角色=${dictLabel(ROLE, filterRole.value)}`);
  if (filterStatus.value !== '') parts.push(`状态=${dictLabel(ACCOUNT_STATUS, filterStatus.value)}`);
  if (keyword.value.trim()) parts.push(`关键字=${keyword.value.trim()}`);
  return parts.length ? parts.join('；') : '全部用户';
}

/** 导出当前筛选结果 CSV（字段 = 当前列；先登记审计留痕再下载，登记失败即中止） */
const exporting = ref(false);
async function handleExport() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    await exportCsvWithConfirm({
      total: total.value,
      targetType: 'users',
      filterSummary: exportFilterSummary(),
      columns: [
        { header: '登录名', value: (r: any) => r.username },
        { header: '显示昵称', value: (r: any) => r.nickname },
        { header: '学校', value: (r: any) => r.school },
        { header: '角色', value: (r: any) => dictLabel(ROLE, r.role) },
        { header: '状态', value: (r: any) => dictLabel(ACCOUNT_STATUS, r.status) },
        { header: '注册时间', value: (r: any) => formatTime(r.createTime) },
      ],
      fetchPage: (page) => {
        const params: any = { page, pageSize: 100 };
        if (filterRole.value !== '') params.role = filterRole.value;
        if (filterStatus.value !== '') params.status = filterStatus.value;
        if (keyword.value.trim()) params.keyword = keyword.value.trim();
        return getUserList(params);
      },
    });
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="用户管理"
    description="管理平台账号、角色与使用状态。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无用户"
    empty-hint="调整筛选条件试试；新注册的用户会出现在这里"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #filters>
      <el-select
        v-model="filterRole"
        placeholder="角色"
        clearable
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in roleOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-select
        v-model="filterStatus"
        placeholder="状态"
        clearable
        style="width: 120px"
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
        placeholder="搜索登录名或显示昵称"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </template>
    <template #actions>
      <el-button type="success" @click="openCreateDialog">新建用户</el-button>
      <el-button :loading="exporting" :disabled="!total" @click="handleExport">
        导出 CSV
      </el-button>
    </template>

    <el-table-column label="序号" width="80">
      <template #default="{ $index }">
        {{ (currentPage - 1) * pageSize + $index + 1 }}
      </template>
    </el-table-column>
    <el-table-column prop="username" label="登录名" width="120" />
    <el-table-column prop="nickname" label="显示昵称" width="120" />
    <el-table-column prop="school" label="学校" min-width="120" />
    <el-table-column label="角色" width="100">
      <template #default="{ row }">
        {{ dictLabel(ROLE, row.role) }}
      </template>
    </el-table-column>
    <el-table-column label="状态" width="80">
      <template #default="{ row }">
        <ZyStatusTag :dict="ACCOUNT_STATUS" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="注册时间" width="160">
      <template #default="{ row }">
        {{ formatTime(row.createTime) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="280" fixed="right">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click="handleEdit(row)">
          编辑
        </el-button>
        <el-button
          :type="row.status === 1 ? 'warning' : 'success'"
          link
          size="small"
          @click="handleToggleStatus(row)"
        >
          {{ row.status === 1 ? '禁用' : '启用' }}
        </el-button>
        <el-button type="primary" link size="small" @click="handleChangeRole(row)">
          变更角色
        </el-button>
        <el-button type="danger" link size="small" @click="handleDelete(row)">
          删除
        </el-button>
      </template>
    </el-table-column>

    <template #append>
      <!-- 编辑弹窗 -->
      <el-dialog v-model="editDialogVisible" title="编辑用户" width="440px">
        <el-form label-width="80px">
          <el-form-item label="显示昵称">
            <el-input v-model="editForm.nickname" />
          </el-form-item>
          <el-form-item label="学校">
            <el-input v-model="editForm.school" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitEdit">保存</el-button>
        </template>
      </el-dialog>

      <!-- 变更角色弹窗 -->
      <el-dialog v-model="roleDialogVisible" title="变更角色" width="400px">
        <el-form label-width="80px">
          <el-form-item label="角色">
            <el-select v-model="roleForm.role" style="width: 100%">
              <el-option label="学生" :value="0" />
              <el-option label="传承人" :value="1" />
              <el-option label="咨询师" :value="2" />
              <el-option label="管理员" :value="3" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="roleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitRole">确认</el-button>
        </template>
      </el-dialog>

      <!-- 新建用户弹窗（独立组件） -->
      <CreateUserDialog ref="createDialogRef" @created="fetchList" />
    </template>
  </ZyListPage>
</template>
