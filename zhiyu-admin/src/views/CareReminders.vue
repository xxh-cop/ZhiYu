<script setup>
import { ref } from 'vue'

/** 状态筛选 */
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待处理' },
  { value: 1, label: '已跟进' },
]
const riskOptions = [
  { value: '', label: '全部等级' },
  { value: 1, label: '一般' },
  { value: 2, label: '高危' },
]
const filterStatus = ref('')
const filterRisk = ref('')

/** Mock：脱敏关怀提醒（5 条） */
const tableData = ref([
  {
    id: 201,
    time: '2026-09-28 22:15',
    userLabel: '同学-A3F2',
    riskLevel: 2,
    riskLabel: '高危',
    summary: '对话中出现持续低落表述，提及"撑不下去了"，已触发危机干预流程并展示热线。',
    status: 0,
    statusLabel: '待处理',
  },
  {
    id: 202,
    time: '2026-09-28 15:43',
    userLabel: '同学-B7K9',
    riskLevel: 1,
    riskLabel: '一般',
    summary: '连续三日便签情绪偏低，推荐手作后未进入创作，建议关注。',
    status: 0,
    statusLabel: '待处理',
  },
  {
    id: 203,
    time: '2026-09-27 19:22',
    userLabel: '同学-C1D5',
    riskLevel: 1,
    riskLabel: '一般',
    summary: '考试周压力较大，对话中多次提及焦虑，已完成一次情绪倾听。',
    status: 1,
    statusLabel: '已跟进',
  },
  {
    id: 204,
    time: '2026-09-26 11:08',
    userLabel: '同学-D8E3',
    riskLevel: 2,
    riskLabel: '高危',
    summary: '便签中出现自伤倾向表述，系统已展示心理援助热线，建议人工跟进。',
    status: 1,
    statusLabel: '已跟进',
  },
  {
    id: 205,
    time: '2026-09-25 20:37',
    userLabel: '同学-E2F7',
    riskLevel: 1,
    riskLabel: '一般',
    summary: '人际冲突导致情绪波动，推荐扎染后进入创作，完成后情绪改善。',
    status: 1,
    statusLabel: '已跟进',
  },
])

const filteredData = ref(tableData.value)

function handleFilter() {
  filteredData.value = tableData.value.filter((row) => {
    const matchStatus =
      filterStatus.value === '' || row.status === filterStatus.value
    const matchRisk =
      filterRisk.value === '' || row.riskLevel === filterRisk.value
    return matchStatus && matchRisk
  })
}

function tagType(riskLevel) {
  return riskLevel === 2 ? 'danger' : 'info'
}

function statusTagType(status) {
  return status === 0 ? 'warning' : 'success'
}

function handleView(row) {
  ElMessage.info(`关怀提醒 #${row.id} 详情功能开发中`)
}
</script>

<template>
  <div class="care-page">
    <h2 class="page-title">关怀提醒</h2>
    <p class="page-desc">
      以下为系统自动生成的脱敏汇总，供授权角色查看。原始对话内容已加密存储，不在此展示。
    </p>

    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-select
          v-model="filterStatus"
          placeholder="状态"
          clearable
          style="width: 140px"
          @change="handleFilter"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select
          v-model="filterRisk"
          placeholder="风险等级"
          clearable
          style="width: 140px"
          @change="handleFilter"
        >
          <el-option
            v-for="opt in riskOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="filteredData" style="width: 100%">
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="userLabel" label="脱敏用户标识" width="140" />
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.riskLevel)" size="small">
              {{ row.riskLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="脱敏摘要" min-width="300" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.care-page {
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

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #8a8378;
  line-height: 1.6;
}

.filter-card,
.table-card {
  border: 1px solid var(--zy-border);
  border-radius: var(--zy-radius-card);
  box-shadow: var(--zy-shadow-card);
}

.filter-bar {
  display: flex;
  gap: var(--zy-space-12);
  align-items: center;
}

:deep(.el-table th.el-table__cell) {
  background-color: var(--zy-bg);
}
</style>
