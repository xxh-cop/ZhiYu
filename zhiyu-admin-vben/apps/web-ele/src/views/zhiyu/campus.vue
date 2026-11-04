<script setup lang="ts">
import { ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { requestClient } from '#/api/request';
import ZyListPage from '#/components/zy-list-page/ZyListPage.vue';
import { useListPage } from '#/components/zy-list-page/useListPage';
import ZyStatusTag from '#/components/zy-status-tag/ZyStatusTag.vue';
import { CAMPUS_STATUS } from '#/constants/status-dict';

const savingId = ref('');

/**
 * 列表状态机。
 * 后端 /admin/campus-verifications 仅接受 page（固定每页条数），
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
    requestClient.get('/admin/campus-verifications', { params: { page } }),
});

async function review(row: any, decision: string) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请确认已通过学校认可的方式核实身份，再${decision === 'APPROVED' ? '通过' : '驳回'}申请。不要填写证件号等敏感内容。`,
      '校园身份审核',
      {
        inputType: 'textarea',
        inputValidator: (v) =>
          (!!v?.trim() && v.length <= 300) || '请填写 1–300 字审核说明',
        confirmButtonText: '确认审核',
      },
    );
    savingId.value = row.id;
    await requestClient.post(`/admin/campus-verifications/${row.id}/review`, {
      decision,
      reason: value,
    });
    ElMessage.success('审核已保存');
    await fetchList();
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '审核失败');
  } finally {
    savingId.value = '';
  }
}
</script>

<template>
  <ZyListPage
    v-model:page="currentPage"
    v-model:page-size="pageSize"
    title="校园身份审核"
    description="核实校园身份，保留处理说明与操作记录。审核不改变账号权限。"
    :data="tableData"
    :loading="loading"
    :error="errorMsg"
    :total="total"
    empty-text="暂无身份申请"
    empty-hint="学生提交校园身份核验申请后会出现在这里"
    :show-sizes="false"
    :pagination-disabled="!!savingId"
    @refresh="fetchList"
    @page-change="handlePageChange"
  >
    <template #banner>
      <el-alert
        title="身份资料仅供审核使用。请通过学校认可的渠道核实，不要下载或另行传播。"
        type="info"
        :closable="false"
      />
    </template>

    <el-table-column label="申请人" min-width="100">
      <template #default="{ row }">{{ row.identity?.realName }}</template>
    </el-table-column>
    <el-table-column label="学号" min-width="150">
      <template #default="{ row }">{{ row.identity?.studentNumber }}</template>
    </el-table-column>
    <el-table-column prop="school" label="学校" min-width="170" />
    <el-table-column prop="college" label="学院" min-width="170" />
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <ZyStatusTag :dict="CAMPUS_STATUS" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column prop="review_reason" label="审核说明" min-width="180" />
    <el-table-column label="操作" width="150" fixed="right">
      <template #default="{ row }">
        <template v-if="row.status === 'PENDING'">
          <el-button
            link
            type="primary"
            :loading="savingId === row.id"
            :disabled="!!savingId"
            @click="review(row, 'APPROVED')"
          >
            通过
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="!!savingId"
            @click="review(row, 'REJECTED')"
          >
            驳回
          </el-button>
        </template>
        <span v-else>已处理</span>
      </template>
    </el-table-column>

    <template #append>
      <p class="business-note">本系统不能替代专业心理咨询</p>
    </template>
  </ZyListPage>
</template>
