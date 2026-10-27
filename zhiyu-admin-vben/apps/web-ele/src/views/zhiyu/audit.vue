<script setup lang="ts">
import VideoPreview from "./components/VideoPreview.vue";
const videoToReview=ref('');
import BusinessPageHeader from '#/components/BusinessPageHeader.vue';
import {useRoute} from 'vue-router';
import {requestClient} from '#/api/request';
const route=useRoute();
import { onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { auditProject } from '#/api/zhiyu/admin';
import DataState from '#/components/data-state/DataState.vue';
import TablePagination from '#/components/table-pagination/TablePagination.vue';

/** 类型筛选选项 */
const typeOptions = [{ value: 'project', label: '项目' }];
const filterType = ref('project');
const catalogStatus=ref(['0','1','2'].includes(String(route.query.status))?Number(route.query.status):0),catalogCategory=ref(String(route.query.category||''));
const searchText = ref('');

/** 表格数据 */
const tableData = ref<any[]>([]);
const loading = ref(false);
const filtersVisible = ref(true);
const errorMsg = ref('');

/** 分页 */
const total = ref(0);
const currentPage = ref(1);
const pageSize = 10;

/** 驳回弹窗 */
const rejectDialogVisible = ref(false);
const rejectRow = ref<any>(null);
const rejectReason = ref('');
const rejectLoading = ref(false);

/** 格式化时间 */
function formatTime(dt?: string) {
  if (!dt) return '-';
  return dt.replace('T', ' ').substring(0, 16);
}

/** 拉取列表 */
let listRequest = 0;
async function fetchList() {
  const requestId = ++listRequest;
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await requestClient.get<any>('/admin/project-catalog',{params:{page:currentPage.value,status:catalogStatus.value,category:catalogCategory.value,keyword:searchText.value}});
    if (requestId !== listRequest) return;
    tableData.value = data.records || [];
    applyFilter();
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

/** 前端搜索（在已加载的数据中过滤标题） */
const filteredData = ref<any[]>([]);
function applyFilter() {
  if (!searchText.value) {
    filteredData.value = tableData.value;
    return;
  }
  const kw = searchText.value.toLowerCase();
  filteredData.value = tableData.value.filter((row) =>
    (row.title || '').toLowerCase().includes(kw),
  );
}

/** 监听 tableData 变化自动过滤 */
function syncFilter() {
  applyFilter();
}

/** 通过 */
async function handleApprove(row: any) {
  try {
    await auditProject(row.id, { action: 'approve' });
    ElMessage.success(`「${row.title}」已通过`);
    await fetchList();
    syncFilter();
  } catch {
    // 拦截器已弹错误提示
  }
}

/** 打开驳回弹窗 */
function handleReject(row: any) {
  rejectRow.value = row;
  rejectReason.value = '';
  rejectDialogVisible.value = true;
}

/** 确认驳回 */
async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由');
    return;
  }
  rejectLoading.value = true;
  try {
    await auditProject(rejectRow.value.id, {
      action: 'reject',
      reason: rejectReason.value.trim(),
    });
    ElMessage.success(`「${rejectRow.value.title}」已驳回`);
    rejectDialogVisible.value = false;
    await fetchList();
    syncFilter();
  } catch {
    // 拦截器已弹错误提示
  } finally {
    rejectLoading.value = false;
  }
}

/** 翻页 */
function handlePageChange(page: number) {
  currentPage.value = page;
  fetchList();
}

/** 类型变化 */
function handleTypeChange() {
  currentPage.value = 1;
  fetchList();
}

onMounted(() => {
  fetchList().then(syncFilter);
});
</script>

<template>
  <div class="zy-business-page flex flex-col gap-4">
    <BusinessPageHeader title="内容审核" description="按分类和上架状态查看项目，处理待审核内容。" :loading="loading" @refresh="fetchList" />

    <el-card shadow="never">
      <div class="zy-filter-heading"><span>筛选条件</span><el-button link type="primary" :aria-expanded="filtersVisible" @click="filtersVisible = !filtersVisible">{{ filtersVisible ? '收起筛选' : '展开筛选' }}</el-button></div>
      <div v-show="filtersVisible" class="zy-filter-content flex flex-wrap items-center gap-3">
        <el-select v-model="catalogStatus" style="width:140px" aria-label="项目状态" @change="handleTypeChange"><el-option :value="0" label="待审核"/><el-option :value="1" label="已上架"/><el-option :value="2" label="已下架"/></el-select><el-input v-model="catalogCategory" placeholder="分类（可选）" clearable style="width:160px" @change="handleTypeChange"/><el-select
          v-model="filterType"
          placeholder="类型"
          style="width: 140px"
          @change="handleTypeChange"
        >
          <el-option
            v-for="opt in typeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="searchText"
          placeholder="搜索标题"
          clearable
          style="width: 260px"
          @change="handleTypeChange"
          @clear="handleTypeChange"
        />
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="filteredData"
        style="width: 100%"
      >
        <template #empty>
          <DataState
            :loading="loading"
            :error="errorMsg"
            empty-text="没有匹配的项目"
            @retry="fetchList"
          />
        </template>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column label="教学视频" width="110"><template #default="{row}"><el-button v-if="row.videoUrl" link type="primary" @click="videoToReview=row.videoUrl">预览视频</el-button><span v-else>无视频</span></template></el-table-column><el-table-column prop="category" label="品类" width="100" />
        <el-table-column label="难度" width="80">
          <template #default="{ row }">
            {{ row.level === 1 ? '入门' : row.level === 2 ? '进阶' : row.level === 3 ? '高阶' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="estMinutes" label="时长(分)" width="100" />
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" v-if="catalogStatus===0" @click="handleApprove(row)">
              通过
            </el-button>
            <el-button type="danger" size="small" v-if="catalogStatus===0" @click="handleReject(row)">
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <TablePagination
        v-model:page="currentPage"
        :page-size="pageSize"
        :total="total"
        @change="handlePageChange"
      />
    </el-card>

    <!-- 驳回弹窗 -->
    <el-dialog
      v-model="rejectDialogVisible"
      title="驳回项目"
      width="440px"
    >
      <p class="mb-3 text-sm text-gray-500">请填写驳回理由（将通知传承人）</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="请输入驳回理由"
        maxlength="200"
        show-word-limit
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectLoading" @click="confirmReject">
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
<el-dialog :model-value="!!videoToReview" title="教学视频审核" width="min(720px, 94vw)" @close="videoToReview=''"> <VideoPreview v-if="videoToReview" :src="videoToReview"/><p>确认教学内容、画面和音轨授权后，再通过项目审核。</p></el-dialog></template>
