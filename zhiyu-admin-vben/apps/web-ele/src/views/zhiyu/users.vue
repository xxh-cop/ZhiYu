<script setup lang="ts">
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { onMounted, ref } from 'vue';

import { useAccessStore } from '@vben/stores';

import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';

import {
  createUser,
  deleteUser,
  getUserList,
  updateUser,
  updateUserRole,
  updateUserStatus,
} from '#/api/zhiyu/admin-user';
import type { UserCreateBody } from '#/api/zhiyu/admin-user';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

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
const filtersVisible = ref(true);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

/** 编辑弹窗 */
const editDialogVisible = ref(false);
const editForm = ref({ id: 0, nickname: '', school: '' });

/** 角色弹窗 */
const roleDialogVisible = ref(false);
const roleForm = ref({ id: 0, role: 0 });

/** 新建用户弹窗 */
const createDialogVisible = ref(false);
const createFormRef = ref<FormInstance>();
const creating = ref(false);
const avatarUploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);
const accessStore = useAccessStore();

const createForm = ref<UserCreateBody>({
  username: '',
  password: '',
  nickname: '',
  role: 0,
  school: '',
  intro: '',
  avatarUrl: '',
  skill: '',
  honor: '',
});

const createRules: FormRules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { min: 3, max: 50, message: '登录名长度 3-50', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: '请输入显示昵称', trigger: 'blur' },
    { max: 50, message: '显示昵称不超过 50 字', trigger: 'blur' },
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' },
  ],
};

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
    if (filterRole.value !== '') params.role = filterRole.value;
    if (filterStatus.value !== '') params.status = filterStatus.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const data = await getUserList(params);
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

// ── 新建用户 ──

function openCreateDialog() {
  createForm.value = {
    username: '',
    password: '',
    nickname: '',
    role: 0,
    school: '',
    intro: '',
    avatarUrl: '',
    skill: '',
    honor: '',
  };
  createDialogVisible.value = true;
}

function handleRoleChange() {
  // 切换角色时清空传承人专用字段
  if (createForm.value.role !== 1) {
    createForm.value.skill = '';
    createForm.value.honor = '';
  }
}

function triggerAvatarUpload() {
  avatarInput.value?.click();
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('仅支持 JPG / PNG / GIF / WebP 格式');
    input.value = '';
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB');
    input.value = '';
    return;
  }

  avatarUploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file);
    const token = accessStore.accessToken;
    const response = await fetch('/api/files/upload/image', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    const result = await response.json();
    if (result.code === 0 && result.data?.url) {
      createForm.value.avatarUrl = result.data.url;
      ElMessage.success('头像上传成功');
    } else {
      ElMessage.error(result.message || '头像上传失败');
    }
  } catch {
    ElMessage.error('头像上传失败');
  } finally {
    avatarUploading.value = false;
    input.value = '';
  }
}

function getAvatarUrl(url: string): string {
  if (!url) return '';
  if (url.startsWith('http')) return url;
  return `/api${url}`;
}

async function submitCreate() {
  if (!createFormRef.value) return;
  const valid = await createFormRef.value.validate().catch(() => false);
  if (!valid) return;

  creating.value = true;
  try {
    const body: UserCreateBody = {
      username: createForm.value.username,
      password: createForm.value.password,
      nickname: createForm.value.nickname,
      role: createForm.value.role,
    };
    if (createForm.value.school) body.school = createForm.value.school;
    if (createForm.value.intro) body.intro = createForm.value.intro;
    if (createForm.value.avatarUrl) body.avatarUrl = createForm.value.avatarUrl;
    if (createForm.value.role === 1) {
      if (createForm.value.skill) body.skill = createForm.value.skill;
      if (createForm.value.honor) body.honor = createForm.value.honor;
    }

    await createUser(body);
    ElMessage.success('用户创建成功');
    createDialogVisible.value = false;
    fetchList();
  } catch (err: any) {
    const code = err?.response?.data?.code;
    const msg = err?.response?.data?.message || '创建失败';
    if (code === 2002) {
      ElMessage.error('登录名已存在');
    } else {
      ElMessage.error(msg);
    }
  } finally {
    creating.value = false;
  }
}

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader title="用户管理" description="管理平台账号、角色与使用状态。" :loading="loading" @refresh="fetchList" />

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="zy-filter-heading"><span>筛选条件</span><el-button link type="primary" :aria-expanded="filtersVisible" @click="filtersVisible = !filtersVisible">{{ filtersVisible ? '收起筛选' : '展开筛选' }}</el-button></div>
      <div v-show="filtersVisible" class="zy-filter-content flex flex-wrap items-center gap-3">
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
        <el-button type="success" @click="openCreateDialog">新建用户</el-button>
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
            empty-text="暂无用户"
            @retry="fetchList"
          />
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

      <TablePagination
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :show-sizes="true"
        :total="total"
        @change="handlePageChange"
      />
    </el-card>

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

    <!-- 新建用户弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建用户" width="600px">
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="100px"
      >
        <!-- 头像上传 -->
        <el-form-item label="头像">
          <div class="flex items-center gap-3">
            <div class="create-avatar-wrapper">
              <img
                v-if="createForm.avatarUrl"
                :src="getAvatarUrl(createForm.avatarUrl)"
                alt="头像"
                class="create-avatar-img"
              />
              <div v-else class="create-avatar-placeholder">
                {{ createForm.nickname.charAt(0) || '?' }}
              </div>
            </div>
            <el-button :loading="avatarUploading" @click="triggerAvatarUpload">
              上传头像
            </el-button>
          </div>
          <input
            ref="avatarInput"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            style="display: none"
            @change="handleAvatarChange"
          />
        </el-form-item>

        <el-form-item label="登录名" prop="username">
          <el-input
            v-model="createForm.username"
            placeholder="3-50 位（用于登录）"
            style="max-width: 300px"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="createForm.password"
            type="password"
            placeholder="6-32 位"
            show-password
            style="max-width: 300px"
          />
        </el-form-item>

        <el-form-item label="显示昵称" prop="nickname">
          <el-input
            v-model="createForm.nickname"
            placeholder="不超过 50 字（用于展示）"
            style="max-width: 300px"
          />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select
            v-model="createForm.role"
            placeholder="请选择角色"
            style="width: 200px"
            @change="handleRoleChange"
          >
            <el-option label="学生" :value="0" />
            <el-option label="传承人" :value="1" />
            <el-option label="咨询师" :value="2" />
            <el-option label="管理员" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item label="学校">
          <el-input
            v-model="createForm.school"
            placeholder="可选"
            style="max-width: 300px"
          />
        </el-form-item>

        <el-form-item label="个人简介">
          <el-input
            v-model="createForm.intro"
            type="textarea"
            :rows="3"
            placeholder="可选"
          />
        </el-form-item>

        <!-- 传承人专用字段 -->
        <template v-if="createForm.role === 1">
          <el-divider content-position="left">传承人资料</el-divider>
          <el-form-item label="擅长技艺">
            <el-input
              v-model="createForm.skill"
              placeholder="如：赣绣、陶瓷等"
              style="max-width: 300px"
            />
          </el-form-item>
          <el-form-item label="荣誉">
            <el-input
              v-model="createForm.honor"
              placeholder="如：非遗传承人等"
              style="max-width: 300px"
            />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.create-avatar-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  border: 2px solid #e4e7ed;
}

.create-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.create-avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
