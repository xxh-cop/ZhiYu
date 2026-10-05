<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteUser,
  getUserList,
  updateUser,
  updateUserRole,
  updateUserStatus,
} from '#/api/zhiyu/admin-user';

/** 角色中文映射 */
const ROLE_LABELS: Record<number, string> = {
  0: '学生',
  1: '传承人',
  2: '咨询师',
  3: '管理员',
};

/** 筛选 */
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
const keyword = ref('');

/** 表格 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 编辑弹窗 */
const editDialogVisible = ref(false);
const editForm = ref({ id: 0, nickname: '', school: '' });

/** 角色弹窗 */
const roleDialogVisible = ref(false);
const roleForm = ref({ id: 0, role: 0 });

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
    if (filterRole.value !== '') params.role = filterRole.value;
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const data = await getUserList(params);
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

/** 编辑 */
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

/** 变更角色 */
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

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="flex flex-col gap-4 p-4">
    <h2 class="m-0 text-xl font-semibold">用户管理</h2>

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="flex flex-wrap items-center gap-3">
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
          placeholder="搜索用户名或昵称"
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
        :empty-text="errorMsg || '暂无用户'"
      >
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="school" label="学校" min-width="120" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            {{ ROLE_LABELS[row.role] || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
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

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="440px">
      <el-form label-width="80px">
        <el-form-item label="昵称">
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
  </div>
</template>
