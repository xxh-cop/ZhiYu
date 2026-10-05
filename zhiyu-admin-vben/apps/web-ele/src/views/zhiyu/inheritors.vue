<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@vben/stores';

import {
  getInheritorList,
  updateInheritor,
  verifyInheritor,
} from '#/api/zhiyu/admin-inheritor';

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
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 编辑弹窗 */
const editDialogVisible = ref(false);
const editForm = ref({ id: 0, skill: '', intro: '', honor: '' });

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
    if (filterVerified.value !== '') params.verified = filterVerified.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const data = await getInheritorList(params);
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

onMounted(() => {
  fetchList();
});
</script>

<template>
  <div class="flex flex-col gap-4 p-4">
    <h2 class="m-0 text-xl font-semibold">传承人管理</h2>

    <!-- 筛选栏 -->
    <el-card shadow="never">
      <div class="flex flex-wrap items-center gap-3">
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
          placeholder="搜索技艺或昵称"
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
        :empty-text="errorMsg || '暂无传承人'"
      >
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="nickname" label="昵称" width="120" />
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
  </div>
</template>
