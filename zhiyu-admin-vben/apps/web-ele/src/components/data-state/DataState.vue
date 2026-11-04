<script lang="ts" setup>
import { computed } from 'vue';

import { resolveDataState } from './resolve';

const props = withDefaults(
  defineProps<{
    emptyHint?: string;
    emptyText?: string;
    error?: string;
    loading?: boolean;
  }>(),
  {
    emptyHint: '',
    emptyText: '暂无数据',
    error: '',
    loading: false,
  },
);

const emit = defineEmits<{ retry: [] }>();

const kind = computed(() =>
  resolveDataState({
    error: props.error,
    isEmpty: true,
    loading: props.loading,
  }),
);
</script>

<template>
  <div class="data-state" aria-live="polite">
    <el-result
      v-if="kind === 'error'"
      icon="error"
      role="alert"
      title="加载失败"
      :sub-title="error"
    >
      <template #extra>
        <el-button type="primary" @click="emit('retry')">重试</el-button>
      </template>
    </el-result>
    <!-- 加载统一用骨架，不用裸 spinner / 纯文本 -->
    <el-skeleton v-else-if="kind === 'loading'" :rows="3" animated />
    <el-empty v-else :description="emptyText">
      <p v-if="emptyHint" class="data-state__hint">{{ emptyHint }}</p>
    </el-empty>
  </div>
</template>

<style scoped>
.data-state {
  padding: 24px 0;
  text-align: center;
}
.data-state :deep(.el-skeleton) {
  text-align: left;
}
.data-state__hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.data-state :deep(.el-empty__description p) {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
