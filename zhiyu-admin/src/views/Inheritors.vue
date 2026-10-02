<script setup>
import { ref } from 'vue'

/** Mock：传承人列表（5 条） */
const tableData = ref([
  {
    id: 2,
    avatar: '',
    nickname: '赣绣王老师',
    skill: '赣绣',
    verified: 1,
    verifiedLabel: '已认证',
    projectCount: 8,
  },
  {
    id: 11,
    avatar: '',
    nickname: '景德镇陈师傅',
    skill: '陶艺',
    verified: 1,
    verifiedLabel: '已认证',
    projectCount: 5,
  },
  {
    id: 12,
    avatar: '',
    nickname: '大理扎染杨阿姨',
    skill: '扎染',
    verified: 1,
    verifiedLabel: '已认证',
    projectCount: 6,
  },
  {
    id: 13,
    avatar: '',
    nickname: '佛山剪纸刘师傅',
    skill: '剪纸',
    verified: 0,
    verifiedLabel: '待审核',
    projectCount: 0,
  },
  {
    id: 14,
    avatar: '',
    nickname: '青田篆刻周老师',
    skill: '篆刻',
    verified: 0,
    verifiedLabel: '待审核',
    projectCount: 2,
  },
])

function verifiedTagType(verified) {
  return verified === 1 ? 'success' : 'warning'
}

function handleViewDetail(row) {
  ElMessage.info(`「${row.nickname}」详情页开发中`)
}
</script>

<template>
  <div class="inheritors-page">
    <h2 class="page-title">传承人管理</h2>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" style="width: 100%">
        <el-table-column label="头像" width="70">
          <template #default="{ row }">
            <el-avatar :size="36" :src="row.avatar">
              {{ row.nickname.charAt(0) }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="160" />
        <el-table-column prop="skill" label="擅长技艺" width="120" />
        <el-table-column label="认证状态" width="100">
          <template #default="{ row }">
            <el-tag :type="verifiedTagType(row.verified)" size="small">
              {{ row.verifiedLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectCount" label="提交项目数" width="120" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewDetail(row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.inheritors-page {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-16);
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--zy-text);
}

.table-card {
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);
}

:deep(.el-table th.el-table__cell) {
  background-color: var(--zy-bg);
}
</style>
