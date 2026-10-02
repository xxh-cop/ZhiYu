<script setup>
import { ref } from 'vue'

/** 角色筛选 */
const roleOptions = [
  { value: -1, label: '全部角色' },
  { value: 0, label: '学生' },
  { value: 1, label: '传承人' },
  { value: 2, label: '咨询师' },
  { value: 3, label: '管理员' },
]
const filterRole = ref(-1)
const searchText = ref('')

/** 角色码 → tag type / label */
const ROLE_TAGS = {
  0: { type: 'info', label: '学生' },
  1: { type: '', label: '传承人' },
  2: { type: 'warning', label: '咨询师' },
  3: { type: 'danger', label: '管理员' },
}

/** Mock：用户列表（8 条） */
const tableData = ref([
  { id: 1, username: 'admin', nickname: '平台管理员', role: 3, school: '—', status: 1, statusLabel: '正常', regTime: '2026-09-01 08:00' },
  { id: 2, username: 'inheritor01', nickname: '赣绣王老师', role: 1, school: '江西师范大学', status: 1, statusLabel: '正常', regTime: '2026-09-03 10:15' },
  { id: 3, username: 'counselor01', nickname: '心理中心李老师', role: 2, school: '江西师范大学', status: 1, statusLabel: '正常', regTime: '2026-09-03 14:22' },
  { id: 4, username: 'student01', nickname: '小织', role: 0, school: '南昌大学', status: 1, statusLabel: '正常', regTime: '2026-09-05 09:30' },
  { id: 5, username: 'student02', nickname: '小雨', role: 0, school: '南昌大学', status: 1, statusLabel: '正常', regTime: '2026-09-06 11:45' },
  { id: 6, username: 'student03', nickname: '阿然', role: 0, school: '江西财经大学', status: 1, statusLabel: '正常', regTime: '2026-09-08 16:20' },
  { id: 7, username: 'student04', nickname: '小禾', role: 0, school: '江西师范大学', status: 0, statusLabel: '禁用', regTime: '2026-09-10 13:10' },
  { id: 8, username: 'student05', nickname: '林溪', role: 0, school: '华东交通大学', status: 1, statusLabel: '正常', regTime: '2026-09-12 19:55' },
])

const filteredData = ref(tableData.value)

function handleFilter() {
  filteredData.value = tableData.value.filter((row) => {
    const matchRole = filterRole.value === -1 || row.role === filterRole.value
    const matchSearch =
      !searchText.value ||
      row.username.includes(searchText.value) ||
      row.nickname.includes(searchText.value) ||
      (row.school && row.school.includes(searchText.value))
    return matchRole && matchSearch
  })
}

function statusTagType(status) {
  return status === 1 ? 'success' : 'danger'
}

function handleView(row) {
  ElMessage.info(`用户「${row.nickname}」详情功能开发中`)
}
</script>

<template>
  <div class="users-page">
    <h2 class="page-title">用户管理</h2>

    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-select
          v-model="filterRole"
          placeholder="角色"
          style="width: 140px"
          @change="handleFilter"
        >
          <el-option
            v-for="opt in roleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="searchText"
          placeholder="搜索用户名、昵称或学校"
          clearable
          style="width: 280px"
          @input="handleFilter"
          @clear="handleFilter"
        />
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="filteredData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="ROLE_TAGS[row.role].type" size="small">
              {{ ROLE_TAGS[row.role].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="school" label="学校" width="160" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="regTime" label="注册时间" width="160" />
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
.users-page {
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

:deep(.el-table th.el-table__cell) {
  background-color: var(--zy-bg);
}
</style>
