<script setup>
import { ref } from 'vue'

/** 类型筛选选项 */
const typeOptions = [
  { value: '', label: '全部' },
  { value: 'project', label: '项目' },
  { value: 'work', label: '作品' },
  { value: 'comment', label: '评论' },
]
const filterType = ref('')
const searchText = ref('')

/** Mock：待审核内容队列（5 条） */
const tableData = ref([
  {
    id: 101,
    type: 'project',
    typeLabel: '项目',
    title: '赣绣基础：平针绣荷花书签',
    submitter: '赣绣王老师',
    submitTime: '2026-09-28 14:32',
  },
  {
    id: 102,
    type: 'work',
    typeLabel: '作品',
    title: '第一次扎染，染出了晚霞的颜色',
    submitter: '匿名同学',
    submitTime: '2026-09-28 16:05',
  },
  {
    id: 103,
    type: 'comment',
    typeLabel: '评论',
    title: '你的编织好整齐，看了一定会很安心',
    submitter: '小织',
    submitTime: '2026-09-29 09:18',
  },
  {
    id: 104,
    type: 'project',
    typeLabel: '项目',
    title: '陶艺入门：手捏杯基础造型',
    submitter: '景德镇陈师傅',
    submitTime: '2026-09-29 11:47',
  },
  {
    id: 105,
    type: 'work',
    typeLabel: '作品',
    title: '篆刻了我的名字，虽然歪歪扭扭但很珍惜',
    submitter: '匿名同学',
    submitTime: '2026-09-30 20:13',
  },
])

/** 前端筛选 */
const filteredData = ref(tableData.value)

function handleFilter() {
  filteredData.value = tableData.value.filter((row) => {
    const matchType = !filterType.value || row.type === filterType.value
    const matchSearch =
      !searchText.value ||
      row.title.includes(searchText.value) ||
      row.submitter.includes(searchText.value)
    return matchType && matchSearch
  })
}

function handleApprove(row) {
  ElMessage.info(`「${row.title}」审核功能开发中`)
}

function handleReject(row) {
  ElMessage.info(`「${row.title}」驳回功能开发中`)
}
</script>

<template>
  <div class="audit-page">
    <h2 class="page-title">内容审核</h2>

    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-select
          v-model="filterType"
          placeholder="类型"
          clearable
          style="width: 140px"
          @change="handleFilter"
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
          placeholder="搜索标题或提交人"
          clearable
          style="width: 260px"
          @input="handleFilter"
          @clear="handleFilter"
        />
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="filteredData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="typeLabel" label="类型" width="80" />
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column prop="submitter" label="提交人" width="140" />
        <el-table-column prop="submitTime" label="提交时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleApprove(row)">
              通过
            </el-button>
            <el-button type="danger" size="small" @click="handleReject(row)">
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.audit-page {
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

/* 表头背景色 */
:deep(.el-table th.el-table__cell) {
  background-color: var(--zy-bg);
}
</style>
