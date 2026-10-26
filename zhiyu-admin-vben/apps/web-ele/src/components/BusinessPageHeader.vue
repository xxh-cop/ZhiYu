<script setup lang="ts">
defineProps<{ title: string; description: string; loading?: boolean; eyebrow?: string }>();
defineEmits<{ refresh: [] }>();
</script>
<template>
  <header class="zy-business-heading">
    <div>
      <p class="zy-business-eyebrow">{{ eyebrow || '织愈 · 业务管理' }}</p>
      <h1>{{ title }}</h1>
      <p class="zy-business-description">{{ description }}</p>
    </div>
    <slot><el-button :loading="loading" @click="$emit('refresh')">刷新列表</el-button></slot>
  </header>
</template>
<style scoped>
.zy-business-heading{display:flex;align-items:center;justify-content:space-between;gap:24px;margin-bottom:24px}.zy-business-eyebrow{font-size:11px;letter-spacing:2px;color:var(--el-color-primary);margin:0 0 8px}.zy-business-heading h1{font-size:26px;font-weight:650;line-height:1.4;margin:0 0 10px;color:var(--el-text-color-primary)}.zy-business-description{font-size:14px;line-height:1.8;color:var(--el-text-color-secondary);margin:0;max-width:760px}@media(max-width:640px){.zy-business-heading{align-items:flex-start;gap:16px}.zy-business-heading h1{font-size:22px}.zy-business-description{font-size:13px}}
</style>
