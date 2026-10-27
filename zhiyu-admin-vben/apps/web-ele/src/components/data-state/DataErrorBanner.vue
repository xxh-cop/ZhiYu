<script lang="ts" setup>
defineProps<{ error?: string; retrying?: boolean }>();

const emit = defineEmits<{ retry: [] }>();
</script>

<template>
  <div v-if="error" class="data-error-banner" role="alert">
    <span class="data-error-banner__text">{{ error }}</span>
    <el-button size="small" :loading="retrying" @click="emit('retry')">
      重试
    </el-button>
  </div>
</template>

<style scoped>
.data-error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: var(--el-border-radius-base);
}
.data-error-banner__text {
  overflow-wrap: anywhere;
}
</style>
