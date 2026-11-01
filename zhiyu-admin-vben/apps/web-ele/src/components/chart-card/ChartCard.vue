<script lang="ts" setup>
import { computed } from 'vue';

import DataState from '#/components/data-state/DataState.vue';
import { resolveDataState } from '#/components/data-state/resolve';

const props = withDefaults(
  defineProps<{
    description?: string;
    empty?: boolean;
    emptyHint?: string;
    emptyText?: string;
    error?: string;
    loading?: boolean;
    minHeight?: string;
    title: string;
  }>(),
  {
    description: '',
    empty: false,
    emptyHint: '数据将在产生后呈现',
    emptyText: '暂无数据',
    error: '',
    loading: false,
    minHeight: '268px',
  },
);

const emit = defineEmits<{ retry: [] }>();

const kind = computed(() =>
  resolveDataState({
    error: props.error,
    isEmpty: props.empty,
    loading: props.loading,
  }),
);
</script>

<template>
  <section class="chart-card zy-glass zy-slide-up">
    <header class="chart-card__header">
      <div>
        <h3 class="chart-card__title">{{ title }}</h3>
        <p v-if="description" class="chart-card__description">
          {{ description }}
        </p>
      </div>
      <div v-if="$slots.actions" class="chart-card__actions">
        <slot name="actions"></slot>
      </div>
    </header>
    <div
      class="chart-card__body"
      :aria-busy="kind === 'loading'"
      :aria-label="kind === 'ready' ? `${title}。${description}` : undefined"
      :role="kind === 'ready' ? 'img' : undefined"
      :style="{ minHeight }"
    >
      <DataState
        v-if="kind !== 'ready'"
        :empty-hint="emptyHint"
        :empty-text="emptyText"
        :error="error"
        :loading="loading"
        @retry="emit('retry')"
      />
      <slot v-else></slot>
    </div>
    <footer v-if="$slots.footer" class="chart-card__footer">
      <slot name="footer"></slot>
    </footer>
  </section>
</template>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px;
  /* 表面由 .zy-glass 提供；scoped 属性选择器优先级更高，这里不能再铺底色 */
}

.chart-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.chart-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chart-card__description {
  margin: 7px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.chart-card__body {
  margin-top: 16px;
}

.chart-card__body :deep(.data-state) {
  display: flex;
  min-height: inherit;
  flex-direction: column;
  justify-content: center;
}

.chart-card__body :deep(.el-empty__description p) {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.chart-card__footer {
  margin-top: 12px;
}

@media (max-width: 640px) {
  .chart-card {
    padding: 18px;
  }
}
</style>
