<script setup lang="ts">
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import { computed, onMounted, ref } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';

import {
  getInheritorList,
  updateInheritor,
  verifyInheritor,
} from '#/api/zhiyu/admin-inheritor';
import { createUser } from '#/api/zhiyu/admin-user';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

/** 当前用户角色码 */
const userStore = useUserStore();
const accessStore = useAccessStore();
const currentRole = computed(() => {
  const roles = userStore.userInfo?.roles ?? [];
  if (roles.includes('ROLE_3')) return 3;
  if (roles.includes('ROLE_2')) return 2;
  if (roles.includes('ROLE_1')) return 1;
  return 0;
});
const isAdmin = computed(() => currentRole.value === 3);

/** 筛选 */
const verifiedOptions = [
  { value: '', label: '全部认证状态' },
  { value: 0, label: '未认证' },
  { value: 1, label: '已认证' },
];
const filterVerified = ref<number | string>('');
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
const editForm = ref({ id: 0, skill: '', intro: '', honor: '' });

/** 新建传承人弹窗 */
const createDialogVisible = ref(false);
const createFormRef = ref<FormInstance>();
const creating = ref(false);
const avatarUploading = ref(false);
const avatarInput = ref<HTMLInputElement | null>(null);

const createForm = ref({
  username: '',
  password: '',
  nickname: '',
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
  // skill 改为可选，不设 required 规则
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
    if (filterVerified.value !== '') params.verified = filterVerified.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const data = await getInheritorList(params);
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

/** 认证 / 取消认证 */
async function handleVerify(row: any, verified: number) {
  const label = verified === 1 ? '认证通过' : '取消认证';
  try {
    await ElMessageBox.confirm(
      `确认${label}传承人「${row.nickname}」？`,
      '提示',
      { type: 'warning' },
    );
    await verifyInheritor(row.id, verified);
    ElMessage.success(`${label}成功`);
    fetchList();
  } catch {
    // 用户取消或请求失败
  }
}

/** 编辑 */
function handleEdit(row: any) {
  editForm.value = {
    id: row.id,
    skill: row.skill || '',
    intro: '',
    honor: row.honor || '',
  };
  editDialogVisible.value = true;
}

async function submitEdit() {
  try {
    await updateInheritor(editForm.value.id, {
      skill: editForm.value.skill,
      intro: editForm.value.intro,
      honor: editForm.value.honor,
    });
    ElMessage.success('编辑成功');
    editDialogVisible.value = false;
    fetchList();
  } catch {
    // 拦截器已提示
  }
}

// ── 新建传承人 ──

function openCreateDialog() {
  createForm.value = {
    username: '',
    password: '',
    nickname: '',
    school: '',
    intro: '',
    avatarUrl: '',
    skill: '',
    honor: '',
  };
  createDialogVisible.value = true;
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
    const body: any = {
      username: createForm.value.username,
      password: createForm.value.password,
      nickname: createForm.value.nickname,
      role: 1, // 固定传承人
    };
    if (createForm.value.skill) body.skill = createForm.value.skill;
    if (createForm.value.school) body.school = createForm.value.school;
    if (createForm.value.intro) body.intro = createForm.value.intro;
    if (createForm.value.avatarUrl) body.avatarUrl = createForm.value.avatarUrl;
    if (createForm.value.honor) body.honor = createForm.value.honor;

    await createUser(body);
    ElMessage.success('传承人创建成功');
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
    <BusinessPageHeader title="传承人管理" description="维护传承人档案，连接非遗技艺与学生。" :loading="loading" @refresh="fetchList" />

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="zy-filter-heading"><span>筛选条件</span><el-button link type="primary" :aria-expanded="filtersVisible" @click="filtersVisible = !filtersVisible">{{ filtersVisible ? '收起筛选' : '展开筛选' }}</el-button></div>
      <div v-show="filtersVisible" class="zy-filter-content flex flex-wrap items-center gap-3">
        <el-select
          v-model="filterVerified"
          placeholder="认证状态"
          clearable
          style="width: 160px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="opt in verifiedOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索技艺或显示昵称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button v-if="isAdmin" type="success" @click="openCreateDialog">
          新建传承人
        </el-button>
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
            empty-text="暂无传承人"
            @retry="fetchList"
          />
        </template>
        <el-table-column label="序号" width="80">
          <template #default="{ $index }">
            {{ (currentPage - 1) * pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="显示昵称" width="120" />
        <el-table-column prop="skill" label="擅长技艺" width="120" />
        <el-table-column prop="honor" label="荣誉" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.honor || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="认证状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.verified === 1 ? 'success' : 'info'" size="small">
              {{ row.verified === 1 ? '已认证' : '未认证' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="isAdmin && row.verified === 0"
              type="success"
              link
              size="small"
              @click="handleVerify(row, 1)"
            >
              通过认证
            </el-button>
            <el-button
              v-if="isAdmin && row.verified === 1"
              type="warning"
              link
              size="small"
              @click="handleVerify(row, 0)"
            >
              取消认证
            </el-button>
            <el-button
              v-if="isAdmin"
              type="primary"
              link
              size="small"
              @click="handleEdit(row)"
            >
              编辑
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
    <el-dialog v-model="editDialogVisible" title="编辑传承人资料" width="480px">
      <el-form label-width="80px">
        <el-form-item label="擅长技艺">
          <el-input v-model="editForm.skill" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="editForm.intro"
            type="textarea"
            :rows="3"
          />
        </el-form-item>
        <el-form-item label="荣誉">
          <el-input v-model="editForm.honor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建传承人弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建传承人" width="600px">
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

        <el-form-item label="学校">
          <el-input
            v-model="createForm.school"
            placeholder="可选"
            style="max-width: 300px"
          />
        </el-form-item>

        <el-form-item label="擅长技艺">
          <el-input
            v-model="createForm.skill"
            placeholder="如：赣绣、陶瓷等（可选）"
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

        <el-form-item label="荣誉">
          <el-input
            v-model="createForm.honor"
            placeholder="如：非遗传承人等"
            style="max-width: 300px"
          />
        </el-form-item>
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
