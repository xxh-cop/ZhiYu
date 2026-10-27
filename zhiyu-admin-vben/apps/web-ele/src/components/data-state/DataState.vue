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
    <p v-else-if="kind === 'loading'" class="data-state__loading">正在加载…</p>
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
.data-state__loading {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.data-state__hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
