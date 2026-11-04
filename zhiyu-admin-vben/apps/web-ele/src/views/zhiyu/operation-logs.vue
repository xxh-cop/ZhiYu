<script setup lang="ts">
import { requestClient } from '#/api/request';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';

/**
 * 列表状态机。
 * 后端 /admin/operation-logs 仅接受 page（固定每页条数），
 * 故 show-sizes 关闭、fetcher 只透传页码。
 */
const {
  tableData,
  loading,
  errorMsg,
  total,
  currentPage,
  pageSize,
  fetchList,
  handlePageChange,
} = useListPage({
  pageSize: 20,
  fetcher: ({ page }) =>
    requestClient.get('/admin/operation-logs', { params: { page } }),
});
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="操作记录"
    description="记录校园审核与关怀跟进的操作人、时间和对象，不记录敏感正文。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无操作记录"
    empty-hint="审核、上下架等管理操作产生后会在这里记录"
    :show-sizes="false"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <el-table-column prop="create_time" label="时间" min-width="180" />
    <el-table-column prop="actor" label="操作人编号" min-width="170" />
    <el-table-column prop="action" label="操作" min-width="180" />
    <el-table-column prop="target" label="对象编号" min-width="170" />

    <template #append>
      <p class="business-note">本系统不能替代专业心理咨询</p>
    </template>
  </ZyListPage>
</template>
